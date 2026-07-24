package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "BroadcastMessaging"
private const val BROADCAST_TTL_HOPS: UByte = 8u
private const val BROADCAST_EXPIRES_SECONDS = 72L * 3600L

data class BroadcastPost(val text: String, val idHex: String)

/**
 * Broadcast ("everyone nearby") messaging -- `FEATURES.md` §5, `ROUTING-PROTOCOL.md` §5's
 * Broadcast row. Simplest of the four addressing modes: no key material at all, every nearby
 * device that relays the mesh sees it.
 *
 * **Not done, stated plainly:** `ROUTING-PROTOCOL.md` §5 specifies Broadcast as "signed, not
 * encrypted" -- posts here are plain UTF-8 bytes with **no signature**, since `FfiIdentity` has
 * no `sign`/`verify` exported over FFI yet (only `fingerprintHex`/`safetyString`). Anyone can
 * forge a broadcast claiming to be from anyone; there's no authenticity check at this layer.
 * Real signing is a separate, contained follow-up once `FfiIdentity::sign` is exported -- not
 * bundled into this pass silently.
 *
 * Excludes anything matching [isReservedBroadcastMagic] (SOS/bulletin/resource-board posts, and
 * `DirectMessaging.kt`'s prekey-bundle announcement -- see `CivicPost.kt`) so those don't leak
 * into this plain-chat feed as garbled text.
 */
class BroadcastMessenger(private val coordinator: MeshCoordinator) {
    val posts = mutableStateListOf<BroadcastPost>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun send(text: String) {
        val now = nowSeconds()
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = 2u, // Normal
            ttlHops = BROADCAST_TTL_HOPS,
            expiresAt = (now + BROADCAST_EXPIRES_SECONDS).toULong(),
            sealed = text.toByteArray(Charsets.UTF_8),
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for broadcast", e)
        }
    }

    /** Call periodically -- see [DirectMessenger]'s class doc for why polling, not a callback. */
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
            if (isReservedBroadcastMagic(parsed.sealed)) continue // civic post or prekey-bundle announcement, not plain chat
            val text = runCatching { String(parsed.sealed, Charsets.UTF_8) }.getOrNull() ?: continue
            posts.add(0, BroadcastPost(text, parsed.idHex)) // newest first
        }
    }
}

private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L
