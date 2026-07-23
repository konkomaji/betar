package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "ResourceMessaging"
private const val RESOURCE_TTL_HOPS: UByte = 16u
private const val RESOURCE_EXPIRES_SECONDS = 72L * 3600L
private const val PRIORITY_NORMAL: UByte = 2u // no dedicated Priority variant; FEATURES.md SS7 ranks it above chat

enum class ResourceKind(val code: Int, val label: String) {
    HAVE(0, "Have"),
    NEED(1, "Need"),
    ;

    companion object {
        fun fromCode(code: Int): ResourceKind = entries.find { it.code == code } ?: HAVE
    }
}

enum class ResourceCategory(val code: Int, val label: String) {
    FOOD(0, "Food"),
    SHELTER(1, "Shelter"),
    TRANSPORT(2, "Transport"),
    TOOLS(3, "Tools"),
    BLOOD_DONOR(4, "Blood donor"),
    CHARGING(5, "Charging point"),
    LABOUR(6, "Labour"),
    OTHER(7, "Other"),
    ;

    companion object {
        fun fromCode(code: Int): ResourceCategory = entries.find { it.code == code } ?: OTHER
    }
}

data class ResourcePost(val idHex: String, val kind: ResourceKind, val category: ResourceCategory, val text: String, val timestampSeconds: Long)

/**
 * Community resource board (`FEATURES.md` §4): a local "have/need" exchange -- food, shelter,
 * transport, tools, blood donors, charging points, labour. Useful in ordinary rural life, not
 * only disasters, per the doc -- deliberately not SOS/bulletin priority (`Priority::Normal`, the
 * closest available fit; the `Priority` enum has no dedicated "resource" tier, a known coarseness
 * inherited from `envelope.rs`, not something this pass changes).
 *
 * **Not done:** no matching/search across have vs. need entries (a real UX feature, not attempted
 * here -- this pass is a flat feed like the other two); unsigned, same gap as the rest.
 */
class ResourceMessenger(private val coordinator: MeshCoordinator) {
    val posts = mutableStateListOf<ResourcePost>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun send(kind: ResourceKind, category: ResourceCategory, text: String) {
        val payload = encodeCivicPayload(MAGIC_RESOURCE, category.code, kind.code, null, text)
        val now = nowSecondsShared()
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = PRIORITY_NORMAL,
            ttlHops = RESOURCE_TTL_HOPS,
            expiresAt = (now + RESOURCE_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for resource post", e)
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
            if (parsed.priorityTag != PRIORITY_NORMAL) continue
            val decoded = decodeCivicPayload(parsed.sealed) ?: continue
            if (decoded.magic != MAGIC_RESOURCE) continue
            posts.add(
                0,
                ResourcePost(
                    parsed.idHex,
                    ResourceKind.fromCode(decoded.extra),
                    ResourceCategory.fromCode(decoded.category),
                    decoded.text,
                    nowSecondsShared(),
                ),
            )
        }
    }
}
