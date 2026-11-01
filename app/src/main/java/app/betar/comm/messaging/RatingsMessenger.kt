package app.betar.comm.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.betar.comm.MeshCoordinator
import uniffi.mesh_core.FfiIdentity
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
    /** Hex-encoded 32-byte Ed25519 verifying key of whoever submitted this rating, from
     *  `AppPost.kt`'s signing suffix (added 2026-11-01), or null if the signature was missing
     *  or malformed. Null here used to mean "Broadcast envelopes carry no sender identification
     *  at all"; signing has since closed that specific gap, see [communityScore]'s own doc for
     *  what it still doesn't close. */
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
 * **Signed as of 2026-11-01** (`AppPost.kt`'s `signAppPostPayload`/`stripAppPostSignature`):
 * every rating this device submits is signed with [identity]'s long-term Ed25519 key, and
 * [communityScore] now counts at most one rating per unique verified signer per note, not one
 * per raw envelope. **What this does and doesn't fix, stated plainly:** signing stops one
 * device from submitting unlimited *distinct-looking* ratings under no identity at all; it does
 * not stop a device from wiping/reinstalling (a fresh [identity], a fresh "person" by this
 * score's lights) and rating again, or from running several installs. Raising that cost further
 * needs some notion of device/account scarcity this app does not have and, by design (no phone
 * number, no account, `docs/THREAT-MODEL.md`'s identity-system section), may never have; see
 * [NOTE_CONFIRMED_THRESHOLD]'s own doc for why the UI consequence stays scoped down regardless.
 */
class RatingsMessenger(private val identity: FfiIdentity, private val coordinator: MeshCoordinator) {
    val ratings: SnapshotStateList<Rating> = mutableStateListOf()
    private val seenEnvelopeIds = mutableSetOf<String>()

    /** Returns true if submitted. A device can still send more than one rating event for the
     *  same note (nothing here stops the broadcast itself), but [communityScore] now counts at
     *  most one per unique verified signer, so re-rating from the same identity no longer moves
     *  the score further. */
    fun rate(noteId: String, helpful: Boolean): Boolean {
        val now = nowSecondsShared()
        val token = if (helpful) HELPFUL_TOKEN else NOT_HELPFUL_TOKEN
        val encodedText = listOf(noteId, token).joinToString(FIELD_SEP.toString())
        val unsignedPayload = encodeAppPostPayload(MAGIC_RATING, roomPrecision = 0, location = null, text = encodedText)
        val payload = signAppPostPayload(identity, unsignedPayload)
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
            val (unsigned, signerKeyHex, signatureValid) = stripAppPostSignature(parsed.sealed)
            val decoded = decodeAppPostPayload(unsigned) ?: continue
            if (decoded.magic != MAGIC_RATING) continue
            val fields = decoded.text.split(FIELD_SEP)
            if (fields.size != 2) continue
            val helpful = fields[1] == HELPFUL_TOKEN
            ratings.add(
                Rating(
                    noteId = fields[0],
                    helpful = helpful,
                    raterFingerprint = if (signatureValid) signerKeyHex else null,
                    idHex = parsed.idHex,
                ),
            )
        }
    }
}

/** Net helpful votes for [noteId]: helpful-count minus not-helpful-count, across every
 *  [Rating] this device has synced so far (mesh or, once bridged, relay). Deterministic and
 *  recomputable by any client from the same rating events, which is the actual requirement
 *  this project's design calls for, not "ranked the same way everywhere" in some stronger
 *  statistical sense. See this file's own class doc for why it stops there.
 *
 *  **Deduplicated by signer as of 2026-11-01:** a rating with no verified
 *  [Rating.raterFingerprint] (missing or invalid signature) is excluded entirely, there is no
 *  identity to count it against; among a given signer's ratings on this note, only the
 *  *last one seen* counts, so re-rating after a change of mind replaces the old vote instead of
 *  adding a second one, and re-sending the same vote doesn't move the score further either. */
fun communityScore(noteId: String, ratings: List<Rating>): Int {
    val latestPerSigner = linkedMapOf<String, Boolean>()
    for (rating in ratings) {
        if (rating.noteId != noteId) continue
        val signer = rating.raterFingerprint ?: continue
        latestPerSigner[signer] = rating.helpful
    }
    return latestPerSigner.values.count { it } - latestPerSigner.values.count { !it }
}
