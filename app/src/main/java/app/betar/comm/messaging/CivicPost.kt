package app.betar.comm.messaging

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared app-layer wire framing for the three civic-broadcast features -- SOS, disaster
 * bulletins, and the community resource board (`FEATURES.md` §1/§2/§4). All three are ordinary
 * `Addressing::Broadcast` envelopes (`ROUTING-PROTOCOL.md` §5: "anyone nearby / signed, not
 * encrypted"), differentiated from each other and from plain chat broadcast (`BroadcastMessaging.kt`)
 * by a one-byte magic prefix Rust core never needs to know about -- `Envelope.sealed` stays fully
 * opaque, per `docs/ARCHITECTURE.md` §1.
 *
 * Layout: `[magic:1][category:1][extra:1][has_location:1][lat:4 LE][lon:4 LE]?[text: rest utf8]`.
 * `extra`'s meaning is feature-specific (SOS: 0=new alert/1=acknowledgement; resource board:
 * 0=have/1=need; unused for bulletins). `lat`/`lon` are fixed-point (`degrees * 1_000_000`) and
 * only present when `has_location=1`.
 *
 * **Not done, stated plainly:** no device-location integration this pass (`FEATURES.md`'s
 * "optional coarse location, user-confirmed" needs a location permission + `FusedLocationProvider`
 * flow) -- the format supports a location field for when that lands, but nothing in this app
 * populates one yet. Also **unsigned**, same gap already flagged for plain broadcast chat:
 * `FEATURES.md` §2/§4 call for poster-signed entries; `FfiIdentity` has no `sign`/`verify`
 * exported over FFI yet.
 */
const val MAGIC_SOS: Byte = 0xF1.toByte()
const val MAGIC_BULLETIN: Byte = 0xF2.toByte()
const val MAGIC_RESOURCE: Byte = 0xF3.toByte()
/** Map pin (`FEATURES.md` §3's "users drop and share pins over the mesh"). The only civic-post
 *  type that always sets [DecodedCivicPost.location] -- a pin with no coordinate is meaningless.
 *  `category` is [app.betar.comm.ui.screens.map.MapPinCategory.code]. */
const val MAGIC_MAP_PIN: Byte = 0xF5.toByte()

/** Not a civic post -- `DirectMessaging.kt`'s hybrid prekey-bundle announcement, which reuses this
 *  same magic-byte-prefixed-Broadcast pattern. Kept in this shared registry (rather than defined
 *  privately where it's used) specifically so [isReservedBroadcastMagic] can't forget about it --
 *  which is exactly what happened before this constant moved here: the prekey bundle (real
 *  ML-KEM-1024/X25519 key material, no user-readable text) was leaking into the plain chat feed as
 *  garbled binary noise on every launch, since the exclusion check only knew about SOS/bulletin/
 *  resource. Found via an actual on-device QA pass, not a hypothetical. */
const val MAGIC_PREKEY_BUNDLE: Byte = 0xF4.toByte()

private const val HEADER_SIZE = 4
private const val LOCATION_SIZE = 8
private const val FIXED_POINT_SCALE = 1_000_000.0

data class GeoPoint(val latitude: Double, val longitude: Double)

data class DecodedCivicPost(val magic: Byte, val category: Int, val extra: Int, val location: GeoPoint?, val text: String)

/** True for any reserved (non-plain-chat) magic-byte-prefixed Broadcast payload -- civic posts
 *  (SOS/bulletin/resource) or the prekey-bundle announcement. `BroadcastMessaging.kt` excludes
 *  everything this returns true for from the plain chat feed. */
fun isReservedBroadcastMagic(bytes: ByteArray): Boolean =
    bytes.isNotEmpty() &&
        (bytes[0] == MAGIC_SOS || bytes[0] == MAGIC_BULLETIN || bytes[0] == MAGIC_RESOURCE ||
            bytes[0] == MAGIC_MAP_PIN || bytes[0] == MAGIC_PREKEY_BUNDLE)

fun encodeCivicPayload(magic: Byte, category: Int, extra: Int, location: GeoPoint?, text: String): ByteArray {
    val textBytes = text.toByteArray(Charsets.UTF_8)
    val hasLocation = location != null
    val size = HEADER_SIZE + (if (hasLocation) LOCATION_SIZE else 0) + textBytes.size
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.put(magic)
    buf.put(category.toByte())
    buf.put(extra.toByte())
    buf.put(if (hasLocation) 1 else 0)
    if (location != null) {
        buf.putInt((location.latitude * FIXED_POINT_SCALE).toInt())
        buf.putInt((location.longitude * FIXED_POINT_SCALE).toInt())
    }
    buf.put(textBytes)
    return buf.array()
}

fun decodeCivicPayload(bytes: ByteArray): DecodedCivicPost? {
    if (bytes.size < HEADER_SIZE) return null
    val magic = bytes[0]
    if (magic != MAGIC_SOS && magic != MAGIC_BULLETIN && magic != MAGIC_RESOURCE && magic != MAGIC_MAP_PIN) return null
    val category = bytes[1].toInt() and 0xFF
    val extra = bytes[2].toInt() and 0xFF
    val hasLocation = bytes[3].toInt() == 1
    var offset = HEADER_SIZE
    var location: GeoPoint? = null
    if (hasLocation) {
        if (bytes.size < offset + LOCATION_SIZE) return null
        val buf = ByteBuffer.wrap(bytes, offset, LOCATION_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val lat = buf.int / FIXED_POINT_SCALE
        val lon = buf.int / FIXED_POINT_SCALE
        location = GeoPoint(lat, lon)
        offset += LOCATION_SIZE
    }
    val text = runCatching { String(bytes, offset, bytes.size - offset, Charsets.UTF_8) }.getOrDefault("")
    return DecodedCivicPost(magic, category, extra, location, text)
}

fun nowSecondsShared(): Long = System.currentTimeMillis() / 1000L
