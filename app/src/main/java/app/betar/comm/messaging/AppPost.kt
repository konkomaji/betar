package app.betar.comm.messaging

import app.betar.comm.nostr.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.verifyContentSignature

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
 * Layout, updated 2026-11-01 to carry an optional mesh-local image thumbnail:
 * `[magic:1][room_precision:1][has_image:1][has_location:1][lat:4 LE][lon:4 LE]?[image_len:4 LE][image_bytes]?[text: rest utf8]`.
 * `room_precision` is the geohash precision the post/note belongs to (0=block, 1=city,
 * 2=region, 3=country, matching the room chips in the locked Civic Calm design). `lat`/`lon` are
 * fixed-point (`degrees * 1_000_000`) and only present when `has_location=1` -- this is the
 * geohash room's own coarse location, not a one-shot chat location share, which stays inside
 * `DirectMessaging.kt`'s encrypted payloads, never broadcast in the clear. `image_bytes`, only
 * present when `has_image=1`, is a small re-encoded JPEG thumbnail (see `FeedTab.kt`'s composer),
 * never the original full-resolution picked image -- full resolution is meant to travel over the
 * relay bridge via a content-addressed blob store (Blossom protocol or equivalent) once that
 * exists, not attempted this pass, so a mesh-only user stays at thumbnail quality, stated
 * plainly rather than silently. The byte that used to be a placeholder `reserved:1` field is
 * `has_image` now; it was never set to anything but 0 before, so this is not a breaking change
 * against any payload actually produced by this app.
 *
 * **Not done, stated plainly:** no geohash derivation from device location yet (needs a location
 * permission + `FusedLocationProvider` flow, same gap the old file had). Community Notes' own
 * requirement (2 source links + a 150 character explanation) is enforced client-side in the
 * compose UI, not in this wire format, since the routing layer never inspects sealed content by
 * design (`docs/ROUTING-PROTOCOL.md` §2).
 *
 * **Signing, added 2026-11-01** ([signAppPostPayload]/[verifyAppPostSignature], now that
 * `FfiIdentity.sign`/`verifyContentSignature` are exported over FFI): a 32-byte verifying key
 * plus a 64-byte Ed25519 signature are appended after everything [encodeAppPostPayload] already
 * produces, covering those exact bytes, not re-framed into the fixed header above, so this is a
 * pure append that doesn't disturb [decodeAppPostPayload]'s existing layout. The signature does
 * **not** establish who a poster "really is" (there is no name or follow list here, Feed and
 * Notes stay pseudonymous by design); what it establishes is that every signed item from one
 * device carries the same verifying key until that device wipes or reinstalls, so
 * `RatingsMessenger.kt`'s scoring can dedupe by signer instead of by raw envelope count, closing
 * the gap this file used to state plainly: "nothing stops one device submitting many ratings
 * under no identity to move a score." A device can still mint a fresh identity and start over,
 * so this raises the cost of ballot-stuffing, it does not eliminate it; stated plainly rather
 * than oversold.
 */
const val MAGIC_POST: Byte = 0xF1.toByte()
const val MAGIC_NOTE: Byte = 0xF2.toByte()
/** A helpful/not-helpful rating on a Community Note, broadcast the same way the note itself
 *  is, so the note's bridging score is a public, shared value anyone can recompute from the
 *  same synced events, not something only the submitter's own device knows. See
 *  `NotesMessenger.kt`'s class doc and `RatingsMessenger.kt` for the actual scoring. */
const val MAGIC_RATING: Byte = 0xF3.toByte()

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
private const val IMAGE_LEN_SIZE = 4
private const val FIXED_POINT_SCALE = 1_000_000.0

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Where a Feed post or Community Note actually came from: carried over the local mesh, or
 *  received through the Nostr relay bridge (`nostr/RelayBridge.kt`). Shown in the UI so the
 *  "the moment anyone reaches the internet, reach isn't capped by radio range" claim is
 *  something a user can actually verify per-item, not just a line in the docs. */
enum class ContentOrigin { Mesh, Relay }

data class DecodedAppPost(
    val magic: Byte,
    val roomPrecision: Int,
    val location: GeoPoint?,
    val imageBytes: ByteArray?,
    val text: String,
    /** Hex-encoded 32-byte Ed25519 verifying key of whoever signed this, or null if the payload
     *  predates signing or was too short to carry a signature at all (an old build's post, or a
     *  malformed one). Not an identity in any stronger sense than "the same key signed every
     *  item that shares this value" -- see [signAppPostPayload]'s own doc. */
    val signerKeyHex: String? = null,
    /** True only if [signerKeyHex] is non-null AND the attached signature actually verifies
     *  against the exact bytes this struct's other fields were decoded from. Callers that care
     *  about signature validity (`RatingsMessenger.kt`'s dedup) must check this explicitly;
     *  decoding never fails just because a signature is missing or invalid, since this app
     *  still accepts unsigned legacy-shaped payloads. */
    val signatureValid: Boolean = false,
)

private const val SIGNER_KEY_SIZE = 32
private const val SIGNATURE_SIZE = 64
private const val SIGNED_SUFFIX_SIZE = SIGNER_KEY_SIZE + SIGNATURE_SIZE

/** Append a signature over `unsignedPayload` (the exact output of [encodeAppPostPayload]), so a
 *  receiver can verify which device's identity key produced it. Call this on the result of
 *  [encodeAppPostPayload], not on arbitrary bytes, and hand the result (not the unsigned bytes)
 *  to `envelopePack`. */
fun signAppPostPayload(identity: FfiIdentity, unsignedPayload: ByteArray): ByteArray =
    unsignedPayload + identity.verifyingKeyBytes() + identity.sign(unsignedPayload)

/** Split a signed payload (produced by [signAppPostPayload]) back into its unsigned bytes plus
 *  verification results, *before* calling [decodeAppPostPayload] on the unsigned remainder.
 *  Every payload this app produces carries the suffix; one too short to hold it is treated as
 *  malformed (unsigned, unverified), not as an older valid format -- there is no real prior
 *  release that ever broadcast this wire shape without it, so there is nothing to stay
 *  compatible with. */
fun stripAppPostSignature(bytes: ByteArray): Triple<ByteArray, String?, Boolean> {
    if (bytes.size <= SIGNED_SUFFIX_SIZE) return Triple(bytes, null, false)
    val splitAt = bytes.size - SIGNED_SUFFIX_SIZE
    val unsigned = bytes.copyOfRange(0, splitAt)
    val signerKey = bytes.copyOfRange(splitAt, splitAt + SIGNER_KEY_SIZE)
    val signature = bytes.copyOfRange(splitAt + SIGNER_KEY_SIZE, bytes.size)
    val valid = verifyContentSignature(signerKey, unsigned, signature)
    return Triple(unsigned, signerKey.toHexString(), valid)
}

/** True for any reserved (non-plain-chat) magic-byte-prefixed Broadcast payload -- Feed posts,
 *  Community Notes, or the prekey-bundle announcement. `BroadcastMessaging.kt` excludes
 *  everything this returns true for from the plain chat feed. */
fun isReservedBroadcastMagic(bytes: ByteArray): Boolean =
    bytes.isNotEmpty() &&
        (bytes[0] == MAGIC_POST || bytes[0] == MAGIC_NOTE || bytes[0] == MAGIC_RATING || bytes[0] == MAGIC_PREKEY_BUNDLE)

fun encodeAppPostPayload(magic: Byte, roomPrecision: Int, location: GeoPoint?, imageBytes: ByteArray? = null, text: String): ByteArray {
    val textBytes = text.toByteArray(Charsets.UTF_8)
    val hasLocation = location != null
    val hasImage = imageBytes != null
    val size = HEADER_SIZE + (if (hasLocation) LOCATION_SIZE else 0) +
        (if (hasImage) IMAGE_LEN_SIZE + imageBytes!!.size else 0) + textBytes.size
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buf.put(magic)
    buf.put(roomPrecision.toByte())
    buf.put(if (hasImage) 1 else 0)
    buf.put(if (hasLocation) 1 else 0)
    if (location != null) {
        buf.putInt((location.latitude * FIXED_POINT_SCALE).toInt())
        buf.putInt((location.longitude * FIXED_POINT_SCALE).toInt())
    }
    if (imageBytes != null) {
        buf.putInt(imageBytes.size)
        buf.put(imageBytes)
    }
    buf.put(textBytes)
    return buf.array()
}

fun decodeAppPostPayload(bytes: ByteArray): DecodedAppPost? {
    if (bytes.size < HEADER_SIZE) return null
    val magic = bytes[0]
    if (magic != MAGIC_POST && magic != MAGIC_NOTE && magic != MAGIC_RATING) return null
    val roomPrecision = bytes[1].toInt() and 0xFF
    val hasImage = bytes[2].toInt() == 1
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
    var imageBytes: ByteArray? = null
    if (hasImage) {
        if (bytes.size < offset + IMAGE_LEN_SIZE) return null
        val imageLen = ByteBuffer.wrap(bytes, offset, IMAGE_LEN_SIZE).order(ByteOrder.LITTLE_ENDIAN).int
        offset += IMAGE_LEN_SIZE
        if (imageLen < 0 || bytes.size < offset + imageLen) return null
        imageBytes = bytes.copyOfRange(offset, offset + imageLen)
        offset += imageLen
    }
    val text = runCatching { String(bytes, offset, bytes.size - offset, Charsets.UTF_8) }.getOrDefault("")
    return DecodedAppPost(magic, roomPrecision, location, imageBytes, text)
}

fun nowSecondsShared(): Long = System.currentTimeMillis() / 1000L
