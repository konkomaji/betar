package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.MeshApplication
import app.betar.comm.R
import app.betar.comm.messaging.GroupSession

/**
 * DESIGN-BRIEF.md §9 screen 15: private group creation and adding members by code. Backed by
 * the real GroupMessenger (MLS via FfiMlsMember/FfiMlsGroupHandle) -- createGroup, then a real
 * key-package/commit/welcome exchange, all still manual/out-of-band (pasted hex) same as the
 * class doc for GroupMessaging.kt already states; this screen doesn't add automation, just a
 * real UI for the existing manual flow.
 */
@Composable
fun CreateGroupScreen(onGroupCreated: (GroupSession) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val group = app.groupMessenger
    val clipboard = LocalClipboardManager.current

    var label by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<GroupSession?>(null) }
    var memberKeyPackageInput by remember { mutableStateOf("") }
    var lastCommitHex by remember { mutableStateOf<String?>(null) }
    var lastWelcomeHex by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackTopBar(stringResource(R.string.group_title), onBack)

        if (session == null) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.group_label_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { session = group.createGroup(label) }) {
                Text(stringResource(R.string.group_create_button))
            }
        } else {
            val s = session!!
            Text(s.label, fontWeight = FontWeight.Bold)

            Button(onClick = {
                group.pendingKeyPackageHex()?.let { clipboard.setText(AnnotatedString(it)) }
            }) {
                Text(stringResource(R.string.group_my_key_package_button))
            }

            OutlinedTextField(
                value = memberKeyPackageInput,
                onValueChange = { memberKeyPackageInput = it },
                label = { Text(stringResource(R.string.group_add_member_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val output = group.addMember(s, memberKeyPackageInput)
                lastCommitHex = output?.commitBytes?.joinToString("") { "%02x".format(it) }
                lastWelcomeHex = output?.welcomeBytes?.joinToString("") { "%02x".format(it) }
            }) {
                Text(stringResource(R.string.group_add_member_button))
            }
            lastCommitHex?.let { commit ->
                Text(stringResource(R.string.group_add_member_commit_label), fontWeight = FontWeight.Bold)
                Text(commit, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            lastWelcomeHex?.let { welcome ->
                Text(stringResource(R.string.group_add_member_welcome_label), fontWeight = FontWeight.Bold)
                Text(welcome, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }

            Button(onClick = { onGroupCreated(s) }) {
                Text(stringResource(R.string.chats_group_open_conversation))
            }
        }
    }
}
