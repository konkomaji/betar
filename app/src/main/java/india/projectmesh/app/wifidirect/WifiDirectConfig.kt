package india.projectmesh.app.wifidirect

/**
 * Project Mesh's Wi-Fi Direct service-discovery + socket profile, `docs/TRANSPORT.md` §4. Fixed
 * once real devices exist -- never regenerate these, same convention as `ble/BleUuids.kt`.
 */
object WifiDirectConfig {
    /** Bonjour/DNS-SD instance + registration type this app advertises and searches for. */
    const val SERVICE_INSTANCE = "_projectmesh"
    const val SERVICE_REG_TYPE = "_presence._tcp"

    /** TXT record key carrying the hex-encoded 16-byte service ID passed to `start(serviceId)`. */
    const val TXT_KEY_SERVICE_ID = "sid"

    /** Fixed TCP port the group owner listens on once a P2P group forms. */
    const val SOCKET_PORT = 8988

    /** Hardcoded cap on simultaneous connected peers -- reasoned, not benchmarked. A Wi-Fi Direct
     *  group owner can host more clients than BLE's [india.projectmesh.app.ble.MAX_CONNECTIONS],
     *  but this transport is scoped to bulk-transfer bursts between a few nearby devices, not a
     *  dense-crowd primary link (that's BLE's job per `docs/TRANSPORT.md` §1). */
    const val MAX_CONNECTIONS = 6

    /** Guards against a corrupt/hostile length prefix causing an unbounded allocation. */
    const val MAX_FRAME_SIZE = 8 * 1024 * 1024
}
