package app.betar.comm.nostr

import android.content.Context
import android.util.Log
import app.betar.comm.MeshCoordinator
import app.betar.comm.messaging.CommunityNote
import app.betar.comm.messaging.ContentOrigin
import app.betar.comm.messaging.FeedPost
import app.betar.comm.messaging.FeedMessenger
import app.betar.comm.messaging.MAGIC_NOTE
import app.betar.comm.messaging.MAGIC_POST
import app.betar.comm.messaging.NotesMessenger
import app.betar.comm.messaging.RoomPrecision
import app.betar.comm.messaging.encodeAppPostPayload
import app.betar.comm.messaging.nowSecondsShared
import app.betar.comm.messaging.signAppPostPayload
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "RelayBridge"
private const val KIND_TEXT_NOTE = 1
private const val TAG_TOPIC = "t"
private const val TOPIC_FEED = "betar-feed"
private const val TOPIC_NOTE = "betar-note"
private const val TAG_ROOM = "g"
private const val TAG_EVENT_REF = "e"
private const val TAG_URL_REF = "r"
private const val TAG_IMAGE = "image"
private const val SUB_ID = "betar-main"
// Matches NotesMessenger.kt's own FIELD_SEP: this file re-encodes a note's fields into the same
// AppPost.kt sub-format when re-gossiping relay-received content onto the mesh, so the two must
// agree on the delimiter even though each file defines its own copy of the character.
private const val NOTE_FIELD_SEP = '\u001F'
private const val REBROADCAST_TTL_HOPS: UByte = 8u
private const val REBROADCAST_EXPIRES_SECONDS = 72L * 3600L

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
 * **Full-resolution images, added 2026-11-01 (`BlossomClient.kt`):** a post bridged to Nostr
 * that carries a mesh thumbnail also uploads the full-resolution bytes to a Blossom blob server
 * and tags the event `["image", url, sha256Hex]`; an incoming event carrying that tag downloads
 * and hash-verifies the blob and shows it in place of a thumbnail. See `BlossomClient.kt`'s own
 * doc for what this is not (no NIP-92 compliance claim, no verified-live default server).
 *
 * **Relay-to-mesh re-gossip, added 2026-11-01:** a newly-received relay post or note is also
 * composed as an ordinary signed mesh Broadcast envelope ([rebroadcastPostOntoMesh]/
 * [rebroadcastNoteOntoMesh]), so an offline peer this device meets over BLE/Wi-Fi Direct still
 * receives content that only reached this device through the internet. **A real, stated
 * limitation:** `AppPost.kt`'s wire format carries no origin field, so once re-gossiped, the
 * content is indistinguishable from an ordinary mesh-originated post to anyone who receives it
 * that way, including, on this same device, this device's own next `pollForNewPosts()` cycle,
 * which will add a second, mesh-origin [FeedPost]/[CommunityNote] entry for content already
 * shown once as relay-origin. Not a crash, not silent data corruption, just a duplicate list
 * entry; fixing it needs a wire-format origin field, a larger change, not attempted this pass.
 *
 * **Not done, stated plainly:**
 * - No real geohash: `roomPrecision` only carries the coarse block/city/region/country tier
 *   chosen in the composer, not an actual location-derived geohash string, same gap
 *   `AppPost.kt`/`FeedMessenger.kt` already state. The `g` tag above is a precision label, not
 *   a geohash.
 * - No retry/backoff for a publish that silently fails (the relay's `OK` response is logged,
 *   not acted on). A post or note that fails to bridge on one `tick()` is retried on the next
 *   only because it is still unmarked as bridged, not because of any explicit failure handling.
 */
class RelayBridge(
    context: Context,
    private val feedMessenger: FeedMessenger,
    private val notesMessenger: NotesMessenger,
    private val identity: FfiIdentity,
    private val coordinator: MeshCoordinator,
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
     *  internet, then publishes anything newly mesh-originated that hasn't been bridged yet.
     *  Suspends (never blocks the calling dispatcher) since bridging a post with an image does
     *  a real blocking HTTP upload (`BlossomClient.upload`) -- the caller (`FeedTab.kt`'s
     *  `LaunchedEffect` poll loop) already runs this inside a coroutine, same as every other
     *  periodic call there. */
    suspend fun tick() {
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

    private suspend fun bridgeNewPosts() {
        for (post in feedMessenger.posts) {
            if (post.origin != ContentOrigin.Mesh) continue
            if (post.idHex in bridgedMeshIds) continue
            val tags = mutableListOf(
                listOf(TAG_TOPIC, TOPIC_FEED),
                listOf(TAG_ROOM, ROOM_LABELS[post.roomPrecision] ?: "city"),
            )
            // Best-effort: a thumbnail that fails to upload (no network, server down, no
            // verified server configured, see BlossomClient's own doc) just means this post
            // bridges without an "image" tag, the same as before this feature existed -- never
            // blocks the post's own text from bridging.
            post.thumbnailBytes?.let { bytes ->
                val url = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BlossomClient.upload(keyPair, bytes)
                }
                if (url != null) {
                    tags += listOf(TAG_IMAGE, url, BlossomClient.sha256Hex(bytes))
                }
            }
            val event = NostrEvent.create(
                keyPair = keyPair,
                kind = KIND_TEXT_NOTE,
                tags = tags,
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
            // Full resolution, not a mesh-sized thumbnail: whatever BlossomClient.download
            // returns is the exact bytes Blossom's own content-addressed hash check already
            // verified, no further downsampling applied here. A missing or failed-to-verify
            // "image" tag just means this relay-origin post shows with no image, same
            // best-effort pattern bridgeNewPosts's own upload side follows.
            val imageTag = event.tags.firstOrNull { it.size >= 3 && it[0] == TAG_IMAGE }
            val imageBytes = imageTag?.let { BlossomClient.download(url = it[1], expectedSha256 = it[2]) }
            feedMessenger.posts.add(
                0,
                FeedPost(
                    text = event.content,
                    idHex = "nostr:${event.id}",
                    roomPrecision = precision,
                    postedAtSeconds = event.createdAt,
                    origin = ContentOrigin.Relay,
                    thumbnailBytes = imageBytes,
                ),
            )
            rebroadcastPostOntoMesh(event.content, precision, imageBytes)
        } else if (TOPIC_NOTE in topics) {
            val postNostrId = event.tags.firstOrNull { it.size >= 2 && it[0] == TAG_EVENT_REF }?.get(1) ?: return
            val links = event.tags.filter { it.size >= 2 && it[0] == TAG_URL_REF }.map { it[1] }
            val link1 = links.getOrElse(0) { "" }
            val link2 = links.getOrElse(1) { "" }
            notesMessenger.notes.add(
                CommunityNote(
                    postId = "nostr:$postNostrId",
                    link1 = link1,
                    link2 = link2,
                    explanation = event.content,
                    idHex = "nostr:${event.id}",
                    origin = ContentOrigin.Relay,
                ),
            )
            rebroadcastNoteOntoMesh("nostr:$postNostrId", link1, link2, event.content)
        }

        Log.d(TAG, "received a ${if (TOPIC_FEED in topics) "post" else "note"} from the relay bridge")
    }

    /** Re-gossip a relay-received post onto the local mesh as an ordinary signed Broadcast
     *  envelope, so an offline peer this device meets over BLE/Wi-Fi Direct still receives it.
     *  See this class's own doc comment for the real, stated duplicate-display limitation this
     *  carries. [imageBytes], if present, is the full-resolution Blossom download, not
     *  downsampled before composing onto the mesh -- an honest tradeoff stated here rather than
     *  silently shipping a second, different-size copy of the same image: a large image makes
     *  this one envelope large, same cost any image post already has, not a new problem. */
    private fun rebroadcastPostOntoMesh(text: String, roomPrecision: Int, imageBytes: ByteArray?) {
        val now = nowSecondsShared()
        val unsigned = encodeAppPostPayload(MAGIC_POST, roomPrecision, location = null, imageBytes = imageBytes, text = text)
        val payload = signAppPostPayload(identity, unsigned)
        composeOntoMesh(payload, now)
    }

    /** Re-gossip a relay-received note onto the local mesh, same rationale as
     *  [rebroadcastPostOntoMesh]. Re-encodes the same `postId<0x1F>link1<0x1F>link2<0x1F>
     *  explanation` sub-format `NotesMessenger.kt` defines, since this file has no access to
     *  that class's private encoder. */
    private fun rebroadcastNoteOntoMesh(postId: String, link1: String, link2: String, explanation: String) {
        val now = nowSecondsShared()
        val encodedText = listOf(postId, link1, link2, explanation).joinToString(NOTE_FIELD_SEP.toString())
        val unsigned = encodeAppPostPayload(MAGIC_NOTE, roomPrecision = 0, location = null, text = encodedText)
        val payload = signAppPostPayload(identity, unsigned)
        composeOntoMesh(payload, now)
    }

    /** Composes [signedPayload] onto the mesh, and marks the resulting envelope id as already
     *  bridged *before* it can ever be discovered as a "new mesh-origin post" -- without this,
     *  `pollForNewPosts()` would pick up this device's own relay-to-mesh rebroadcast as an
     *  ordinary new mesh post on the next poll, and [bridgeNewPosts] would re-publish it back
     *  to Nostr as a second, distinct event, which would then come back through this exact
     *  function again: an unbounded feedback loop between the mesh and the relay, found and
     *  closed here, not shipped and hoped against. [envelopePack]'s output is unpacked locally
     *  (pure parsing, no store or network involved) purely to learn the id this same bytes will
     *  be assigned once stored, reusing [bridgedMeshIds], the exact set [bridgeNewPosts]/
     *  [bridgeNewNotes] already check before bridging mesh content forward. */
    private fun composeOntoMesh(signedPayload: ByteArray, now: Long) {
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = 2u, // Normal
            ttlHops = REBROADCAST_TTL_HOPS,
            expiresAt = (now + REBROADCAST_EXPIRES_SECONDS).toULong(),
            sealed = signedPayload,
        )
        val resultingIdHex = try {
            envelopeUnpack(bytes).idHex
        } catch (e: Exception) {
            null
        }
        if (resultingIdHex != null) bridgedMeshIds += resultingIdHex
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for relay-to-mesh rebroadcast", e)
        }
    }
}
