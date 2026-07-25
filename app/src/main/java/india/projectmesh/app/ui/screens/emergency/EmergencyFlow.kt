package india.projectmesh.app.ui.screens.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.messaging.SosAlert
import india.projectmesh.app.messaging.SosCategory
import india.projectmesh.app.messaging.SosMessenger
import india.projectmesh.app.ui.components.CategoryTile
import india.projectmesh.app.ui.theme.BetarCategoryShapes
import kotlinx.coroutines.delay

/**
 * DESIGN-BRIEF.md §9 "Emergency, the highest stakes flow in the app" (screens 25-29), state
 * machine and copy transcribed from design/Betar Design System.dc.html's `renderPrototype()`
 * (pick -> detail -> slide -> status), wired to the real [SosMessenger] instead of that file's
 * simulated `startTicker()` counters.
 *
 * DESIGN-BRIEF.md screen 25 calls the fourth category "Danger"; the shipped [SosCategory] enum
 * (wired to the wire protocol already) calls it VIOLENCE (`category_violence`), and this reuses
 * that existing label/shape slot rather than inventing a second name for the same category.
 *
 * **Real gap, not glossed over:** the design file's live-status screen shows a fake
 * "carried by N phones" counter driven by a UI-only timer. RelayEngine does not expose any
 * carrier/hop telemetry for a specific envelope yet, so this build shows only what is actually
 * known: whether the alert has been acknowledged ([SosMessenger.acknowledgedIdHexes], a real
 * boolean, not a count) and [india.projectmesh.app.MeshCoordinator.connectedPeerCount] as an
 * honest, clearly-labelled proxy for "phones your device can currently reach", not a claim that
 * this specific alert reached them.
 */
private enum class EmergencyStep { Pick, Detail, Slide, Status }

private val EmergencyRed = Color(0xFFC8102E)
private val EmergencyRedDark = Color(0xFF8C0B20)
private val EmergencyContainer = Color(0xFFF3D8DC)
private val OnEmergencyContainer = Color(0xFF6B3037)

private fun shapeFor(category: SosCategory): Shape = when (category) {
    SosCategory.MEDICAL -> BetarCategoryShapes.medical
    SosCategory.TRAPPED -> BetarCategoryShapes.trapped
    SosCategory.FIRE -> BetarCategoryShapes.fire
    SosCategory.VIOLENCE -> BetarCategoryShapes.danger
    SosCategory.OTHER -> BetarCategoryShapes.other
}

@Composable
fun EmergencyFlow(messenger: SosMessenger, connectedPeerCount: () -> Int, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(EmergencyStep.Pick) }
    var category by remember { mutableStateOf<SosCategory?>(null) }
    var shareLocation by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sentAlert by remember { mutableStateOf<SosAlert?>(null) }
    var resolved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (step) {
            EmergencyStep.Pick -> EmergencyPickScreen(
                onClose = onDismiss,
                onPick = { category = it; step = EmergencyStep.Detail },
            )
            EmergencyStep.Detail -> {
                val cat = category ?: return@Column
                EmergencyDetailScreen(
                    category = cat,
                    shareLocation = shareLocation,
                    onShareLocationChange = { shareLocation = it },
                    text = text,
                    onTextChange = { text = it },
                    onBack = { step = EmergencyStep.Pick },
                    onContinue = { step = EmergencyStep.Slide },
                )
            }
            EmergencyStep.Slide -> {
                val cat = category ?: return@Column
                EmergencySlideScreen(
                    category = cat,
                    shareLocation = shareLocation,
                    onBack = { step = EmergencyStep.Detail },
                    onSent = {
                        messenger.send(cat, text)
                        sentAlert = messenger.alerts.firstOrNull { it.category == cat && it.text == text }
                        step = EmergencyStep.Status
                    },
                )
            }
            EmergencyStep.Status -> {
                val cat = category ?: return@Column
                EmergencyLiveStatusScreen(
                    category = cat,
                    alert = sentAlert,
                    messenger = messenger,
                    connectedPeerCount = connectedPeerCount,
                    resolved = resolved,
                    onResolve = { resolved = true },
                    onCancel = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun EmergencyHeader(title: String, onClose: (() -> Unit)?, onBack: (() -> Unit)?) {
    Row(Modifier.height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val action = onBack ?: onClose
        if (action != null) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(EmergencyContainer), contentAlignment = Alignment.Center) {
                IconButton(onClick = action) {
                    Text(if (onBack != null) "←" else "✕", color = EmergencyRedDark, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(12.dp))
        }
        Text(title, color = EmergencyRedDark, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EmergencyPickScreen(onClose: () -> Unit, onPick: (SosCategory) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EmergencyHeader(title = stringResource(R.string.emergency_what_is_happening), onClose = onClose, onBack = null)
        Text(
            stringResource(R.string.emergency_press_hold_hint),
            color = OnEmergencyContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(SosCategory.entries) { c ->
                CategoryTile(
                    label = stringResource(c.labelRes),
                    shape = shapeFor(c),
                    selected = false,
                    available = true,
                    onClick = { onPick(c) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp).clip(MaterialTheme.shapes.large).background(EmergencyContainer).padding(16.dp)) {
            Text(stringResource(R.string.emergency_travel_note), color = OnEmergencyContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmergencyDetailScreen(
    category: SosCategory,
    shareLocation: Boolean,
    onShareLocationChange: (Boolean) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        EmergencyHeader(title = stringResource(category.labelRes), onClose = null, onBack = onBack)
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.emergency_share_location_title), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.emergency_share_location_off_by_default), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = shareLocation, onCheckedChange = onShareLocationChange)
                }
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                    Text(
                        stringResource(if (shareLocation) R.string.emergency_share_location_on_note else R.string.emergency_share_location_off_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // Voice note recording is a real, separate feature this app does not implement yet
            // anywhere (no audio capture pipeline exists) -- shown as a disabled affordance
            // rather than silently pretending to record.
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(EmergencyRed), contentAlignment = Alignment.Center) {
                    Text("●", color = Color.White)
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(stringResource(R.string.emergency_say_whats_wrong), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.emergency_voice_not_wired_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.emergency_add_words_optional)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
        ) { Text(stringResource(R.string.emergency_continue), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun EmergencySlideScreen(category: SosCategory, shareLocation: Boolean, onBack: () -> Unit, onSent: () -> Unit) {
    var dragPx by remember { mutableStateOf(0f) }
    val trackWidthPx = remember { mutableStateOf(1f) }
    val progress = (dragPx / trackWidthPx.value).coerceIn(0f, 1f)
    val onSentState = rememberUpdatedState(onSent)
    val shape = shapeFor(category)

    Column(Modifier.fillMaxSize()) {
        EmergencyHeader(title = stringResource(category.labelRes), onClose = null, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(150.dp).clip(shape).background(EmergencyRed))
            Spacer(Modifier.size(20.dp))
            Text(stringResource(R.string.emergency_send_to_everyone), textAlign = TextAlign.Center, color = EmergencyRedDark, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(if (shareLocation) R.string.emergency_location_included else R.string.emergency_location_not_included),
                textAlign = TextAlign.Center,
                color = OnEmergencyContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(EmergencyContainer)
                    .pointerInput(Unit) {
                        trackWidthPx.value = size.width.toFloat() - with(density) { 72.dp.toPx() }
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragPx = (dragPx + dragAmount.x).coerceIn(0f, trackWidthPx.value)
                            },
                            onDragEnd = {
                                if (progress > 0.92f) onSentState.value() else dragPx = 0f
                            },
                        )
                    },
            ) {
                Text(
                    stringResource(R.string.emergency_slide_to_send),
                    modifier = Modifier.align(Alignment.Center),
                    color = EmergencyRedDark,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(64.dp)
                        .offset { IntOffset(dragPx.toInt(), 0) }
                        .clip(MaterialTheme.shapes.large)
                        .background(EmergencyRed),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("→", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(
                stringResource(R.string.emergency_slide_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
                color = OnEmergencyContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmergencyLiveStatusScreen(
    category: SosCategory,
    alert: SosAlert?,
    messenger: SosMessenger,
    connectedPeerCount: () -> Int,
    resolved: Boolean,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }
    var peers by remember { mutableStateOf(connectedPeerCount()) }
    val shape = shapeFor(category)

    LaunchedEffect(alert) {
        while (!resolved) {
            messenger.pollForNewAlerts()
            alert?.let { acknowledged = messenger.acknowledgedIdHexes.contains(it.idHex) }
            peers = connectedPeerCount()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (resolved) R.string.emergency_marked_resolved else R.string.emergency_alert_travelling),
                color = EmergencyRedDark,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surface).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(104.dp).clip(shape).background(if (resolved) Color(0xFF2E7D32) else EmergencyRed), contentAlignment = Alignment.Center) {
                    Text("$peers", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displaySmall)
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    if (peers == 0) stringResource(R.string.emergency_still_looking) else stringResource(R.string.emergency_connected_to_n_phones, peers),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(if (acknowledged) R.string.emergency_acknowledged else R.string.emergency_not_acknowledged),
                    color = if (acknowledged) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(EmergencyContainer).padding(16.dp)) {
                Text(stringResource(R.string.emergency_disclaimer_note), color = OnEmergencyContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { if (!resolved) { alert?.let(messenger::acknowledge); onResolve() } },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (resolved) Color(0xFFDCEEDC) else Color(0xFF2E7D32),
                    contentColor = if (resolved) Color(0xFF1B5E20) else Color.White,
                ),
            ) { Text(stringResource(if (resolved) R.string.emergency_resolved else R.string.emergency_i_am_safe), fontWeight = FontWeight.ExtraBold) }
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = EmergencyRedDark),
            ) { Text(stringResource(R.string.emergency_cancel_alert), fontWeight = FontWeight.Bold) }
        }
    }
}
