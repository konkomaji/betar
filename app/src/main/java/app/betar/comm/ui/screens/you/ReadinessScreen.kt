package app.betar.comm.ui.screens.you

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.betar.comm.OemBatteryGuidance
import app.betar.comm.R
import app.betar.comm.requiredMeshPermissions
import app.betar.comm.ui.theme.BetarPolygonShapes
import kotlinx.coroutines.delay

private data class ReadinessItem(val label: String, val ready: Boolean, val detail: String, val fix: (() -> Unit)?)

/** DESIGN-BRIEF.md §9 screen 35: "what somebody checks before a storm." Every check here reads
 * a real system/app state (Bluetooth adapter, granted permissions, battery exemption); nothing
 * is simulated. Layout transcribed from design/Betar Group and Settings.dc.html's
 * `scrReadiness()`. */
@Composable
fun ReadinessScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val bluetoothOn = remember(tick) {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }
    val meshPermissionsGranted = remember(tick) {
        requiredMeshPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    val batteryExempt = remember(tick) { OemBatteryGuidance.isIgnoringBatteryOptimizations(context) }
    val notificationsOn = remember(tick) {
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val items = listOf(
        // No programmatic Bluetooth-enable API without an extra permission dance on modern
        // Android, so this item's fix action is left null deliberately rather than faked.
        ReadinessItem(
            stringResource(R.string.you_readiness_bluetooth),
            bluetoothOn,
            if (bluetoothOn) stringResource(R.string.you_readiness_ready) else stringResource(R.string.you_readiness_bluetooth_off),
            fix = null,
        ),
        ReadinessItem(stringResource(R.string.you_readiness_permissions), meshPermissionsGranted, if (meshPermissionsGranted) stringResource(R.string.you_readiness_ready) else stringResource(R.string.you_readiness_permissions_missing)) {
            permissionLauncher.launch(requiredMeshPermissions())
        },
        ReadinessItem(stringResource(R.string.you_readiness_background), batteryExempt, if (batteryExempt) stringResource(R.string.you_readiness_ready) else stringResource(R.string.you_readiness_background_blocked)) {
            OemBatteryGuidance.requestIgnoreBatteryOptimizations(context)
        },
        ReadinessItem(stringResource(R.string.you_readiness_notifications), notificationsOn, if (notificationsOn) stringResource(R.string.you_readiness_ready) else stringResource(R.string.you_readiness_notifications_off)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
    val allReady = items.all { it.ready }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!allReady) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                ) {
                    Box(modifier = Modifier.size(24.dp).clip(BetarPolygonShapes.diamond).background(MaterialTheme.colorScheme.error))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(stringResource(R.string.you_readiness_needs_fixing), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(stringResource(R.string.you_readiness_check_before_storm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        items(items) { item -> ReadinessRow(item) }
    }
}

@Composable
private fun ReadinessRow(item: ReadinessItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(if (item.ready) BetarPolygonShapes.hexagon else BetarPolygonShapes.diamond)
                .background(if (item.ready) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFF9A6700)),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = if (item.ready) androidx.compose.ui.graphics.Color(0xFF1B5E20) else androidx.compose.ui.graphics.Color(0xFF7A5100))
        }
        if (!item.ready && item.fix != null) {
            Button(onClick = item.fix) { Text(stringResource(R.string.you_readiness_fix_button)) }
        }
    }
}
