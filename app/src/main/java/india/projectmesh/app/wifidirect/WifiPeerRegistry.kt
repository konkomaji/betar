package india.projectmesh.app.wifidirect

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Maps a connected socket's remote address (`Socket.inetAddress.hostAddress`, unique within one
 * P2P group's DHCP-assigned subnet) to the `u64` (`ULong`) peer handles the FFI layer expects,
 * and enforces [WifiDirectConfig.MAX_CONNECTIONS]. Same shape and same "no cross-session peer
 * identity here" scope as `ble/BlePeerRegistry.kt` -- handles are process-lifetime only, the
 * higher `Identity`/crypto layer is what actually authenticates a peer, untouched here.
 *
 * **Deliberate simplification vs. the BLE registry:** keyed by IP rather than the P2P device's
 * MAC-like `deviceAddress`, because a group-owner-accepted [java.net.Socket] only cheaply exposes
 * the former -- correlating an accepted socket back to a specific `WifiP2pDevice` needs a second
 * `requestGroupInfo` round trip this pass doesn't do. Sufficient since this layer's only job is a
 * stable handle for one connection's lifetime, not peer identity.
 */
class WifiPeerRegistry {
    private val addressToHandle = ConcurrentHashMap<String, ULong>()
    private val handleToAddress = ConcurrentHashMap<ULong, String>()
    private val nextHandle = AtomicLong(1L) // 0 reserved, avoids confusion with an uninitialized default

    /** Existing handle for `address`, or a freshly assigned one if under the connection cap. */
    fun handleFor(address: String): ULong? {
        addressToHandle[address]?.let { return it }
        if (addressToHandle.size >= WifiDirectConfig.MAX_CONNECTIONS) return null
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

    fun connectedCount(): Int = addressToHandle.size

    fun isConnected(address: String): Boolean = addressToHandle.containsKey(address)

    fun allAddresses(): List<String> = addressToHandle.keys.toList()
}
