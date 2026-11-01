package app.betar.comm.messaging

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared app-layer wire framing for reserved Broadcast content: Feed posts and Community Notes
 * (`FEATURES.md`). Both are ordinary `Addressing::Broadcast` envelopes
 * (`ROUTING-PROTOCOL.md` §5: "anyone nearby / signed, not encrypted"), differentiated from each
 * other and from plain chat broadcast (`BroadcastMessaging.kt`) by a one-byte magic prefix Rust
 * core never needs to know about -- `Envelope.sealed` stays fully opaque, per
 * `docs/ARCHITECTURE.md` §1.
 *
 * Renamed 2026-11-01 from `CivicPost.kt`: that file framed SOS, disaster bulletins, the resource
 * board, and map pins, all cut in the pivot. This file keeps the exact same wire pattern for the
 * two features that replaced them, Feed posts and Community Notes, plus the one thing from the
 * old file that was never civic-specific to begin with: the prekey-bundle magic-byte exclusion
 * (see [MAGIC_PREKEY_BUNDLE]'s own doc comment for why that one can't be forgotten).
 *
 * Layout: `[magic:1][room_precision:1][reserved:1][has_location:1][lat:4 LE][lon:4 LE]?[text: rest utf8]`.
 * `room_precision` is the geohash precision the post/note belongs to (0=block, 1=city,
 * 2=region, 3=country, matching the room chips in the locked Civic Calm design). `lat`/`lon` are
 * fixed-point (`degrees * 1_000_000`) and only present when `has_location=1` -- this is the
 * geohash room's own coarse location, not a one-shot chat location share, which stays inside
 * `DirectMessaging.kt`'s encrypted payloads, never broadcast in the clear.
 *
 * **Not done, stated plainly:** no geohash derivation from device location yet (needs a location
 * permission + `FusedLocationProvider` flow, same gap the old file had). Also **unsigned**, same
 * gap already flagged for plain broadcast chat: `FfiIdentity` has no `sign`/`verify` exported
 * over FFI yet. Community Notes' own requirement (2 source links + a 150 character explanation)
 * is enforced client-side in the compose UI, not in this wire format, since the routing layer
 * never inspects sealed content by design (`docs/ROUTING-PROTOCOL.md` §2).
 */
const val MAGIC_POST: Byte = 0xF1.toByte()
const val MAGIC_NOTE: Byte = 0xF2.toByte()

/** Not an app post -- `DirectMessaging.kt`'s hybrid prekey-bundle announcement, which reuses this
 *  same magic-byte-prefixed-Broadcast pattern. Kept in this shared registry (rather than defined
 *  privately where it's used) specifically so [isReservedBroadcastMagic] can't forget about it --
 *  which is exactly what happened before this constant moved here: the prekey bundle (real
 *  ML-KEM-1024/X25519 key material, no user-readable text) was leaking into the plain chat feed
 *  as garbled binary noise on every launch, since the exclusion check only knew about the civic
 *  posts that existed at the time. Found via an actual on-device QA pass, not a hypothetical. */
const val MAGIC_PREKEY_BUNDLE: Byte = 0xF4.toByte()

private const val HEADER_SIZE = 4
private const val LOCATION_SIZE = 8
private const val FIXED_POINT_SCALE = 1_000_000.0

data class GeoPoint(val latitude: Double, val longitude: Double)

data class DecodedAppPost(val magic: Byte, val roomPrecision: Int, val location: GeoPoint?, val text: String)

/** True for any reserved (non-plain-chat) magic-byte-prefixed Broadcast payload -- Feed posts,
 *  Community Notes, or the prekey-bundle announcement. `BroadcastMessaging.kt` excludes
 *  everything this returns true for from the plain chat feed. */
fun isReservedBroadcastMagic(bytes: ByteArray): Boolean =
    bytes.isNotEmpty() &&
        (bytes[0] == MAGIC_POST || bytes[0] == MAGIC_NOTE || bytes[0] == MAGIC_PREKEY_BUNDLE)

fun encodeAppPostPayload(magic: Byte, roomPrecision: Int, location: GeoPoint?, text: String): ByteArray {
    val textBytes = text.toByteArray(Charsets.UTF_8)
    val hasLocation = location != null
    val size = HEADER_SIZE + (if (hasLocation) LOCATION_SIZE else 0) + textBytes.size
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.put(magic)
    buf.put(roomPrecision.toByte())
    buf.put(0) // reserved
    buf.put(if (hasLocation) 1 else 0)
    if (location != null) {
        buf.putInt((location.latitude * FIXED_POINT_SCALE).toInt())
        buf.putInt((location.longitude * FIXED_POINT_SCALE).toInt())
    }
    buf.put(textBytes)
    return buf.array()
}

fun decodeAppPostPayload(bytes: ByteArray): DecodedAppPost? {
    if (bytes.size < HEADER_SIZE) return null
    val magic = bytes[0]
    if (magic != MAGIC_POST && magic != MAGIC_NOTE) return null
    val roomPrecision = bytes[1].toInt() and 0xFF
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
    return DecodedAppPost(magic, roomPrecision, location, text)
}

fun nowSecondsShared(): Long = System.currentTimeMillis() / 1000L
