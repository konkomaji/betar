package app.betar.comm.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.betar.comm.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "RatingsMessenger"
private const val RATING_TTL_HOPS: UByte = 8u
private const val RATING_EXPIRES_SECONDS = 72L * 3600L
private const val FIELD_SEP = '\u001F' // same unit separator NotesMessenger.kt uses
private const val HELPFUL_TOKEN = "helpful"
private const val NOT_HELPFUL_TOKEN = "not"

/** A Community Note is treated as confirmed, and its post gets a warning banner
 *  (`FeedTab.kt`), once its [communityScore] reaches this many net "helpful" votes. 3 is a
 *  deliberately low bar: this app has no way to guarantee one rating is one person (see this
 *  file's own class doc), so a high bar would mean a single device submitting many ratings is
 *  needed to force a false flag just as easily as a real one, a low bar at least keeps the
 *  cost of *reaching visibility* small, which matters more than the cost of a false positive
 *  when the action taken is "show a warning with a tap-to-reveal," not "delete." */
const val NOTE_CONFIRMED_THRESHOLD = 3

data class Rating(
    val noteId: String,
    val helpful: Boolean,
    /** Broadcast envelopes carry no sender identification at all (unlike `DirectMessaging.kt`,
     *  which prefixes a sender fingerprint into its own payload) -- always null until that
     *  changes. Kept as a field rather than left out so the type doesn't need to change the day
     *  signing/sender-attribution exists for Broadcast traffic. */
    val raterFingerprint: String?,
    val idHex: String,
)

/**
 * Helpful / not-helpful ratings on a [CommunityNote], the missing half of
 * `NotesMessenger.kt`'s own stated gap: "no helpful/not-helpful rating and no public scoring
 * algorithm are implemented this pass." Same `Addressing::Broadcast` + `AppPost.kt` magic-byte
 * pattern every messenger in this codebase uses, this time with [MAGIC_RATING].
 *
 * **The scoring here is deliberately simple**, not the real bridging-algorithm matrix
 * factorization this project's own docs (`docs/FEATURES.md`, referencing X's open-sourced
 * Community Notes algorithm) name as the eventual aspiration. [communityScore] is just
 * helpful-count minus not-helpful-count. A real bridging score needs rater-diversity weighting
 * (so agreement only counts if it comes from people who don't usually agree with each other,
 * the actual innovation of the real algorithm) which needs a rater-identity and rating-history
 * concept this app does not have yet. Building a simplified version of a real statistical
 * algorithm and calling it done would be worse than building something honestly simple and
 * saying so, which is what this is.
 *
 * **Not done, stated plainly:** no signature on rating events (same gap every Broadcast-based
 * messenger here already has), which means nothing stops one device from submitting many
 * ratings under no identity at all to inflate or deflate a score. This is a real, currently
 * unmitigated weakness in the scoring's integrity, not a hypothetical one, see
 * [NOTE_CONFIRMED_THRESHOLD]'s own doc for how the UI consequence is scoped down to account
 * for it.
 */
class RatingsMessenger(private val coordinator: MeshCoordinator) {
    val ratings: SnapshotStateList<Rating> = mutableStateListOf()
    private val seenEnvelopeIds = mutableSetOf<String>()

    /** Returns true if submitted. A device can rate the same note more than once; this class
     *  does not deduplicate per-rater (it has no rater identity to deduplicate by, see
     *  [Rating.raterFingerprint]'s doc), so [communityScore] counts every rating event seen,
     *  not every distinct rater. */
    fun rate(noteId: String, helpful: Boolean): Boolean {
        val now = nowSecondsShared()
        val token = if (helpful) HELPFUL_TOKEN else NOT_HELPFUL_TOKEN
        val encodedText = listOf(noteId, token).joinToString(FIELD_SEP.toString())
        val payload = encodeAppPostPayload(MAGIC_RATING, roomPrecision = 0, location = null, text = encodedText)
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = 2u, // Normal
            ttlHops = RATING_TTL_HOPS,
            expiresAt = (now + RATING_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        return try {
            coordinator.node().composeLocal(bytes, now.toULong())
            true
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for rating", e)
            false
        }
    }

    /** Call periodically, same polling pattern as [BroadcastMessenger.pollForNewPosts]. */
    fun pollForNewRatings() {
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
            if (decoded.magic != MAGIC_RATING) continue
            val fields = decoded.text.split(FIELD_SEP)
            if (fields.size != 2) continue
            val helpful = fields[1] == HELPFUL_TOKEN
            ratings.add(Rating(noteId = fields[0], helpful = helpful, raterFingerprint = null, idHex = parsed.idHex))
        }
    }
}

/** Net helpful votes for [noteId]: helpful-count minus not-helpful-count, across every
 *  [Rating] this device has synced so far (mesh or, once bridged, relay). Deterministic and
 *  recomputable by any client from the same rating events, which is the actual requirement
 *  this project's design calls for, not "ranked the same way everywhere" in some stronger
 *  statistical sense. See this file's own class doc for why it stops there. */
fun communityScore(noteId: String, ratings: List<Rating>): Int {
    var score = 0
    for (rating in ratings) {
        if (rating.noteId != noteId) continue
        score += if (rating.helpful) 1 else -1
    }
    return score
}
