package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/** Matches every mockup in `design/Betar Chats and Onboarding.dc.html`'s `appBar(title, iconBtn('b','←'))`
 * pattern and the Workflow Map's global rule ("back is always the top left arrow"). Shared across
 * the Chats tab's sub-screens rather than duplicated per screen; [trailing] is for the rare screen
 * that needs something else in the bar too (e.g. [DirectConversationScreen]'s trust chip). */
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Text("←", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * In-person verification ("met in person" vs "not met yet") tracked here, in the UI layer only,
 * for this pass -- there is no persisted/cryptographically-bound trust record in
 * DirectMessenger/Contact yet (CRYPTOGRAPHY.md §3's real gap: no QR-code trust establishment).
 * Tapping "Yes, they match" on the verify screen only sets this in-memory flag; it does not
 * survive process death and provides no cryptographic guarantee, it is a UI affordance ahead of
 * the real mechanism landing. Flagged here rather than silently presented as real trust storage.
 */
object TrustStore {
    private val verified = mutableStateMapOf<String, Boolean>()

    fun isVerified(fingerprintHex: String): Boolean = verified[fingerprintHex] == true

    fun markVerified(fingerprintHex: String) {
        verified[fingerprintHex] = true
    }
}

/** Sub-screens within the Chats tab, driven by simple local state rather than a nested NavHost --
 * kept intentionally small so a single back-stack of one matches how this tab is actually used
 * (list, then one destination at a time). */
sealed class ChatsRoute {
    data object List : ChatsRoute()
    data object NewConversationSheet : ChatsRoute()
    data object ScanCode : ChatsRoute()
    data object ShowMyCode : ChatsRoute()
    data class VerifyInPerson(val fingerprintHex: String) : ChatsRoute()
    data class DirectThread(val fingerprintHex: String) : ChatsRoute()
    data object JoinChannelByName : ChatsRoute()
    data class ChannelThread(val selectorHex: String) : ChatsRoute()
    data object CreateGroup : ChatsRoute()
    data class GroupThread(val selectorHex: String) : ChatsRoute()
}
