package india.projectmesh.app.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import androidx.core.content.ContextCompat
import india.projectmesh.app.ble.MeshEventSink
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import uniffi.mesh_core.FfiException
import uniffi.mesh_core.FfiMeshTransport

private const val TAG = "MeshWifiDirect"

/**
 * Real `android.net.wifi.p2p.*` implementation of [FfiMeshTransport], `docs/TRANSPORT.md` §4 --
 * the higher-throughput sibling to `ble/BleTransportDriver.kt`, for payloads too large for
 * comfortable BLE transfer (map packs, images, voice notes per the doc). Same driver contract as
 * the BLE transport (`start`/`stop`/`send`, [MeshEventSink] for the reverse direction), a
 * genuinely different link underneath: Wi-Fi Direct Service Discovery (DNS-SD) for peer
 * rendezvous instead of GATT advertise/scan, then a plain TCP socket per connected peer once a
 * P2P group forms, instead of GATT characteristics.
 *
 * **Why DNS-SD instead of `discoverPeers()`:** plain peer discovery finds *every* nearby Wi-Fi
 * Direct device (any app, even none) and would either connect blindly or need an out-of-band
 * filter; DNS-SD lets this driver advertise a TXT record carrying the mesh's `serviceId` and only
 * initiate a connection once a peer's matching record is actually seen -- the Wi-Fi Direct
 * equivalent of BLE's service-UUID scan filter.
 *
 * **Connection-race tie-break**, mirroring `ble/BlePeerRegistry.kt`'s `sessionIdIsLower`: both
 * sides discover each other's matching service simultaneously and could each call `connect()`.
 * Resolved by comparing `WifiP2pDevice.deviceAddress` (available here, unlike BLE's fake
 * `BluetoothAdapter.getAddress()`) -- the lexicographically lower address initiates, the other
 * waits to be connected to. See [maybeInitiateConnection].
 *
 * **Peer identity is IP-based, not device-address-based** (see [WifiPeerRegistry]'s doc comment)
 * -- a deliberate simplification, not a silent gap: correlating an accepted server socket back to
 * a specific `WifiP2pDevice` needs a second `requestGroupInfo` round trip this pass doesn't do.
 *
 * **Written and compile-verified only -- not run against real Wi-Fi Direct hardware or radios.**
 * This dev environment has no physical Android device and Wi-Fi Direct cannot be meaningfully
 * exercised on the AVD emulator (no virtual Wi-Fi Direct radio), so — like the BLE driver's first
 * pass before a real device existed — this is unverified beyond "it builds," stated plainly per
 * `docs/IMPLEMENTATION-STATUS.md`'s convention. **Not wired into [india.projectmesh.app.MeshCoordinator]
 * or any UI yet**: `MeshCoordinator` still hardwires `BleTransportDriver` as the sole
 * [FfiMeshTransport] handed to `FfiMeshNode`; running BLE and Wi-Fi Direct simultaneously needs a
 * multiplexing transport that dispatches `send()` by which underlying link owns a peer handle and
 * merges both links' peer-handle spaces -- a separate integration pass, not implied by this one.
 *
 * Deliberate simplifications, flagged per this project's convention:
 * - No short-lived connect/exchange/disconnect cycle (`docs/TRANSPORT.md` §2.2's battery-
 *   conserving pattern, written for BLE but equally applicable here) -- sockets stay open once
 *   connected.
 * - No backoff/fairness for dense crowds; [WifiDirectConfig.MAX_CONNECTIONS] is a hardcoded cap.
 * - No bootstrapping discovery over BLE first, as the doc's §4 final sentence suggests
 *   ("Discovery can be bootstrapped over BLE") -- this driver runs its own independent DNS-SD
 *   discovery.
 * - No foreground-service integration (unlike BLE, not started from `MeshRelayService` yet).
 * - No recovery flow if Wi-Fi is disabled or the P2P group is torn down externally.
 */
@SuppressLint("MissingPermission") // Caller gates start() behind NEARBY_WIFI_DEVICES / ACCESS_FINE_LOCATION grants.
class WifiDirectTransportDriver(private val context: Context) : FfiMeshTransport {

    /** Set by whatever owns this driver once `FfiMeshNode` exists -- see [MeshEventSink]. */
    var eventSink: MeshEventSink? = null

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val peers = WifiPeerRegistry()
    private val executor = Executors.newCachedThreadPool()

    private class Connection(val socket: Socket, val output: DataOutputStream) {
        val writeLock = Any()
    }
    private val addressToConnection = ConcurrentHashMap<String, Connection>()

    // Remote P2P device address -> the hex service id its TXT record last advertised. The DNS-SD
    // txt-record and service-available callbacks fire separately (txt record first, in practice),
    // so this bridges them -- see dnsSdServiceListener's doc note.
    private val seenServiceIdByDeviceAddress = ConcurrentHashMap<String, String>()

    @Volatile private var localServiceIdHex: String = ""
    @Volatile private var localDeviceAddress: String? = null
    @Volatile private var connecting = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtraCompat<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    localDeviceAddress = device?.deviceAddress
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val mgr = manager
                    val ch = channel
                    if (mgr != null && ch != null) {
                        mgr.requestConnectionInfo(ch) { info -> handleConnectionInfo(info) }
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.i(TAG, "P2P state changed: enabled=${state == WifiP2pManager.WIFI_P2P_STATE_ENABLED}")
                }
                // WIFI_P2P_PEERS_CHANGED_ACTION carries no data this driver needs -- discovery
                // matches arrive through the DNS-SD listeners registered in start(), not a
                // requestPeers() poll.
            }
        }
    }

    fun connectedPeerCount(): Int = peers.connectedCount()
    fun isRunning(): Boolean = running

    // ---- FfiMeshTransport ---------------------------------------------------------------

    override fun start(serviceId: ByteArray) {
        if (running) {
            Log.w(TAG, "start() called while already running -- ignoring")
            return
        }
        val mgr = manager ?: throw FfiException.InvalidState("device has no Wi-Fi Direct support")
        localServiceIdHex = serviceId.toHexString()
        Log.i(TAG, "start(): serviceId=$localServiceIdHex")

        val ch = mgr.initialize(context, Looper.getMainLooper()) {
            Log.w(TAG, "Wi-Fi Direct channel disconnected")
        }
        channel = ch

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true

        mgr.setDnsSdResponseListeners(ch, dnsSdServiceListener, dnsSdTxtListener)

        val txtRecord = mapOf(WifiDirectConfig.TXT_KEY_SERVICE_ID to localServiceIdHex)
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            WifiDirectConfig.SERVICE_INSTANCE,
            WifiDirectConfig.SERVICE_REG_TYPE,
            txtRecord,
        )
        mgr.addLocalService(ch, serviceInfo, actionListener("addLocalService"))
        mgr.addServiceRequest(ch, WifiP2pDnsSdServiceRequest.newInstance(), actionListener("addServiceRequest"))
        mgr.discoverServices(ch, actionListener("discoverServices"))

        running = true
        startAccepting()
    }

    override fun stop() {
        if (!running) return
        running = false
        Log.i(TAG, "stop()")
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            runCatching { mgr.clearServiceRequests(ch, actionListener("clearServiceRequests")) }
            runCatching { mgr.clearLocalServices(ch, actionListener("clearLocalServices")) }
            runCatching { mgr.stopPeerDiscovery(ch, actionListener("stopPeerDiscovery")) }
            runCatching { mgr.removeGroup(ch, actionListener("removeGroup")) }
        }
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
            receiverRegistered = false
        }

        runCatching { serverSocket?.close() }
        serverSocket = null
        for ((address, conn) in addressToConnection) {
            runCatching { conn.socket.close() }
            peers.remove(address)
        }
        addressToConnection.clear()
        seenServiceIdByDeviceAddress.clear()
        channel = null
        localDeviceAddress = null
    }

    override fun send(peerHandle: ULong, frame: ByteArray) {
        val address = peers.addressFor(peerHandle)
            ?: throw FfiException.InvalidState("send(): unknown peer handle $peerHandle")
        val conn = addressToConnection[address]
            ?: throw FfiException.InvalidState("send(): no active socket to peer $peerHandle ($address)")
        try {
            synchronized(conn.writeLock) {
                SocketFraming.writeFrame(conn.output, frame)
            }
        } catch (e: IOException) {
            throw FfiException.InvalidState("send(): write to $address failed: ${e.message}")
        }
    }

    // ---- Discovery ------------------------------------------------------------------------

    private val dnsSdTxtListener = WifiP2pManager.DnsSdTxtRecordListener { _, txtRecordMap, srcDevice ->
        txtRecordMap[WifiDirectConfig.TXT_KEY_SERVICE_ID]?.let { sid ->
            seenServiceIdByDeviceAddress[srcDevice.deviceAddress] = sid
        }
    }

    /**
     * Fires once per discovered advertiser of our registration type. TXT record contents arrive
     * via a separate callback ([dnsSdTxtListener]) that in practice precedes this one for the
     * same device, but isn't guaranteed to have -- if it hasn't yet, this defers rather than
     * guesses, same "wait for a later callback that has it" pattern
     * `ble/BleTransportDriver.kt`'s `handleScanResult` uses for the BLE scan-response session ID.
     */
    private val dnsSdServiceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, srcDevice ->
        if (instanceName != WifiDirectConfig.SERVICE_INSTANCE) return@DnsSdServiceResponseListener
        val theirServiceId = seenServiceIdByDeviceAddress[srcDevice.deviceAddress]
        if (theirServiceId == null) {
            Log.d(TAG, "service seen from ${srcDevice.deviceAddress} before its TXT record -- will retry")
            return@DnsSdServiceResponseListener
        }
        if (theirServiceId != localServiceIdHex) {
            Log.d(TAG, "discovered ${srcDevice.deviceAddress} advertising a different mesh service id -- ignoring")
            return@DnsSdServiceResponseListener
        }
        maybeInitiateConnection(srcDevice)
    }

    private fun maybeInitiateConnection(remote: WifiP2pDevice) {
        if (connecting || peers.isConnected(remote.deviceAddress)) return
        val local = localDeviceAddress
        if (local == null) {
            Log.d(TAG, "no local device address yet -- deferring connect to ${remote.deviceAddress}")
            return
        }
        if (local >= remote.deviceAddress) {
            Log.d(TAG, "deferring to ${remote.deviceAddress}'s connection attempt (tie-break)")
            return
        }
        if (peers.connectedCount() >= WifiDirectConfig.MAX_CONNECTIONS) {
            Log.w(TAG, "discovered ${remote.deviceAddress} but at MAX_CONNECTIONS -- skipping")
            return
        }
        val mgr = manager ?: return
        val ch = channel ?: return
        val config = WifiP2pConfig().apply { deviceAddress = remote.deviceAddress }
        connecting = true
        Log.i(TAG, "initiating connection to ${remote.deviceAddress}")
        mgr.connect(ch, config, actionListener("connect(${remote.deviceAddress})") { connecting = false })
    }

    private fun handleConnectionInfo(info: WifiP2pInfo) {
        connecting = false
        if (!info.groupFormed) return
        if (info.isGroupOwner) {
            startAccepting()
        } else {
            val hostAddress = info.groupOwnerAddress?.hostAddress ?: return
            connectToGroupOwner(hostAddress)
        }
    }

    // ---- Group owner: accept inbound connections -------------------------------------------

    private fun startAccepting() {
        if (serverSocket != null) return
        executor.execute {
            try {
                val server = ServerSocket(WifiDirectConfig.SOCKET_PORT)
                serverSocket = server
                Log.i(TAG, "group owner: listening on port ${WifiDirectConfig.SOCKET_PORT}")
                while (running) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        if (running) Log.w(TAG, "accept() failed", e)
                        break
                    }
                    registerConnection(socket)
                }
            } catch (e: IOException) {
                if (running) Log.e(TAG, "failed to open server socket", e)
            }
        }
    }

    // ---- Client: connect out to the group owner --------------------------------------------

    private fun connectToGroupOwner(hostAddress: String) {
        if (addressToConnection.containsKey(hostAddress)) return
        executor.execute {
            try {
                val socket = Socket(hostAddress, WifiDirectConfig.SOCKET_PORT)
                registerConnection(socket)
            } catch (e: IOException) {
                Log.e(TAG, "connect to group owner $hostAddress failed", e)
            }
        }
    }

    // ---- Shared: connection bookkeeping + read loop ----------------------------------------

    private fun registerConnection(socket: Socket) {
        val address = socket.inetAddress?.hostAddress
        if (address == null) {
            Log.w(TAG, "accepted socket with no resolvable remote address -- closing")
            runCatching { socket.close() }
            return
        }
        val handle = peers.handleFor(address)
        if (handle == null) {
            Log.w(TAG, "connection from $address rejected -- at MAX_CONNECTIONS")
            runCatching { socket.close() }
            return
        }
        addressToConnection[address] = Connection(socket, DataOutputStream(socket.getOutputStream()))
        Log.i(TAG, "connected to $address -> peer handle $handle")
        eventSink?.let { sink ->
            runCatching { sink.onPeerConnected(handle) }
                .onFailure { Log.w(TAG, "onPeerConnected($handle) failed", it) }
        }
        executor.execute { readLoop(address, handle, socket) }
    }

    private fun readLoop(address: String, handle: ULong, socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        try {
            while (running) {
                val frame = SocketFraming.readFrame(input) ?: break
                eventSink?.let { sink ->
                    runCatching { sink.onFrame(handle, frame) }
                        .onFailure { Log.w(TAG, "onFrame($handle, ${frame.size}B) failed", it) }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "read loop for $address ($handle) ended: ${e.message}")
        } finally {
            dropPeer(address, socket)
        }
    }

    private fun dropPeer(address: String, socket: Socket) {
        runCatching { socket.close() }
        addressToConnection.remove(address)
        val handle = peers.remove(address) ?: return
        eventSink?.onPeerLost(handle)
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun actionListener(what: String, onFailureAlso: (() -> Unit)? = null) =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "$what: success")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "$what: failed, reason=${reason.toP2pReasonName()}")
                onFailureAlso?.invoke()
            }
        }
}

private fun Int.toP2pReasonName(): String = when (this) {
    WifiP2pManager.ERROR -> "ERROR"
    WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
    WifiP2pManager.BUSY -> "BUSY"
    WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
    else -> "UNKNOWN($this)"
}

private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
