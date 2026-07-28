package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.MeshApplication
import app.betar.comm.R
import app.betar.comm.messaging.Contact
import app.betar.comm.ui.components.DeliveryGlyph
import app.betar.comm.ui.components.DeliveryState
import app.betar.comm.ui.theme.BetarPolygonShapes
import kotlinx.coroutines.delay

/**
 * DESIGN-BRIEF.md §9 screens 6 (conversation list, real data) and 7 (empty state that teaches
 * how messages travel rather than apologising). Only Direct contacts are wired to a real polling
 * loop this pass; joined Channels/Groups are listed too but their own screens own their polling.
 */
@Composable
fun ConversationListScreen(
    onOpenDirect: (Contact) -> Unit,
    onOpenNewConversation: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val direct = app.directMessenger

    LaunchedEffect(Unit) {
        while (true) {
            direct.pollForNewEnvelopes()
            delay(1500)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (direct.contacts.isEmpty()) {
            EmptyChatsState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(direct.contacts, key = { it.fingerprintHex }) { contact ->
                    ConversationRow(contact = contact, onClick = { onOpenDirect(contact) })
                }
            }
        }
        // A plain .padding(20.dp) put this FAB directly under BetarScaffold's persistent SOS
        // FAB (same BottomEnd corner, near-identical bounds confirmed via uiautomator dump --
        // SOS drawn on top ate every tap, so "new conversation" was unreachable). The mockup
        // (scrEmpty() in design/Betar Chats and Onboarding.dc.html) already places this button
        // at bottom:158 against SOS's own bottom:96, i.e. stacked above it with clearance --
        // matched here instead of the flat corner padding.
        FloatingActionButton(
            onClick = onOpenNewConversation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 92.dp),
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ConversationRow(contact: Contact, onClick: () -> Unit) {
    val lastMessage = contact.messages.lastOrNull()
    val deliveryState = lastMessage?.let {
        if (it.fromMe) DeliveryState.Travelling else DeliveryState.Delivered
    } ?: DeliveryState.Waiting

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(BetarPolygonShapes.cookie9)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.fingerprintHex.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${contact.fingerprintHex.take(6)}…",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeliveryGlyph(deliveryState, size = 17.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                Text(
                    lastMessage?.text ?: stringResource(R.string.chats_row_no_messages_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyChatsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(160.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Text(
            stringResource(R.string.chats_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.chats_empty_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.chats_empty_cta), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
