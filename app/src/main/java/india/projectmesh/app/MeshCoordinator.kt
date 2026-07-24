package india.projectmesh.app

import android.content.Context
import android.util.Log
import india.projectmesh.app.ble.BleTransportDriver
import india.projectmesh.app.ble.BleUuids
import india.projectmesh.app.ble.MeshEventSink
import india.projectmesh.app.wifidirect.WifiDirectTransportDriver
import java.io.File
import uniffi.mesh_core.FfiException
import uniffi.mesh_core.FfiMeshNode

private const val TAG = "MeshCoordinator"
private const val STORE_CAPACITY: UInt = 500u

/**
 * Owns [MultiTransport] (BLE + Wi-Fi Direct composed behind one `FfiMeshTransport`) and the
 * [FfiMeshNode] it drives. Resolves the construction-order circularity between the two (the node
 * needs the transport at construction; the transport needs a way to call back into the node once
 * it exists) via [MeshEventSink] -- see the plan doc's "Key design decision" section.
 *
 * **Master key is now Keystore-wrapped** -- see [KeystoreMasterKey]'s doc comment for the full
 * design. Previously this class generated the key with `SecureRandom` and stored it as plain
 * base64 in `SharedPreferences`, readable by anyone with root or backup-extraction access; that
 * gap is closed as of this pass, not bundled silently into an earlier "done" claim.
 */
class MeshCoordinator(private val context: Context) {
    private val ble = BleTransportDriver(context)
    private val wifiDirect = WifiDirectTransportDriver(context)
    val transport = MultiTransport(ble, wifiDirect)
    private val node: FfiMeshNode

    init {
        val masterKey = KeystoreMasterKey.loadOrCreate(context)
        val dbPath = File(context.filesDir, "mesh-store.redb").absolutePath
        node = FfiMeshNode.open(dbPath, masterKey, STORE_CAPACITY, nowSeconds(), transport)
        transport.eventSink = object : MeshEventSink {
            override fun onPeerConnected(peerHandle: ULong) {
                runCatching { node.onPeerConnected(peerHandle) }
                    .onFailure { e -> logFfiFailure("onPeerConnected", peerHandle, e) }
            }

            override fun onFrame(peerHandle: ULong, bytes: ByteArray) {
                runCatching { node.onFrame(peerHandle, bytes, nowSeconds()) }
                    .onFailure { e -> logFfiFailure("onFrame(${bytes.size}B)", peerHandle, e) }
            }

            override fun onPeerLost(peerHandle: ULong) {
                node.onPeerLost(peerHandle)
            }
        }
    }

    /** Begin advertising/scanning (BLE) and service discovery (Wi-Fi Direct) on both links. */
    fun start() {
        transport.start(BleUuids.serviceIdBytes())
    }

    fun stop() {
        transport.stop()
    }

    fun connectedPeerCount(): Int = transport.connectedPeerCount()

    fun isRunning(): Boolean = transport.isRunning()

    fun node(): FfiMeshNode = node

    private fun logFfiFailure(what: String, peerHandle: ULong, e: Throwable) {
        // A single malformed/rejected frame from a misbehaving peer shouldn't crash the app --
        // the *decision* to reject already happened in Rust; this layer's only job is not to
        // crash on it (docs/ARCHITECTURE.md §1's "dumb byte pipe, no decisions").
        val reason = if (e is FfiException) e.message else e.toString()
        Log.w(TAG, "$what for peer=$peerHandle failed: $reason")
    }

    private fun nowSeconds(): ULong = (System.currentTimeMillis() / 1000L).toULong()
}
