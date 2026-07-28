package app.betar.comm.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import app.betar.comm.MeshCoordinator
import app.betar.comm.ui.screens.map.MapPin
import app.betar.comm.ui.screens.map.MapPinCategory
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "MapMessaging"
private const val PIN_TTL_HOPS: UByte = 16u
private const val PIN_EXPIRES_SECONDS = 72L * 3600L // same "goes stale" window as bulletins
private const val PRIORITY_MAP_PIN: UByte = 1u // reuses the bulletin tier, no dedicated Priority variant
private const val EXTRA_UNUSED = 0

/**
 * Map pins over the mesh (`FEATURES.md` §3: "users drop and share pins over the mesh: safe
 * zones, relief points, water, hazards, blocked roads, medical help. Pins are envelopes like any
 * other and propagate through the mesh"). Same shape as [BulletinMessenger]: an ordinary
 * `Addressing::Broadcast` envelope, magic-byte-prefixed via [CivicPost], differentiated from
 * plain chat and the other civic-post types by [MAGIC_MAP_PIN].
 *
 * **Real coordinates, not screen positions:** [CivicPost]'s wire format already carried an
 * optional fixed-point lat/lon field before this pass (added for a future SOS/bulletin location
 * feature that was never wired up); this is the first thing that actually populates it. A pin's
 * [GeoPoint] is always present here (a pin with no coordinate is meaningless), unlike SOS/bulletin
 * where it stays optional.
 *
 * **Not done:** no responder-key endorsement or signing, same gap every other civic-post type
 * has; no pin removal/expiry-from-UI (relies on the envelope's own `expires_at` aging it out of
 * the store, same mechanism bulletins use, but nothing currently prunes [pins] itself once that
 * happens locally -- a pin can outlive its backing envelope in this in-memory list).
 */
class MapPinMessenger(private val coordinator: MeshCoordinator) {
    val pins = mutableStateListOf<MapPin>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun drop(category: MapPinCategory, location: GeoPoint) {
        val payload = encodeCivicPayload(MAGIC_MAP_PIN, category.code, EXTRA_UNUSED, location, "")
        val now = nowSecondsShared()
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = PRIORITY_MAP_PIN,
            ttlHops = PIN_TTL_HOPS,
            expiresAt = (now + PIN_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for map pin", e)
        }
    }

    /** Call periodically -- see [DirectMessenger]'s class doc for why polling, not a callback. */
    fun pollForNewPins() {
        val node = coordinator.node()
        for (idHex in node.allIdsHex()) {
            if (!seenEnvelopeIds.add(idHex)) continue
            val bytes = node.getEnvelopeHex(idHex) ?: continue
            val parsed = try {
                envelopeUnpack(bytes)
            } catch (e: Exception) {
                continue
            }
            if (parsed.priorityTag != PRIORITY_MAP_PIN) continue
            val decoded = decodeCivicPayload(parsed.sealed) ?: continue
            if (decoded.magic != MAGIC_MAP_PIN) continue
            val location = decoded.location ?: continue // a pin with no coordinate can't be placed
            pins.add(
                MapPin(
                    idHex = parsed.idHex,
                    category = MapPinCategory.fromCode(decoded.category),
                    position = location,
                    droppedAtSeconds = nowSecondsShared(),
                ),
            )
        }
    }
}
