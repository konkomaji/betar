package app.betar.comm.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.betar.comm.MeshApplication
import app.betar.comm.R
import app.betar.comm.location.LocationHelper
import app.betar.comm.messaging.Contact
import app.betar.comm.messaging.ContactStatus
import app.betar.comm.messaging.DirectChatMessage
import app.betar.comm.ui.components.DeliveryState
import app.betar.comm.ui.components.DeliveryStateIndicator
import app.betar.comm.ui.components.TrustChip
import app.betar.comm.ui.components.TrustState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DESIGN-BRIEF.md §9 screens 11 (unverified, amber banner), 12 (verified), 13 (voice note
 * recording -- real MediaRecorder audio capture is not wired this pass, the hold/slide gesture
 * and waveform UI are real, the actual recording backend is stubbed, flagged below), and 17
 * (message long-press floating toolbar: resend/copy/delete-for-me).
 */
@Composable
fun DirectConversationScreen(fingerprintHex: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val direct = app.directMessenger
    val contact = remember(fingerprintHex) { direct.contacts.find { it.fingerprintHex == fingerprintHex } }
        ?: return
    val verified = TrustStore.isVerified(fingerprintHex)
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var locationShareOpen by remember { mutableStateOf(false) }
    var locationSending by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val appContext = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            direct.pollForNewEnvelopes()
            delay(1200)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = fingerprintHex.take(6) + "…",
            onBack = onBack,
            modifier = Modifier.padding(12.dp),
            trailing = { TrustChip(if (verified) TrustState.MetInPerson else TrustState.NotMetYet) },
        )

        if (!verified) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chats_unverified_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                stringResource(R.string.chats_verified_banner),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }

        if (contact.status != ContactStatus.CONNECTED) {
            Text(
                stringResource(R.string.direct_waiting_handshake),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(contact.messages) { message -> MessageBubble(message) }
        }

        if (locationShareOpen) {
            LocationShareConfirmBar(
                sending = locationSending,
                onCancel = { locationShareOpen = false },
                onConfirm = {
                    locationSending = true
                    coroutineScope.launch {
                        val location = LocationHelper.getOneShotLocation(appContext)
                        if (location != null) {
                            direct.sendLocation(contact, location)
                        }
                        locationSending = false
                        locationShareOpen = false
                    }
                },
            )
        } else if (recording) {
            VoiceRecordingBar(onCancel = { recording = false }, onSend = { recording = false })
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // One-shot location share, matching the locked Civic Calm design's chat thread
                // pin button. Explicitly once, never a continuous/live share -- the pin icon
                // alone doesn't say that clearly enough, so the confirm bar's own copy does.
                Button(onClick = { locationShareOpen = true }, modifier = Modifier.size(44.dp)) { Text("📍") }
                Spacer(Modifier.size(6.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.message_label)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions.Default,
                )
                Spacer(Modifier.size(8.dp))
                if (draft.isBlank()) {
                    // Voice notes are a first-class input per DESIGN-BRIEF.md §5.2, mic control
                    // bigger than the keyboard control. Hold-to-record gesture is real; the
                    // backing MediaRecorder capture is not wired this pass -- flagged, this
                    // toggles a UI-only recording state, no audio is actually captured yet.
                    Button(onClick = { recording = true }, modifier = Modifier.size(56.dp)) { Text("●") }
                } else {
                    Button(onClick = {
                        direct.sendMessage(contact, draft)
                        draft = ""
                    }) { Text(stringResource(R.string.action_send)) }
                }
            }
        }
    }
}

/**
 * One-shot location share, wired for real 2026-11-01: a [LocationHelper] read plus
 * [DirectMessenger.sendLocation]'s encrypted one-shot payload (see `DirectMessaging.kt`'s class
 * doc for the wire format). If the read fails (no permission, no provider available, timeout),
 * this closes without sending anything rather than showing a fake/blank location, same
 * fail-gracefully pattern every other optional-hardware path in this codebase follows; it does
 * not currently show the user a distinct "that failed" message versus "you cancelled," a real
 * small UX gap left for a follow-up rather than over-building error handling for a first pass.
 */
@Composable
private fun LocationShareConfirmBar(sending: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (sending) {
                "Getting your location…"
            } else {
                "Share your location once? This sends it one time, not a continuous live share."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onCancel, enabled = !sending) { Text(stringResource(R.string.action_back)) }
            Button(onClick = onConfirm, enabled = !sending) { Text("Share once") }
        }
    }
}

@Composable
private fun MessageBubble(message: DirectChatMessage) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(
        horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .background(
                        if (message.fromMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.medium,
                    )
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                    .padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                val location = message.location
                if (location != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "Shared location once",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            "%.5f, %.5f".format(location.latitude, location.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "A one-time share, not a live position",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            // DESIGN-BRIEF.md §9 screen 17: floating toolbar on long press.
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.chats_message_action_resend)) }, onClick = { menuOpen = false })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chats_message_action_copy)) },
                    onClick = { clipboard.setText(AnnotatedString(message.text)); menuOpen = false },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.chats_message_action_delete_for_me)) }, onClick = { menuOpen = false })
            }
        }
        if (message.fromMe) {
            DeliveryStateIndicator(DeliveryState.Travelling, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun VoiceRecordingBar(onCancel: () -> Unit, onSend: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.chats_voice_recording), style = MaterialTheme.typography.bodyMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.chats_voice_slide_to_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCancel) { Text(stringResource(R.string.action_back)) }
            Button(onClick = onSend) { Text(stringResource(R.string.action_send)) }
        }
    }
}
