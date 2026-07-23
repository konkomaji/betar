package india.projectmesh.app.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import india.projectmesh.app.MeshApplication
import india.projectmesh.app.R
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 1000L

/**
 * Real messaging UI for the two addressing modes actually wireable today -- Direct and
 * Broadcast. Channel and Group aren't here: neither is exported over UniFFI yet (`Channel` from
 * `core/src/crypto/channel.rs`, MLS groups from `core/src/groups.rs` -- both built and tested in
 * Rust, neither reachable from Kotlin), so a UI claiming to drive them would be UI theater over
 * nothing. Tracked as separate follow-ups rather than stubbed in here.
 */
@Composable
fun MessagingScreen() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.messaging_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.messaging_subtitle), style = MaterialTheme.typography.bodySmall)

        BroadcastSection(app.broadcastMessenger)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        DirectSection(app.directMessenger)
    }
}

@Composable
private fun BroadcastSection(messenger: BroadcastMessenger) {
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewPosts()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.broadcast_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.message_label)) },
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    messenger.send(draft)
                    draft = ""
                }
            }) {
                Text(stringResource(R.string.action_post))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            items(messenger.posts) { post ->
                Text(post.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DirectSection(messenger: DirectMessenger) {
    var fingerprintDraft by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            messenger.pollForNewEnvelopes()
            delay(POLL_INTERVAL_MS)
        }
    }

    val contactRowFormat = stringResource(R.string.direct_contact_row)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.direct_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.direct_my_fingerprint_label), style = MaterialTheme.typography.bodySmall)
        Text(messenger.myFingerprintHex, style = MaterialTheme.typography.bodySmall)

        val contact = selectedContact
        if (contact == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fingerprintDraft,
                    onValueChange = { fingerprintDraft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.direct_contact_fingerprint_label)) },
                )
                Button(onClick = {
                    val added = messenger.addContact(fingerprintDraft)
                    if (added != null) fingerprintDraft = ""
                }) {
                    Text(stringResource(R.string.action_add))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                items(messenger.contacts) { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(contactRowFormat.format(c.fingerprintHex.take(16), c.status), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { selectedContact = c }) { Text(stringResource(R.string.action_open)) }
                    }
                }
            }
        } else {
            ContactThread(messenger, contact, onBack = { selectedContact = null })
        }
    }
}

@Composable
private fun ContactThread(messenger: DirectMessenger, contact: Contact, onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val contactRowFormat = stringResource(R.string.direct_contact_row)
    val mePrefix = stringResource(R.string.direct_chat_me_prefix)
    val themPrefix = stringResource(R.string.direct_chat_them_prefix)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(
                contactRowFormat.format(contact.fingerprintHex.take(16), contact.status),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            items(contact.messages) { m ->
                Text(
                    (if (m.fromMe) mePrefix else themPrefix) + m.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.message_label)) },
                enabled = contact.status == ContactStatus.CONNECTED,
            )
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        messenger.sendMessage(contact, draft)
                        draft = ""
                    }
                },
                enabled = contact.status == ContactStatus.CONNECTED,
            ) {
                Text(stringResource(R.string.action_send))
            }
        }
        if (contact.status != ContactStatus.CONNECTED) {
            Text(stringResource(R.string.direct_waiting_handshake), style = MaterialTheme.typography.bodySmall)
        }
    }
}
