package india.projectmesh.app

import android.util.Log
import india.projectmesh.app.ble.BleTransportDriver
import india.projectmesh.app.ble.MeshEventSink
import india.projectmesh.app.wifidirect.WifiDirectTransportDriver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import uniffi.mesh_core.FfiException
import uniffi.mesh_core.FfiMeshTransport

private const val TAG = "MeshMultiTransport"

/**
 * Composes [BleTransportDriver] + [WifiDirectTransportDriver] behind one [FfiMeshTransport], so
 * `FfiMeshNode` -- which takes exactly one transport at construction -- can drive both links at
 * once, per `docs/TRANSPORT.md` §1's "the core treats all of these as interchangeable links
 * carrying opaque frames."
 *
 * **The real problem this solves:** each driver numbers its own peers independently starting at
 * 1 (`BlePeerRegistry`/`WifiPeerRegistry` are transport-local) -- handing both drivers' raw
 * handles straight to `FfiMeshNode` would collide (BLE's "peer 1" and Wi-Fi Direct's "peer 1" are
 * different peers) and `FfiMeshNode.send(peerHandle, ...)` has no way to know which transport
 * produced a given handle. This class sets itself as *both* drivers' [MeshEventSink], remapping
 * each `(link, localHandle)` pair into one global `ULong` on the way in, and reverses that lookup
 * in [send] on the way out -- the only place either translation needs to happen.
 *
 * **Not a decision about which link to prefer.** Both run concurrently and independently; a peer
 * reachable over both BLE and Wi-Fi Direct simultaneously is represented as two distinct peer
 * handles today (one per link), same as if it were two different physical peers -- `RelayEngine`'s
 * existing dedup-by-envelope-ID already makes double-delivery over both links harmless, just not
 * bandwidth-optimal. Deduplicating that at the peer-identity level would need real peer identity
 * at the transport layer, which per [india.projectmesh.app.wifidirect.WifiPeerRegistry]'s own doc
 * comment this transport stack doesn't have yet.
 */
class MultiTransport(
    private val ble: BleTransportDriver,
    private val wifiDirect: WifiDirectTransportDriver,
) : FfiMeshTransport {

    /** Set by [MeshCoordinator] once `FfiMeshNode` exists -- see [MeshEventSink]'s doc comment. */
    var eventSink: MeshEventSink? = null

    private enum class Link { BLE, WIFI_DIRECT }
    private data class LocalPeer(val link: Link, val localHandle: ULong)

    private val nextGlobalHandle = AtomicLong(1L) // 0 reserved, matches both drivers' own convention
    private val globalToLocal = ConcurrentHashMap<ULong, LocalPeer>()
    private val localToGlobal = ConcurrentHashMap<LocalPeer, ULong>()

    init {
        ble.eventSink = object : MeshEventSink {
            override fun onPeerConnected(peerHandle: ULong) = onLocalPeerConnected(Link.BLE, peerHandle)
            override fun onFrame(peerHandle: ULong, bytes: ByteArray) = onLocalFrame(Link.BLE, peerHandle, bytes)
            override fun onPeerLost(peerHandle: ULong) = onLocalPeerLost(Link.BLE, peerHandle)
        }
        wifiDirect.eventSink = object : MeshEventSink {
            override fun onPeerConnected(peerHandle: ULong) = onLocalPeerConnected(Link.WIFI_DIRECT, peerHandle)
            override fun onFrame(peerHandle: ULong, bytes: ByteArray) = onLocalFrame(Link.WIFI_DIRECT, peerHandle, bytes)
            override fun onPeerLost(peerHandle: ULong) = onLocalPeerLost(Link.WIFI_DIRECT, peerHandle)
        }
    }

    // ---- FfiMeshTransport ---------------------------------------------------------------

    override fun start(serviceId: ByteArray) {
        ble.start(serviceId)
        // Wi-Fi Direct isn't available on every device (no hardware, or the OS reports no P2P
        // support -- WifiDirectTransportDriver.start() throws FfiException.InvalidState in that
        // case). BLE is this project's universal baseline (docs/TRANSPORT.md §2); losing the
        // higher-throughput Wi-Fi Direct link shouldn't take the whole mesh down with it.
        runCatching { wifiDirect.start(serviceId) }
            .onFailure { Log.w(TAG, "Wi-Fi Direct failed to start -- continuing on BLE only", it) }
    }

    override fun stop() {
        runCatching { ble.stop() }.onFailure { Log.w(TAG, "ble.stop() failed", it) }
        runCatching { wifiDirect.stop() }.onFailure { Log.w(TAG, "wifiDirect.stop() failed", it) }
        globalToLocal.clear()
        localToGlobal.clear()
    }

    override fun send(peerHandle: ULong, frame: ByteArray) {
        val local = globalToLocal[peerHandle]
            ?: throw FfiException.InvalidState("send(): unknown peer handle $peerHandle")
        when (local.link) {
            Link.BLE -> ble.send(local.localHandle, frame)
            Link.WIFI_DIRECT -> wifiDirect.send(local.localHandle, frame)
        }
    }

    // ---- Status, for MeshCoordinator/UI ---------------------------------------------------

    fun connectedPeerCount(): Int = ble.connectedPeerCount() + wifiDirect.connectedPeerCount()

    /** Running if either link is -- matches [start]'s "one link failing doesn't stop the mesh." */
    fun isRunning(): Boolean = ble.isRunning() || wifiDirect.isRunning()

    // ---- Handle translation ----------------------------------------------------------------

    private fun onLocalPeerConnected(link: Link, localHandle: ULong) {
        val key = LocalPeer(link, localHandle)
        val global = localToGlobal.computeIfAbsent(key) { nextGlobalHandle.getAndIncrement().toULong() }
        globalToLocal[global] = key
        eventSink?.let { sink ->
            runCatching { sink.onPeerConnected(global) }
                .onFailure { Log.w(TAG, "onPeerConnected($global) failed", it) }
        }
    }

    private fun onLocalFrame(link: Link, localHandle: ULong, bytes: ByteArray) {
        val global = localToGlobal[LocalPeer(link, localHandle)]
        if (global == null) {
            Log.w(TAG, "frame from untracked local peer $localHandle on $link -- dropping")
            return
        }
        eventSink?.let { sink ->
            runCatching { sink.onFrame(global, bytes) }
                .onFailure { Log.w(TAG, "onFrame($global, ${bytes.size}B) failed", it) }
        }
    }

    private fun onLocalPeerLost(link: Link, localHandle: ULong) {
        val key = LocalPeer(link, localHandle)
        val global = localToGlobal.remove(key) ?: return
        globalToLocal.remove(global)
        eventSink?.onPeerLost(global)
    }
}
