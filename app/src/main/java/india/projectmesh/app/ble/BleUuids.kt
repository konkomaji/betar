package india.projectmesh.app.ble

import java.util.UUID

/**
 * Project Mesh's BLE GATT profile. Fixed once real devices exist -- never regenerate these.
 * See `docs/TRANSPORT.md` §2 for the design; `docs/IMPLEMENTATION-STATUS.md` for the deliberate
 * two-characteristic simplification vs. the doc's single implied characteristic (GATT's data
 * model is inherently asymmetric -- only a server notifies, only a client writes).
 */
object BleUuids {
    val SERVICE: UUID = UUID.fromString("3fb3b188-b1b8-4a14-b955-359405576295")

    /** Central -> peripheral: the connecting device writes frame fragments here. */
    val INBOUND_CHAR: UUID = UUID.fromString("ee265c9e-ec46-4d29-bf0c-ee2f0c88cc87")

    /** Peripheral -> central: the advertiser notifies frame fragments here. */
    val OUTBOUND_CHAR: UUID = UUID.fromString("79570745-7caa-4139-aefc-dae8d4bf2d89")

    /** Standard Client Characteristic Configuration Descriptor, needed to enable notifications. */
    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** The raw 16 bytes handed to `FfiMeshTransport.start`/`FfiMeshNode` -- see `ServiceId`. */
    fun serviceIdBytes(): ByteArray {
        val bb = java.nio.ByteBuffer.wrap(ByteArray(16))
        bb.putLong(SERVICE.mostSignificantBits)
        bb.putLong(SERVICE.leastSignificantBits)
        return bb.array()
    }
}
