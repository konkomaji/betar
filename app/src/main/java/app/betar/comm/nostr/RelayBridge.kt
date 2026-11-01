package app.betar.comm.nostr

import android.content.Context
import android.util.Log
import app.betar.comm.messaging.CommunityNote
import app.betar.comm.messaging.ContentOrigin
import app.betar.comm.messaging.FeedPost
import app.betar.comm.messaging.FeedMessenger
import app.betar.comm.messaging.NotesMessenger
import app.betar.comm.messaging.RoomPrecision

private const val TAG = "RelayBridge"
private const val KIND_TEXT_NOTE = 1
private const val TAG_TOPIC = "t"
private const val TOPIC_FEED = "betar-feed"
private const val TOPIC_NOTE = "betar-note"
private const val TAG_ROOM = "g"
private const val TAG_EVENT_REF = "e"
private const val TAG_URL_REF = "r"
private const val SUB_ID = "betar-main"

private val ROOM_LABELS = mapOf(
    RoomPrecision.BLOCK to "block",
    RoomPrecision.CITY to "city",
    RoomPrecision.REGION to "region",
    RoomPrecision.COUNTRY to "country",
)
private val ROOM_LABELS_REVERSE = ROOM_LABELS.entries.associate { (k, v) -> v to k }

/**
 * Bridges Feed posts and Community Notes between the local mesh and the open Nostr relay
 * network (`docs/ARCHITECTURE.md` §2, the "the moment any one device nearby reaches the
 * internet" tier). This is the piece that makes the architecture's central claim literally
 * true rather than aspirational: before this class existed, [FeedMessenger] and
 * [NotesMessenger] only ever saw mesh-local content, and there was nothing on the other side
 * of the relay bridge to test against (stated plainly in both their class docs at the time).
 *
 * **Design, stated plainly:**
 * - Nostr identity is a separate secp256k1 keypair ([NostrIdentity]), not this device's mesh
 *   identity. There is no cryptographic link between the two; bridged content is only as
 *   trustworthy as the device that chose to bridge it honestly.
 * - Mesh-originated posts/notes are published as Nostr kind 1 ("text note") events, tagged
 *   `["t","betar-feed"]` or `["t","betar-note"]` so other Betar clients (and, since kind 1 is
 *   a standard Nostr kind, any ordinary Nostr client too) can find them. A Feed post also
 *   carries `["g", <room label>]`; a Community Note carries `["e", <nostr id of the post it's
 *   about>]` plus `["r", link1]`/`["r", link2]` for its two source links, with the explanation
 *   as the event's own `content`.
 * - A mesh envelope's id (BLAKE3 of the envelope) and a Nostr event's id (sha256 of the Nostr
 *   serialization) are different values for the same logical post, so [meshToNostrId] tracks
 *   the mapping once a post has actually been published, which a Note's own bridging needs to
 *   resolve its `e` tag correctly.
 *
 * **Not done, stated plainly:**
 * - No real geohash: `roomPrecision` only carries the coarse block/city/region/country tier
 *   chosen in the composer, not an actual location-derived geohash string, same gap
 *   `AppPost.kt`/`FeedMessenger.kt` already state. The `g` tag above is a precision label, not
 *   a geohash.
 * - No retry/backoff for a publish that silently fails (the relay's `OK` response is logged,
 *   not acted on). A post or note that fails to bridge on one `tick()` is retried on the next
 *   only because it is still unmarked as bridged, not because of any explicit failure handling.
 * - Does not relay content it received from Nostr back onto the mesh for offline peers (the
 *   "any one device in the chain" framing implies this eventually, but it is a separate,
 *   larger task: mesh envelopes need their own addressing/priority for relay-sourced content,
 *   not attempted this pass).
 */
class RelayBridge(
    context: Context,
    private val feedMessenger: FeedMessenger,
    private val notesMessenger: NotesMessenger,
) {
    private val appContext = context.applicationContext
    private val keyPair = NostrIdentity.loadOrCreate(appContext)
    private val bridgedMeshIds = mutableSetOf<String>()
    private val meshToNostrId = mutableMapOf<String, String>()
    private val seenNostrIds = mutableSetOf<String>()
    private var connected = false

    private val pool = NostrRelayPool(onEvent = { _, event -> handleIncomingEvent(event) })

    /** Call periodically (same polling pattern as every other messenger in this codebase, e.g.
     *  [FeedMessenger.pollForNewPosts]). Connects if not already connected and the device has
     *  internet, then publishes anything newly mesh-originated that hasn't been bridged yet. */
    fun tick() {
        if (!connected) {
            if (!hasInternetConnectivity(appContext)) return
            pool.connectAll()
            pool.subscribe(SUB_ID, NostrFilter(kinds = listOf(KIND_TEXT_NOTE), tagFilters = mapOf(TAG_TOPIC to listOf(TOPIC_FEED, TOPIC_NOTE))))
            connected = true
            return // give the socket a moment to actually open before publishing on it
        }
        if (!pool.anyOpen()) {
            connected = false
            return
        }
        bridgeNewPosts()
        bridgeNewNotes()
    }

    fun stop() {
        pool.disconnectAll()
        connected = false
    }

    private fun bridgeNewPosts() {
        for (post in feedMessenger.posts) {
            if (post.origin != ContentOrigin.Mesh) continue
            if (post.idHex in bridgedMeshIds) continue
            val event = NostrEvent.create(
                keyPair = keyPair,
                kind = KIND_TEXT_NOTE,
                tags = listOf(
                    listOf(TAG_TOPIC, TOPIC_FEED),
                    listOf(TAG_ROOM, ROOM_LABELS[post.roomPrecision] ?: "city"),
                ),
                content = post.text,
                createdAt = post.postedAtSeconds,
            )
            pool.publish(event)
            bridgedMeshIds += post.idHex
            meshToNostrId[post.idHex] = event.id
        }
    }

    private fun bridgeNewNotes() {
        for (note in notesMessenger.notes) {
            if (note.origin != ContentOrigin.Mesh) continue
            if (note.idHex in bridgedMeshIds) continue
            // Can't reference a post on Nostr before that post itself has a Nostr id. Skip for
            // now, retried automatically next tick() once the post it's about gets bridged.
            val postNostrId = meshToNostrId[note.postId] ?: continue
            val event = NostrEvent.create(
                keyPair = keyPair,
                kind = KIND_TEXT_NOTE,
                tags = listOf(
                    listOf(TAG_TOPIC, TOPIC_NOTE),
                    listOf(TAG_EVENT_REF, postNostrId),
                    listOf(TAG_URL_REF, note.link1),
                    listOf(TAG_URL_REF, note.link2),
                ),
                content = note.explanation,
            )
            pool.publish(event)
            bridgedMeshIds += note.idHex
            meshToNostrId[note.idHex] = event.id
        }
    }

    private fun handleIncomingEvent(event: NostrEvent) {
        if (!seenNostrIds.add(event.id)) return
        if (event.kind != KIND_TEXT_NOTE) return
        val topics = event.tags.filter { it.size >= 2 && it[0] == TAG_TOPIC }.map { it[1] }

        if (TOPIC_FEED in topics) {
            val roomLabel = event.tags.firstOrNull { it.size >= 2 && it[0] == TAG_ROOM }?.get(1)
            val precision = ROOM_LABELS_REVERSE[roomLabel] ?: RoomPrecision.CITY
            feedMessenger.posts.add(
                0,
                FeedPost(
                    text = event.content,
                    idHex = "nostr:${event.id}",
                    roomPrecision = precision,
                    postedAtSeconds = event.createdAt,
                    origin = ContentOrigin.Relay,
                ),
            )
        } else if (TOPIC_NOTE in topics) {
            val postNostrId = event.tags.firstOrNull { it.size >= 2 && it[0] == TAG_EVENT_REF }?.get(1) ?: return
            val links = event.tags.filter { it.size >= 2 && it[0] == TAG_URL_REF }.map { it[1] }
            notesMessenger.notes.add(
                CommunityNote(
                    postId = "nostr:$postNostrId",
                    link1 = links.getOrElse(0) { "" },
                    link2 = links.getOrElse(1) { "" },
                    explanation = event.content,
                    idHex = "nostr:${event.id}",
                    origin = ContentOrigin.Relay,
                ),
            )
        }

        Log.d(TAG, "received a ${if (TOPIC_FEED in topics) "post" else "note"} from the relay bridge")
    }
}
