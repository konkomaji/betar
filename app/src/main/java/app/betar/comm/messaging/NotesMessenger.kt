package app.betar.comm.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import app.betar.comm.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "NotesMessenger"
private const val EXPLANATION_MAX_LENGTH = 150
private const val NOTE_TTL_HOPS: UByte = 8u
private const val NOTE_EXPIRES_SECONDS = 72L * 3600L
private const val FIELD_SEP = '\u001F' // unit separator, won't collide with real note text

data class CommunityNote(
    val postId: String,
    val link1: String,
    val link2: String,
    val explanation: String,
    val idHex: String,
)

/**
 * Community Notes: a fact-check note attached to a Feed post, requiring 2 source links plus a
 * short explanation (`FEATURES.md`). Ordinary `Addressing::Broadcast` envelopes, magic-byte
 * prefixed with [MAGIC_NOTE] via `AppPost.kt`.
 *
 * Sub-format inside `AppPost.kt`'s `text` field, chosen here: `postId<0x1F>link1<0x1F>link2<0x1F>explanation`,
 * unit-separator-delimited since it can't appear in ordinary user-typed URLs or prose. The
 * routing layer never parses this, it is opaque sealed content same as everything else
 * (`docs/ROUTING-PROTOCOL.md` §2).
 *
 * **Not done, stated plainly, same honest-gaps culture as the rest of this codebase:** no
 * helpful/not-helpful rating and no public scoring algorithm are implemented this pass. The
 * design calls for a deterministic, publicly computable bridging score (`docs/FEATURES.md`) so
 * no single scorer is authoritative, that computation does not exist anywhere yet, Rust or
 * Kotlin, not stubbed here with a fake number. This class only stores and surfaces notes as
 * submitted, in submission order, nothing ranks them. No signature either, same gap every other
 * Broadcast-based messenger in this codebase already has.
 */
class NotesMessenger(private val coordinator: MeshCoordinator) {
    val notes = mutableStateListOf<CommunityNote>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun notesFor(postId: String): List<CommunityNote> = notes.filter { it.postId == postId }

    /** Returns true if submitted, false if either link is blank or `explanation` is empty or
     *  over [EXPLANATION_MAX_LENGTH] characters. */
    fun addNote(postId: String, link1: String, link2: String, explanation: String): Boolean {
        if (link1.isBlank() || link2.isBlank()) return false
        if (explanation.isEmpty() || explanation.length > EXPLANATION_MAX_LENGTH) return false
        val now = nowSecondsShared()
        val encodedText = listOf(postId, link1, link2, explanation).joinToString(FIELD_SEP.toString())
        val payload = encodeAppPostPayload(MAGIC_NOTE, roomPrecision = 0, location = null, text = encodedText)
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = 1u, // Elevated: a correction should resist eviction and propagate
            // ahead of ordinary traffic, same tier the cut disaster bulletin used to get.
            ttlHops = NOTE_TTL_HOPS,
            expiresAt = (now + NOTE_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        return try {
            coordinator.node().composeLocal(bytes, now.toULong())
            true
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for community note", e)
            false
        }
    }

    /** Call periodically, same polling pattern as [BroadcastMessenger.pollForNewPosts]. */
    fun pollForNewNotes() {
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
            if (decoded.magic != MAGIC_NOTE) continue
            val fields = decoded.text.split(FIELD_SEP)
            if (fields.size != 4) continue
            notes.add(CommunityNote(fields[0], fields[1], fields[2], fields[3], parsed.idHex))
        }
    }
}
