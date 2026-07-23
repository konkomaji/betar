package india.projectmesh.app

import android.app.Application

/**
 * Owns the app-wide [MeshCoordinator] (BLE transport driver + `FfiMeshNode`), created lazily on
 * first access rather than at process start -- constructing it touches Bluetooth system services
 * and opens the encrypted store, neither of which should happen before `MainActivity` is ready to
 * gate BLE start behind runtime permissions. No foreground relay service yet (deferred, see
 * `docs/IMPLEMENTATION-STATUS.md`) -- the mesh only runs while the app is foregrounded.
 */
class MeshApplication : Application() {
    val coordinator: MeshCoordinator by lazy { MeshCoordinator(this) }
}
