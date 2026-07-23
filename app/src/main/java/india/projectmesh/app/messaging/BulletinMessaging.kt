package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "BulletinMessaging"
private const val BULLETIN_TTL_HOPS: UByte = 16u
private const val BULLETIN_EXPIRES_SECONDS = 72L * 3600L // ROUTING-PROTOCOL.md's example 24-72h range
private const val PRIORITY_BULLETIN: UByte = 1u
private const val EXTRA_UNUSED = 0

enum class BulletinCategory(val code: Int, val label: String) {
    RELIEF_CAMP(0, "Relief camp"),
    FOOD(1, "Food"),
    WATER(2, "Water"),
    MEDICINE(3, "Medicine"),
    ROAD_STATUS(4, "Road/bridge status"),
    SHELTER(5, "Shelter"),
    MISSING_PERSON(6, "Missing person"),
    OTHER(7, "Other"),
    ;

    companion object {
        fun fromCode(code: Int): BulletinCategory = entries.find { it.code == code } ?: OTHER
    }
}

data class BulletinPost(val idHex: String, val category: BulletinCategory, val text: String, val timestampSeconds: Long)

/**
 * Disaster bulletin board (`FEATURES.md` §2): a store-and-forward public notice board --
 * relief-camp locations, food/water/medicine availability, road/bridge status, shelter openings,
 * missing-person notices. `Priority::Bulletin` (second-highest, after SOS) so `RelayEngine`
 * already prioritizes it over ordinary chatter with no routing-layer changes needed.
 *
 * **Not done:** no responder-key endorsement (`FEATURES.md` §2's "bulletins from known responder
 * keys can be visually distinguished" needs a trusted-key list this app doesn't have); unsigned,
 * same gap as broadcast chat and SOS.
 */
class BulletinMessenger(private val coordinator: MeshCoordinator) {
    val posts = mutableStateListOf<BulletinPost>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun send(category: BulletinCategory, text: String) {
        val payload = encodeCivicPayload(MAGIC_BULLETIN, category.code, EXTRA_UNUSED, null, text)
        val now = nowSecondsShared()
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = PRIORITY_BULLETIN,
            ttlHops = BULLETIN_TTL_HOPS,
            expiresAt = (now + BULLETIN_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for bulletin", e)
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
            if (parsed.priorityTag != PRIORITY_BULLETIN) continue
            val decoded = decodeCivicPayload(parsed.sealed) ?: continue
            if (decoded.magic != MAGIC_BULLETIN) continue
            posts.add(0, BulletinPost(parsed.idHex, BulletinCategory.fromCode(decoded.category), decoded.text, nowSecondsShared()))
        }
    }
}
