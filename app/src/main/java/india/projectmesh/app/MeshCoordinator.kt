package india.projectmesh.app

import android.content.Context
import android.util.Base64
import android.util.Log
import india.projectmesh.app.ble.BleTransportDriver
import india.projectmesh.app.ble.BleUuids
import india.projectmesh.app.ble.MeshEventSink
import java.io.File
import java.security.SecureRandom
import uniffi.mesh_core.FfiException
import uniffi.mesh_core.FfiMeshNode

private const val TAG = "MeshCoordinator"
private const val PREFS_NAME = "mesh_master_key"
private const val PREFS_KEY_MASTER_KEY = "master_key_b64"
private const val MASTER_KEY_SIZE = 32
private const val STORE_CAPACITY: UInt = 500u

/**
 * Owns one [BleTransportDriver] and the [FfiMeshNode] it drives. Resolves the
 * construction-order circularity between the two (the node needs the transport at construction;
 * the transport needs a way to call back into the node once it exists) via [MeshEventSink] --
 * see the plan doc's "Key design decision" section.
 *
 * **Master key gap, flagged not silently patched:** `FfiMeshNode.open` wants a 32-byte key
 * documented as needing platform-Keystore backing (Android Keystore / iOS Secure Enclave, per
 * `docs/CRYPTOGRAPHY.md` §8). No Keystore integration exists anywhere in this repo yet -- this
 * class generates 32 random bytes once via [SecureRandom] and stores them Base64-encoded in a
 * private `SharedPreferences` file. **Not hardware-backed.** Tracked as a separate outstanding
 * item in `docs/IMPLEMENTATION-STATUS.md`, not bundled into "BLE driver done."
 */
class MeshCoordinator(private val context: Context) {
    val driver = BleTransportDriver(context)
    private val node: FfiMeshNode

    init {
        val masterKey = loadOrCreateMasterKey(context)
        val dbPath = File(context.filesDir, "mesh-store.redb").absolutePath
        node = FfiMeshNode.open(dbPath, masterKey, STORE_CAPACITY, nowSeconds(), driver)
        driver.eventSink = object : MeshEventSink {
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

    /** Begin advertising, scanning, and serving GATT connections. */
    fun start() {
        driver.start(BleUuids.serviceIdBytes())
    }

    fun stop() {
        driver.stop()
    }

    fun connectedPeerCount(): Int = driver.connectedPeerCount()

    fun isRunning(): Boolean = driver.isRunning()

    fun node(): FfiMeshNode = node

    private fun logFfiFailure(what: String, peerHandle: ULong, e: Throwable) {
        // A single malformed/rejected frame from a misbehaving peer shouldn't crash the app --
        // the *decision* to reject already happened in Rust; this layer's only job is not to
        // crash on it (docs/ARCHITECTURE.md §1's "dumb byte pipe, no decisions").
        val reason = if (e is FfiException) e.message else e.toString()
        Log.w(TAG, "$what for peer=$peerHandle failed: $reason")
    }

    private fun nowSeconds(): ULong = (System.currentTimeMillis() / 1000L).toULong()

    private fun loadOrCreateMasterKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(PREFS_KEY_MASTER_KEY, null)?.let { encoded ->
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            if (decoded.size == MASTER_KEY_SIZE) return decoded
            Log.w(TAG, "stored master key has wrong length (${decoded.size}), regenerating")
        }
        val fresh = ByteArray(MASTER_KEY_SIZE)
        SecureRandom().nextBytes(fresh)
        prefs.edit().putString(PREFS_KEY_MASTER_KEY, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }
}
