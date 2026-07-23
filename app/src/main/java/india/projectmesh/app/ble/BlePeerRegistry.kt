package india.projectmesh.app.ble

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Hardcoded cap on simultaneous connected peers -- reasoned, not benchmarked (see plan doc). */
const val MAX_CONNECTIONS = 4

/**
 * Maps BLE device addresses to the `u64` (`ULong` on the Kotlin side) peer handles the FFI
 * layer expects, and enforces a coarse connection cap. Handles are process-lifetime only --
 * fresh on every app run, since the Rust side has no notion of a stable peer identity at this
 * transport layer (only the higher `Identity`/crypto layer does, untouched here).
 */
class BlePeerRegistry {
    private val addressToHandle = ConcurrentHashMap<String, ULong>()
    private val handleToAddress = ConcurrentHashMap<ULong, String>()
    private val nextHandle = AtomicLong(1L) // 0 reserved, avoids confusion with an uninitialized default

    /** Existing handle for `address`, or a freshly assigned one if under the connection cap. */
    fun handleFor(address: String): ULong? {
        addressToHandle[address]?.let { return it }
        if (addressToHandle.size >= MAX_CONNECTIONS) return null
        val handle = nextHandle.getAndIncrement().toULong()
        addressToHandle[address] = handle
        handleToAddress[handle] = address
        return handle
    }

    fun addressFor(handle: ULong): String? = handleToAddress[handle]

    /** Removes `address`'s mapping, if any, and returns the handle it used to have. */
    fun remove(address: String): ULong? {
        val handle = addressToHandle.remove(address) ?: return null
        handleToAddress.remove(handle)
        return handle
    }

    fun removeByHandle(handle: ULong) {
        handleToAddress.remove(handle)?.let { addressToHandle.remove(it) }
    }

    fun connectedCount(): Int = addressToHandle.size

    fun isConnected(address: String): Boolean = addressToHandle.containsKey(address)
}

/**
 * Both sides of a pair are simultaneously advertising and scanning, so each could discover and
 * initiate a connection to the other -- two physical links for one logical peer. Android's public
 * API deliberately hides a device's own real Bluetooth MAC (`BluetoothAdapter.getAddress()`
 * returns a constant dummy value since API 23), so the tie-break can't compare local vs. remote
 * *address*. Instead, each side generates a random session ID at `start()` and advertises it in
 * BLE scan-response service data; whichever side's session ID sorts lower (unsigned lexicographic
 * byte comparison) initiates the outbound connection, the other waits to be connected to. Coarse
 * (pairwise only, no real fairness for 3+ device topologies) but required for a correct
 * two-device test -- see plan doc §"Peer handles and connection lifecycle".
 */
fun sessionIdIsLower(local: ByteArray, remote: ByteArray): Boolean {
    val len = minOf(local.size, remote.size)
    for (i in 0 until len) {
        val l = local[i].toInt() and 0xFF
        val r = remote[i].toInt() and 0xFF
        if (l != r) return l < r
    }
    // Equal-length equal content is an astronomically unlikely collision for a random ID; treat
    // as "don't initiate" on both sides rather than risk a double-connect race. Logged by the
    // caller, not here (this function has no logging dependency).
    return local.size < remote.size
}
