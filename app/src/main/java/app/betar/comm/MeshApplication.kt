package app.betar.comm

import android.app.Application
import app.betar.comm.messaging.BroadcastMessenger
import app.betar.comm.messaging.ChannelMessenger
import app.betar.comm.messaging.DirectMessenger
import app.betar.comm.messaging.FeedMessenger
import app.betar.comm.messaging.GroupMessenger
import app.betar.comm.messaging.NotesMessenger
import app.betar.comm.messaging.RatingsMessenger
import app.betar.comm.nostr.RelayBridge
import uniffi.mesh_core.FfiIdentity

/**
 * Owns the app-wide [MeshCoordinator] (BLE transport driver + `FfiMeshNode`), created lazily on
 * first access rather than at process start -- constructing it touches Bluetooth system services
 * and opens the encrypted store, neither of which should happen before `MainActivity` is ready to
 * gate BLE start behind runtime permissions. Backed by [MeshRelayService] as a foreground service
 * so the mesh keeps running while backgrounded, see `docs/IMPLEMENTATION-STATUS.md`.
 *
 * [identity]: a single stable identity for the whole app session, used by [directMessenger] for
 * Direct-message addressing and the handshake initiator tie-break, and as the source of the
 * short fingerprint ID shown throughout onboarding and Profile (see `docs/THREAT-MODEL.md`'s
 * identity-system section: the ID is a derivation of this key, never a separately issued one).
 * **Persisted across restarts** via [KeystoreIdentityStore] -- the fingerprint stays stable and
 * Direct contacts are no longer silently orphaned by every process restart, the biggest gap
 * this session's own product-review demo surfaced. [groupMessenger]'s MLS groups and
 * [directMessenger]'s hybrid prekey pool persist across restarts too (Keystore-wrapped
 * signers/secrets, AEAD-sealed group snapshots) -- see their own class docs.
 *
 * Pivoted 2026-11-01: [sosMessenger]/[bulletinMessenger]/[resourceMessenger]/[mapMessenger]
 * (SOS, disaster bulletin board, community resource board, offline map pins) are removed, not
 * repurposed, those features are cut. [feedMessenger] and [notesMessenger] replace them for the
 * new geohash-scoped Feed and Community Notes features, both built on `AppPost.kt`'s wire
 * framing, the direct successor to the old `CivicPost.kt`.
 */
class MeshApplication : Application() {
    val coordinator: MeshCoordinator by lazy { MeshCoordinator(this) }
    val identity: FfiIdentity by lazy { KeystoreIdentityStore.loadOrCreate(this) }
    val directMessenger: DirectMessenger by lazy { DirectMessenger(this, identity, coordinator) }
    val broadcastMessenger: BroadcastMessenger by lazy { BroadcastMessenger(coordinator) }
    val channelMessenger: ChannelMessenger by lazy { ChannelMessenger(this, coordinator) }
    val groupMessenger: GroupMessenger by lazy { GroupMessenger(this, identity, coordinator) }
    val feedMessenger: FeedMessenger by lazy { FeedMessenger(identity, coordinator) }
    val notesMessenger: NotesMessenger by lazy { NotesMessenger(identity, coordinator) }
    val ratingsMessenger: RatingsMessenger by lazy { RatingsMessenger(identity, coordinator) }

    /** The relay bridge (`nostr/RelayBridge.kt`): connects [feedMessenger]/[notesMessenger] to
     *  the open Nostr relay network whenever this device has internet. Lazy for the same
     *  reason [coordinator] is: constructing it touches `ConnectivityManager` and generates/
     *  loads a Keystore-wrapped Nostr identity, neither of which should happen before
     *  [MainActivity] is ready. */
    val relayBridge: RelayBridge by lazy { RelayBridge(this, feedMessenger, notesMessenger, identity, coordinator) }
}
