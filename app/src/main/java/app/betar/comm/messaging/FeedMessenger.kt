package app.betar.comm.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import app.betar.comm.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "FeedMessenger"
private const val POST_MAX_LENGTH = 280
private const val FEED_TTL_HOPS: UByte = 8u
private const val FEED_EXPIRES_SECONDS = 72L * 3600L

/** 0=block, 1=city, 2=region, 3=country, matching the room chips in the locked Civic Calm
 *  design and [AppPost.kt]'s `room_precision` byte. */
object RoomPrecision {
    const val BLOCK = 0
    const val CITY = 1
    const val REGION = 2
    const val COUNTRY = 3
}

data class FeedPost(val text: String, val idHex: String, val roomPrecision: Int, val postedAtSeconds: Long)

/**
 * Feed posts: geohash-scoped public rooms (`FEATURES.md`), the direct successor to the cut
 * disaster-bulletin board. Ordinary `Addressing::Broadcast` envelopes, magic-byte-prefixed with
 * [MAGIC_POST] via `AppPost.kt`, same framing pattern `BroadcastMessenger` already uses for
 * plain chat.
 *
 * **Not done, stated plainly:** no real geohash derivation from device location, `roomPrecision`
 * is whatever the UI passes in, same gap `AppPost.kt` already states. No signature (`FfiIdentity`
 * has no `sign`/`verify` exported over FFI yet, same gap `BroadcastMessenger` has). No relay
 * bridge exists in `core/` yet either, so every post here is mesh-local only regardless of what
 * a "relayed" label might imply, there is nothing on the other side of that bridge to test
 * against yet, not pretended otherwise.
 */
class FeedMessenger(private val coordinator: MeshCoordinator) {
    val posts = mutableStateListOf<FeedPost>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    /** Returns true if posted, false if `text` is empty or over [POST_MAX_LENGTH] characters. */
    fun post(text: String, roomPrecision: Int): Boolean {
        if (text.isEmpty() || text.length > POST_MAX_LENGTH) return false
        val now = nowSecondsShared()
        val payload = encodeAppPostPayload(MAGIC_POST, roomPrecision, location = null, text = text)
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = 2u, // Normal
            ttlHops = FEED_TTL_HOPS,
            expiresAt = (now + FEED_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        return try {
            coordinator.node().composeLocal(bytes, now.toULong())
            true
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for feed post", e)
            false
        }
    }

    /** Call periodically, same polling pattern as [BroadcastMessenger.pollForNewPosts]. */
    fun pollForNewPosts() {
        val node = coordinator.node()
        for (idHex in node.allIdsHex()) {
            if (!seenEnvelopeIds.add(idHex)) continue
            val bytes = node.getEnvelopeHex(idHex) ?: continue
            val parsed = try {
                envelopeUnpack(bytes)
            } catch (e: Exception) {
                continue
            }
            if (parsed.addressingTag != 0.toUByte()) continue // not Broadcast
            val decoded = decodeAppPostPayload(parsed.sealed) ?: continue
            if (decoded.magic != MAGIC_POST) continue
            posts.add(0, FeedPost(decoded.text, parsed.idHex, decoded.roomPrecision, nowSecondsShared()))
        }
    }
}
