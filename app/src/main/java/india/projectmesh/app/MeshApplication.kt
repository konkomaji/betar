package india.projectmesh.app

import android.app.Application
import india.projectmesh.app.messaging.BroadcastMessenger
import india.projectmesh.app.messaging.BulletinMessenger
import india.projectmesh.app.messaging.ChannelMessenger
import india.projectmesh.app.messaging.DirectMessenger
import india.projectmesh.app.messaging.GroupMessenger
import india.projectmesh.app.messaging.ResourceMessenger
import india.projectmesh.app.messaging.SosMessenger
import uniffi.mesh_core.FfiIdentity

/**
 * Owns the app-wide [MeshCoordinator] (BLE transport driver + `FfiMeshNode`), created lazily on
 * first access rather than at process start -- constructing it touches Bluetooth system services
 * and opens the encrypted store, neither of which should happen before `MainActivity` is ready to
 * gate BLE start behind runtime permissions. Backed by [MeshRelayService] as a foreground service
 * so the mesh keeps running while backgrounded, see `docs/IMPLEMENTATION-STATUS.md`.
 *
 * [identity]: a single stable identity for the whole app session, used by [directMessenger] for
 * Direct-message addressing and the handshake initiator tie-break. **Now persisted across
 * restarts** via [KeystoreIdentityStore] -- the fingerprint stays stable and Direct contacts
 * are no longer silently orphaned by every process restart, the biggest gap this session's own
 * product-review demo surfaced.
 */
class MeshApplication : Application() {
    val coordinator: MeshCoordinator by lazy { MeshCoordinator(this) }
    val identity: FfiIdentity by lazy { KeystoreIdentityStore.loadOrCreate(this) }
    val directMessenger: DirectMessenger by lazy { DirectMessenger(this, identity, coordinator) }
    val broadcastMessenger: BroadcastMessenger by lazy { BroadcastMessenger(coordinator) }
    val channelMessenger: ChannelMessenger by lazy { ChannelMessenger(this, coordinator) }
    val groupMessenger: GroupMessenger by lazy { GroupMessenger(identity, coordinator) }
    val sosMessenger: SosMessenger by lazy { SosMessenger(coordinator) }
    val bulletinMessenger: BulletinMessenger by lazy { BulletinMessenger(coordinator) }
    val resourceMessenger: ResourceMessenger by lazy { ResourceMessenger(coordinator) }
}
