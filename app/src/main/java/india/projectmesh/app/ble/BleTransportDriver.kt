package india.projectmesh.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import uniffi.mesh_core.FfiException
import uniffi.mesh_core.FfiMeshTransport

private const val TAG = "MeshBle"
private const val DEFAULT_ATT_MTU = 23
private const val REQUESTED_ATT_MTU = 517
private const val ATT_HEADER_SIZE = 3
private const val SESSION_ID_SIZE = 4

/**
 * How often the advertised session ID (and, on the OS's own schedule, the underlying BLE MAC/
 * link-layer address) rotates -- `docs/CRYPTOGRAPHY.md` §7.1's "order of ~15 minutes," so a
 * passive observer can't trivially correlate this device across time/place by a stable link
 * identifier. Reasoned, not benchmarked against real tracking-resistance field data.
 */
private const val SESSION_ID_ROTATION_INTERVAL_MS = 15 * 60 * 1000L

/**
 * Real `android.bluetooth.*` implementation of [FfiMeshTransport]. Every device runs both GATT
 * roles concurrently, per `docs/TRANSPORT.md` §2: a [BluetoothGattServer] (peripheral: advertise
 * + serve) and, per discovered peer, a [BluetoothGatt] client connection (central: scan +
 * connect). See the plan doc for the full design (`docs/PROGRESS.md` will get a dated entry once
 * this is verified against real radios, not just compiled).
 *
 * Deliberate simplifications, flagged per this project's convention (not silently assumed
 * complete) -- also recorded in `docs/IMPLEMENTATION-STATUS.md`:
 * - Connections are kept open while peers stay in range, not TRANSPORT.md §2.2's short-lived
 *   connect/exchange/disconnect cycle.
 * - No backoff/fairness for dense crowds -- [MAX_CONNECTIONS] is a hardcoded cap; peers beyond
 *   it are logged and skipped.
 * - The dual-role connect race is resolved by a coarse session-ID tie-break (see
 *   [sessionIdIsLower]), validated only for pairs, not larger topologies.
 * - No foreground service -- this only runs while the app is in the foreground.
 * - No BLE-adapter-disabled recovery flow.
 *
 * **Link-identifier rotation** (`docs/CRYPTOGRAPHY.md` §7.1): the advertised [localSessionId]
 * (scan-response service data, used only for the connect tie-break) is regenerated and
 * re-advertised every [SESSION_ID_ROTATION_INTERVAL_MS]. **Honest scope note:** this rotates the
 * *application-layer* identifier this driver itself chose to broadcast -- it does not and cannot
 * control the underlying BLE MAC/link-layer address, which is the Android platform's own
 * privacy policy (`BluetoothLeAdvertiser` has no public API to force that). Rotating our own
 * identifier on a matching cadence means it doesn't undermine whatever MAC rotation the OS is
 * already doing; a device already connected through an existing GATT link is unaffected by a
 * rotation (the advertised ID is only consulted pre-connection, during scanning/tie-break).
 */
@SuppressLint("MissingPermission") // Caller (MainActivity) gates start() behind runtime permission grants.
class BleTransportDriver(private val context: Context) : FfiMeshTransport {

    /** Set by the coordinator once `FfiMeshNode` exists -- see [MeshEventSink]'s doc comment. */
    var eventSink: MeshEventSink? = null

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val peers = BlePeerRegistry()
    private val reassembler = Reassembler()
    private val messageIds = MessageIdCounter()

    // All keyed by BLE device address -- the address is stable for the lifetime of one
    // connection, which is all this transport needs (no cross-session peer identity here).
    private val addressToDevice = ConcurrentHashMap<String, BluetoothDevice>()
    private val addressToMtu = ConcurrentHashMap<String, Int>()
    private val addressToClientGatt = ConcurrentHashMap<String, BluetoothGatt>()
    private val addressToServerSubscribed = ConcurrentHashMap<String, Boolean>()
    private val connectingAddresses = ConcurrentHashMap.newKeySet<String>()

    // @Volatile + always-replace-the-reference-never-mutate-in-place (see rotateSessionId) so a
    // scan/GATT callback on a Binder thread never observes a torn/partially-regenerated array.
    @Volatile private var localSessionId: ByteArray = ByteArray(SESSION_ID_SIZE)
    private val rotationHandler = Handler(Looper.getMainLooper())
    private val rotationRunnable = object : Runnable {
        override fun run() {
            rotateSessionId()
            rotationHandler.postDelayed(this, SESSION_ID_ROTATION_INTERVAL_MS)
        }
    }
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var outboundCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile private var running = false

    /** Number of peers currently connected (either role). For UI status display. */
    fun connectedPeerCount(): Int = peers.connectedCount()

    // ---- FfiMeshTransport ---------------------------------------------------------------

    override fun start(serviceId: ByteArray) {
        if (running) {
            Log.w(TAG, "start() called while already running -- ignoring")
            return
        }
        val bleAdapter = adapter
        if (bleAdapter == null || !bleAdapter.isEnabled) {
            throw FfiException.InvalidState("Bluetooth adapter unavailable or disabled")
        }
        localSessionId = freshSessionId()
        Log.i(TAG, "start(): service=${BleUuids.SERVICE} sessionId=${localSessionId.toHexString()}")

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val server = gattServer ?: throw FfiException.InvalidState("openGattServer returned null")

        val inbound = BluetoothGattCharacteristic(
            BleUuids.INBOUND_CHAR,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val outbound = BluetoothGattCharacteristic(
            BleUuids.OUTBOUND_CHAR,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        outbound.addDescriptor(
            BluetoothGattDescriptor(
                BleUuids.CLIENT_CONFIG_DESCRIPTOR,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        outboundCharacteristic = outbound

        val service = BluetoothGattService(BleUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(inbound)
        service.addCharacteristic(outbound)
        server.addService(service)

        advertiser = bleAdapter.bluetoothLeAdvertiser
        scanner = bleAdapter.bluetoothLeScanner
        if (advertiser == null || scanner == null) {
            throw FfiException.InvalidState("device does not support BLE advertising or scanning")
        }

        startAdvertising()
        startScanning()
        running = true
        rotationHandler.postDelayed(rotationRunnable, SESSION_ID_ROTATION_INTERVAL_MS)
    }

    override fun stop() {
        if (!running) return
        running = false
        Log.i(TAG, "stop()")
        rotationHandler.removeCallbacks(rotationRunnable)

        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
            .onFailure { Log.w(TAG, "stopAdvertising failed", it) }
        runCatching { scanner?.stopScan(scanCallback) }
            .onFailure { Log.w(TAG, "stopScan failed", it) }

        for (gatt in addressToClientGatt.values) {
            runCatching { gatt.disconnect(); gatt.close() }
        }
        addressToClientGatt.clear()

        runCatching { gattServer?.close() }
        gattServer = null
        advertiser = null
        scanner = null
        outboundCharacteristic = null

        addressToDevice.clear()
        addressToMtu.clear()
        addressToServerSubscribed.clear()
        connectingAddresses.clear()
    }

    override fun send(peerHandle: ULong, frame: ByteArray) {
        val address = peers.addressFor(peerHandle)
            ?: throw FfiException.InvalidState("send(): unknown peer handle $peerHandle")

        val mtu = addressToMtu[address] ?: DEFAULT_ATT_MTU
        val chunkSize = (mtu - ATT_HEADER_SIZE - FRAGMENT_HEADER_SIZE).coerceAtLeast(1)
        val fragments = Fragmentation.encodeFragments(messageIds.next(peerHandle), frame, chunkSize)
        Log.d(TAG, "send(): peer=$peerHandle ($address) ${frame.size}B -> ${fragments.size} fragment(s), mtu=$mtu")

        val clientGatt = addressToClientGatt[address]
        when {
            clientGatt != null -> sendAsClient(clientGatt, address, fragments)
            addressToServerSubscribed[address] == true -> sendAsServer(address, fragments)
            else -> throw FfiException.InvalidState("send(): no active link to peer $peerHandle ($address)")
        }
    }

    // ---- Central role: advertise ---------------------------------------------------------

    private fun freshSessionId(): ByteArray = ByteArray(SESSION_ID_SIZE).also { SecureRandom().nextBytes(it) }

    /**
     * Regenerate [localSessionId] and re-advertise it. BLE advertising payloads are immutable
     * once started (`BluetoothLeAdvertiser` has no "update in place" call), so this stops and
     * restarts advertising with fresh [AdvertiseData] -- existing GATT connections are
     * unaffected (see the class doc's rotation note).
     */
    private fun rotateSessionId() {
        if (!running) return
        localSessionId = freshSessionId()
        Log.i(TAG, "rotating advertised session id -> ${localSessionId.toHexString()}")
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
            .onFailure { Log.w(TAG, "stopAdvertising during rotation failed", it) }
        startAdvertising()
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        // 128-bit service UUID + service-data session ID don't both fit in one 31-byte legacy
        // advertising packet -- the UUID goes in the primary advertisement, the session ID (used
        // only for the connection tie-break, see BlePeerRegistry.sessionIdIsLower) in the scan
        // response, which scanners receive merged into ScanResult.scanRecord by default.
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleUuids.SERVICE))
            .build()
        val scanResponseData = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(BleUuids.SERVICE), localSessionId)
            .build()
        advertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "advertising failed to start: ${advertiseErrorName(errorCode)}")
        }
    }

    private fun advertiseErrorName(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "UNKNOWN($code)"
    }

    // ---- Central role: scan + connect -----------------------------------------------------

    private fun startScanning() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed: errorCode=$errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        if (peers.isConnected(address) || connectingAddresses.contains(address)) return

        val remoteSessionId = result.scanRecord?.serviceData?.get(ParcelUuid(BleUuids.SERVICE))
        if (remoteSessionId == null) {
            // Scan response (which carries the session ID) hasn't been merged into this result
            // yet -- wait for a later scan callback that has it.
            return
        }
        if (!sessionIdIsLower(localSessionId, remoteSessionId)) {
            Log.d(TAG, "discovered $address, deferring to their connection attempt (tie-break)")
            return
        }
        if (peers.connectedCount() >= MAX_CONNECTIONS) {
            Log.w(TAG, "discovered $address but at MAX_CONNECTIONS=$MAX_CONNECTIONS -- skipping")
            return
        }

        Log.i(TAG, "discovered $address rssi=${result.rssi}, initiating connection")
        connectingAddresses.add(address)
        addressToDevice[address] = device
        device.connectGatt(context, false, gattClientCallback)
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "client connected to $address (status=$status), requesting MTU")
                    addressToClientGatt[address] = gatt
                    gatt.requestMtu(REQUESTED_ATT_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "client disconnected from $address (status=$status)")
                    connectingAddresses.remove(address)
                    addressToClientGatt.remove(address)
                    addressToMtu.remove(address)
                    dropPeer(address)
                    runCatching { gatt.close() }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            addressToMtu[gatt.device.address] = mtu
            Log.i(TAG, "client MTU negotiated with ${gatt.device.address}: $mtu (status=$status)")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            val outbound = gatt.getService(BleUuids.SERVICE)?.getCharacteristic(BleUuids.OUTBOUND_CHAR)
            if (outbound == null) {
                Log.e(TAG, "service discovery on $address didn't find OUTBOUND_CHAR -- disconnecting")
                runCatching { gatt.disconnect() }
                return
            }
            gatt.setCharacteristicNotification(outbound, true)
            val cccd = outbound.getDescriptor(BleUuids.CLIENT_CONFIG_DESCRIPTOR)
            if (cccd != null) {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            } else {
                Log.w(TAG, "OUTBOUND_CHAR on $address has no CCCD -- notifications may not arrive")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != BleUuids.CLIENT_CONFIG_DESCRIPTOR) return
            val address = gatt.device.address
            connectingAddresses.remove(address)
            val handle = peers.handleFor(address)
            if (handle == null) {
                Log.w(TAG, "notifications enabled on $address but connection cap reached -- disconnecting")
                runCatching { gatt.disconnect() }
                return
            }
            Log.i(TAG, "notifications enabled on $address -> peer handle $handle, connected")
            eventSink?.let { sink ->
                runCatching { sink.onPeerConnected(handle) }
                    .onFailure { Log.w(TAG, "onPeerConnected($handle) failed", it) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != BleUuids.OUTBOUND_CHAR) return
            deliverFragment(gatt.device.address, value)
        }
    }

    private fun sendAsClient(gatt: BluetoothGatt, address: String, fragments: List<ByteArray>) {
        val inbound = gatt.getService(BleUuids.SERVICE)?.getCharacteristic(BleUuids.INBOUND_CHAR)
        if (inbound == null) {
            throw FfiException.InvalidState("no INBOUND_CHAR discovered on $address")
        }
        for (fragment in fragments) {
            @Suppress("DEPRECATION")
            inbound.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            inbound.value = fragment
            @Suppress("DEPRECATION")
            val accepted = gatt.writeCharacteristic(inbound)
            if (!accepted) {
                throw FfiException.InvalidState("writeCharacteristic rejected locally for $address")
            }
        }
    }

    // ---- Peripheral role: GATT server -----------------------------------------------------

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "server: $address connected (status=$status), awaiting subscription")
                    addressToDevice[address] = device
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "server: $address disconnected (status=$status)")
                    addressToServerSubscribed.remove(address)
                    dropPeer(address)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleUuids.INBOUND_CHAR) {
                if (addressToServerSubscribed[device.address] == true) {
                    deliverFragment(device.address, value)
                } else {
                    Log.w(TAG, "dropping write from ${device.address} -- not yet subscribed (out of protocol order)")
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == BleUuids.CLIENT_CONFIG_DESCRIPTOR) {
                val address = device.address
                val subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                addressToServerSubscribed[address] = subscribed
                if (subscribed) {
                    val handle = peers.handleFor(address)
                    if (handle == null) {
                        Log.w(TAG, "server: $address subscribed but connection cap reached")
                    } else {
                        Log.i(TAG, "server: $address subscribed -> peer handle $handle, connected")
                        eventSink?.let { sink ->
                            runCatching { sink.onPeerConnected(handle) }
                                .onFailure { Log.w(TAG, "onPeerConnected($handle) failed", it) }
                        }
                    }
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            addressToMtu[device.address] = mtu
            Log.i(TAG, "server: MTU changed for ${device.address}: $mtu")
        }
    }

    private fun sendAsServer(address: String, fragments: List<ByteArray>) {
        val device = addressToDevice[address]
            ?: throw FfiException.InvalidState("send(): no BluetoothDevice tracked for $address")
        val characteristic = outboundCharacteristic
            ?: throw FfiException.InvalidState("send(): server not started")
        for (fragment in fragments) {
            @Suppress("DEPRECATION")
            characteristic.value = fragment
            @Suppress("DEPRECATION")
            val queued = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
            if (!queued) {
                throw FfiException.InvalidState("notifyCharacteristicChanged rejected locally for $address")
            }
        }
    }

    // ---- Shared -----------------------------------------------------------------------------

    private fun deliverFragment(address: String, raw: ByteArray) {
        val handle = peers.handleFor(address)
        if (handle == null) {
            Log.w(TAG, "dropping fragment from $address -- no peer handle (cap reached or not yet connected)")
            return
        }
        val complete = reassembler.offer(handle, raw) ?: return
        Log.d(TAG, "reassembled ${complete.size}B frame from peer=$handle ($address)")
        eventSink?.let { sink ->
            runCatching { sink.onFrame(handle, complete) }
                .onFailure { Log.w(TAG, "onFrame($handle, ${complete.size}B) failed", it) }
        }
    }

    private fun dropPeer(address: String) {
        addressToDevice.remove(address)
        val handle = peers.remove(address) ?: return
        reassembler.clear(handle)
        messageIds.clear(handle)
        eventSink?.onPeerLost(handle)
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
