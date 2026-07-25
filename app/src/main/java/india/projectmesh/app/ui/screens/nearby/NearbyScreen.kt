package india.projectmesh.app.ui.screens.nearby

import android.bluetooth.BluetoothManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.core.content.ContextCompat
import india.projectmesh.app.MeshApplication
import india.projectmesh.app.MeshRelayService
import india.projectmesh.app.R
import india.projectmesh.app.requiredMeshPermissions
import india.projectmesh.app.ui.components.TrustChip
import india.projectmesh.app.ui.components.TrustState
import india.projectmesh.app.ui.theme.BetarPolygonShapes
import kotlinx.coroutines.delay

/**
 * DESIGN-BRIEF.md §9 screens 18-20 ("Nearby"). Geometry and copy transcribed from
 * design/Betar Board Map and Nearby.dc.html's `scrNearbyEmpty()`/`scrDeviceSheet()` and
 * design/Betar Design System.dc.html's `nearbyDevices` sample row.
 *
 * **Real gap, stated plainly:** [MeshCoordinator][india.projectmesh.app.MeshCoordinator] only
 * exposes `connectedPeerCount(): Int`, there is no per-device name/signal/link-type/trust API
 * anywhere in the backend yet. [devices] below is real, populatable state (a proper
 * [NearbyDevice] list, not a placeholder type), and the populated-list card rendering is fully
 * built and correct, but nothing feeds it today, so the list is always empty and screen 18's
 * "one card per device" view is only reachable once that backend API exists. Until then this
 * screen always shows the empty state (19), which is itself real and not a stand-in.
 */
data class NearbyDevice(
    val id: String,
    val name: String,
    val link: String,
    val signalWord: String,
    val trust: TrustState,
    val signalBars: List<Float>,
)

@Composable
fun NearbyScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }
    val coordinator = remember { app.coordinator }

    // Real, populatable list state -- see the class doc above for why it never actually
    // receives entries yet.
    val devices = remember { mutableStateListOf<NearbyDevice>() }
    var selected by remember { mutableStateOf<NearbyDevice?>(null) }

    var relayOn by remember { mutableStateOf(coordinator.isRunning()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            relayOn = coordinator.isRunning()
            delay(1000)
        }
    }

    val permissionNeededMessage = stringResource(R.string.nearby_permission_needed)
    val enableBluetoothFirstMessage = stringResource(R.string.nearby_enable_bluetooth_first)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            ContextCompat.startForegroundService(context, MeshRelayService.startIntent(context))
        } else {
            statusMessage = permissionNeededMessage
        }
    }

    fun onRelayToggle(turnOn: Boolean) {
        if (turnOn) {
            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            if (bluetoothManager?.adapter?.isEnabled != true) {
                statusMessage = enableBluetoothFirstMessage
            } else {
                permissionLauncher.launch(requiredMeshPermissions())
            }
        } else {
            context.stopService(MeshRelayService.startIntent(context))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (devices.isEmpty()) {
            NearbyEmptyState(
                relayOn = relayOn,
                onRelayToggle = ::onRelayToggle,
                statusMessage = statusMessage,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    NearbyDeviceRow(device = device, onClick = { selected = device })
                }
            }
        }
    }

    selected?.let { device ->
        DeviceDetailSheet(device = device, onDismiss = { selected = null })
    }
}

@Composable
private fun NearbyEmptyState(relayOn: Boolean, onRelayToggle: (Boolean) -> Unit, statusMessage: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RippleHero()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.nearby_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.nearby_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nearby_carry_for_others_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    stringResource(R.string.nearby_carry_for_others_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Switch(checked = relayOn, onCheckedChange = onRelayToggle)
        }
    }
}

@Composable
private fun RippleHero() {
    Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(BetarPolygonShapes.cookie9)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NearbyDeviceRow(device: NearbyDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(BetarPolygonShapes.clover4)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(device.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("${device.link} · ${device.signalWord}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            TrustChip(state = device.trust)
        }
        Spacer(Modifier.size(8.dp))
        SignalBars(device.signalBars)
    }
}

@Composable
private fun SignalBars(levels: List<Float>) {
    Row(modifier = Modifier.height(34.dp), verticalAlignment = Alignment.Bottom) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .width(6.dp)
                    .height((34 * level).dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (level >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailSheet(device: NearbyDevice, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(BetarPolygonShapes.clover4)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(device.name.take(1), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(device.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.size(6.dp))
                    TrustChip(state = device.trust)
                }
            }
            Spacer(Modifier.size(16.dp))
            listOf(
                stringResource(R.string.nearby_detail_link_label) to device.link,
                stringResource(R.string.nearby_detail_signal_label) to device.signalWord,
            ).forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
