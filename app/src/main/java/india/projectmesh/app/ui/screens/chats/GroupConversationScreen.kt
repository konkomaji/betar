package india.projectmesh.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import india.projectmesh.app.MeshApplication
import india.projectmesh.app.R
import india.projectmesh.app.messaging.GroupPost
import kotlinx.coroutines.delay

/**
 * DESIGN-BRIEF.md §9 screens 16 (group conversation) and 17 (message long-press floating
 * toolbar, shared with DirectConversationScreen's implementation). The open-group warning is
 * shown here as a permanent header, not a dismissible banner, per the mockup's own note.
 */
@Composable
fun GroupConversationScreen(selectorHex: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val group = app.groupMessenger
    val session = remember(selectorHex) { group.sessions.find { it.selectorHex == selectorHex } } ?: return
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            group.pollForNewPosts()
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(session.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
        Text(
            stringResource(R.string.chats_open_group_reminder),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(10.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.posts) { post -> GroupPostBubble(post) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.message_label)) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { group.send(session, draft); draft = "" }) { Text(stringResource(R.string.action_send)) }
        }
    }
}

@Composable
private fun GroupPostBubble(post: GroupPost) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(12.dp),
        ) {
            Text(post.text, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.chats_message_action_resend)) }, onClick = { menuOpen = false })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chats_message_action_copy)) },
                onClick = { clipboard.setText(AnnotatedString(post.text)); menuOpen = false },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.chats_message_action_delete_for_me)) }, onClick = { menuOpen = false })
        }
    }
}
