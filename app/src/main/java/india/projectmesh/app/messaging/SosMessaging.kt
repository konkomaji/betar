package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "SosMessaging"
private const val SOS_TTL_HOPS: UByte = 32u // maximal spread -- SOS should reach as far as the mesh can carry it
private const val SOS_EXPIRES_SECONDS = 24L * 3600L // shorter-lived than bulletins; an SOS from yesterday is stale
private const val PRIORITY_SOS: UByte = 0u
private const val EXTRA_NEW_ALERT = 0
private const val EXTRA_ACKNOWLEDGEMENT = 1

enum class SosCategory(val code: Int, val label: String) {
    MEDICAL(0, "Medical"),
    TRAPPED(1, "Trapped"),
    FIRE(2, "Fire"),
    VIOLENCE(3, "Violence"),
    OTHER(4, "Other"),
    ;

    companion object {
        fun fromCode(code: Int): SosCategory = entries.find { it.code == code } ?: OTHER
    }
}

data class SosAlert(val idHex: String, val category: SosCategory, val text: String, val location: GeoPoint?, val timestampSeconds: Long)

/**
 * Emergency SOS (`FEATURES.md` §1): one-tap high-priority broadcast, `Priority::Sos` so
 * `RelayEngine`'s existing priority-ordering and eviction rules (SOS never evicted for
 * lower-priority traffic, transferred first on contact) already apply with zero routing-layer
 * changes -- this feature only needed an app-layer payload shape and a UI, not new core logic.
 *
 * **Acknowledgement, a real design choice made explicitly:** an ACK is itself a public broadcast
 * (`extra = EXTRA_ACKNOWLEDGEMENT`, `text` holds the acknowledged envelope's hex ID) rather than
 * a private reply to the sender. Deliberate, not an oversight: `Addressing::Broadcast` carries no
 * sender identity at all (unlike Direct messages, which embed one for handshake routing -- see
 * `DirectMessaging.kt`), and *adding* one to SOS specifically, so an ACK could be addressed
 * privately back, would deanonymize whoever is in danger -- the wrong tradeoff for exactly this
 * feature. A public ACK ("someone nearby has seen this") fits `FEATURES.md` §1's "nearby users
 * see an SOS ... and can acknowledge" without that cost.
 *
 * **Not done:** no device location (see `CivicPost.kt`'s doc comment); no distance/direction
 * display (`FEATURES.md` §1 mentions this, needs the same location integration); unsigned.
 */
class SosMessenger(private val coordinator: MeshCoordinator) {
    val alerts = mutableStateListOf<SosAlert>()
    val acknowledgedIdHexes = mutableStateListOf<String>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    fun send(category: SosCategory, text: String) {
        val payload = encodeCivicPayload(MAGIC_SOS, category.code, EXTRA_NEW_ALERT, null, text)
        composeAndSend(payload)
    }

    fun acknowledge(alert: SosAlert) {
        val payload = encodeCivicPayload(MAGIC_SOS, alert.category.code, EXTRA_ACKNOWLEDGEMENT, null, alert.idHex)
        composeAndSend(payload)
    }

    private fun composeAndSend(payload: ByteArray) {
        val now = nowSecondsShared()
        val bytes = envelopePack(
            addressingTag = 0u, // Broadcast
            addressingTarget = null,
            priorityTag = PRIORITY_SOS,
            ttlHops = SOS_TTL_HOPS,
            expiresAt = (now + SOS_EXPIRES_SECONDS).toULong(),
            sealed = payload,
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for SOS payload", e)
        }
    }

    /** Call periodically -- see [DirectMessenger]'s class doc for why polling, not a callback. */
    fun pollForNewAlerts() {
        val node = coordinator.node()
        for (idHex in node.allIdsHex()) {
            if (!seenEnvelopeIds.add(idHex)) continue
            val bytes = node.getEnvelopeHex(idHex) ?: continue
            val parsed = try {
                envelopeUnpack(bytes)
            } catch (e: Exception) {
                continue
            }
            if (parsed.priorityTag != PRIORITY_SOS) continue
            val decoded = decodeCivicPayload(parsed.sealed) ?: continue
            if (decoded.magic != MAGIC_SOS) continue

            if (decoded.extra == EXTRA_ACKNOWLEDGEMENT) {
                if (!acknowledgedIdHexes.contains(decoded.text)) acknowledgedIdHexes.add(decoded.text)
            } else {
                alerts.add(
                    0,
                    SosAlert(parsed.idHex, SosCategory.fromCode(decoded.category), decoded.text, decoded.location, nowSecondsShared()),
                )
            }
        }
    }
}
