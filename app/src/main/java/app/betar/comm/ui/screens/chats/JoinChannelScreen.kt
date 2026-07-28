package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.MeshApplication
import app.betar.comm.R
import app.betar.comm.messaging.ChannelSession
import app.betar.comm.ui.components.DeliveryState
import app.betar.comm.ui.components.DeliveryStateIndicator
import app.betar.comm.ui.theme.BetarPolygonShapes
import kotlinx.coroutines.delay

/**
 * DESIGN-BRIEF.md §9 screen 14: "Join a group by name, with an unmissable warning that anyone
 * who knows the name and passphrase can read everything in it." Warning is the first thing on
 * the screen per the mockup's own note, and is not dismissible.
 */
@Composable
fun JoinChannelScreen(onJoined: (ChannelSession) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    var passphrase by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTopBar(stringResource(R.string.chats_new_join_channel), onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(18.dp),
        ) {
            Box(Modifier.size(24.dp).clip(BetarPolygonShapes.diamond).background(MaterialTheme.colorScheme.error))
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    stringResource(R.string.chats_join_channel_warning_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.chats_join_channel_warning_body),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.channel_passphrase_label)) },
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            app.channelMessenger.join(passphrase)?.let(onJoined)
        }) {
            Text(stringResource(R.string.channel_join_button))
        }
    }
}

/** Same conversation shape as a Direct thread, backed by ChannelMessenger.send/pollForNewPosts. */
@Composable
fun ChannelConversationScreen(selectorHex: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val channel = app.channelMessenger
    val session = remember(selectorHex) { channel.sessions.find { it.selectorHex == selectorHex } } ?: return
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            channel.pollForNewPosts()
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(session.label, onBack, modifier = Modifier.padding(12.dp))
        Text(
            stringResource(R.string.chats_open_channel_reminder),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.posts) { post ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                ) {
                    Text(post.text, style = MaterialTheme.typography.bodyLarge)
                    DeliveryStateIndicator(DeliveryState.Spreading)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.message_label)) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { channel.send(session, draft); draft = "" }) { Text(stringResource(R.string.action_send)) }
        }
    }
}
