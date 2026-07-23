package india.projectmesh.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import india.projectmesh.app.messaging.BulletinScreen
import india.projectmesh.app.messaging.MessagingScreen
import india.projectmesh.app.messaging.ResourceScreen
import india.projectmesh.app.messaging.SosScreen
import kotlinx.coroutines.delay

/**
 * Phase 1 screen, independently-testable pieces:
 * - [IdentityScreen]: displays the app's one stable session identity ([MeshApplication.identity])
 *   — proves the UniFFI pipe end-to-end and is what [india.projectmesh.app.messaging.DirectMessenger]
 *   actually addresses Direct messages with (not a throwaway demo identity anymore).
 * - [MeshScreen]: drives the real BLE transport driver (`ble/BleTransportDriver.kt`) via
 *   [MeshApplication.coordinator], now running inside [MeshRelayService] so it survives
 *   backgrounding. Requests Bluetooth runtime permissions, starts/stops the mesh, shows
 *   connected-peer count, and OEM battery-whitelist guidance.
 * - [MessagingScreen]: real Direct + Broadcast messaging UI (Channel/Group deferred — see its
 *   own doc comment for why).
 * - [SosScreen]/[BulletinScreen]/[ResourceScreen]: the three civic-broadcast features
 *   (`FEATURES.md` §1/§2/§4) — see `messaging/CivicPost.kt` for their shared wire framing.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        val app = LocalContext.current.applicationContext as MeshApplication
                        IdentityScreen()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        MeshScreen()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        MessagingScreen()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        SosScreen(app.sosMessenger)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        BulletinScreen(app.bulletinMessenger)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        ResourceScreen(app.resourceMessenger)
                    }
                }
            }
        }
    }
}

@Composable
fun IdentityScreen() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }
    var shown by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Project Mesh", style = MaterialTheme.typography.headlineMedium)
        Text(
            "One stable identity per app session (not persisted across restarts yet).",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = {
            try {
                shown = true
                error = null
            } catch (e: UnsatisfiedLinkError) {
                error = "Native library not loaded: ${e.message}"
            }
        }) {
            Text("Show my identity")
        }

        if (shown) {
            Text("Fingerprint:")
            Text(app.identity.fingerprintHex(), style = MaterialTheme.typography.bodySmall)
            Text("Safety string:")
            Text(app.identity.safetyString(), style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun requiredMeshPermissions(): Array<String> {
    val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // The foreground service's persistent notification needs this at runtime on API 33+.
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    return bluetooth + notifications
}

@Composable
fun MeshScreen() {
    val context = LocalContext.current
    val coordinator = remember { (context.applicationContext as MeshApplication).coordinator }

    var meshRunning by remember { mutableStateOf(coordinator.isRunning()) }
    var connectedCount by remember { mutableStateOf(0) }
    var batteryExempt by remember { mutableStateOf(OemBatteryGuidance.isIgnoringBatteryOptimizations(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            ContextCompat.startForegroundService(context, MeshRelayService.startIntent(context))
            statusMessage = null
        } else {
            statusMessage = "Permissions denied -- mesh needs all of them to run"
        }
    }

    // Single source of truth for on-screen state: the foreground service (MeshRelayService) is
    // what actually starts/stops the mesh now, and it can be started/stopped/restarted (STICKY)
    // independently of this composable's lifecycle -- polling coordinator.isRunning() rather
    // than tracking a locally-set flag keeps the UI honest about what's actually running.
    LaunchedEffect(Unit) {
        while (true) {
            meshRunning = coordinator.isRunning()
            connectedCount = if (meshRunning) coordinator.connectedPeerCount() else 0
            batteryExempt = OemBatteryGuidance.isIgnoringBatteryOptimizations(context)
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mesh (BLE)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Real android.bluetooth.* driver -- advertise + scan, GATT server + client, " +
                "running in a foreground service so it keeps working when backgrounded.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = {
            if (meshRunning) {
                context.stopService(MeshRelayService.startIntent(context))
            } else {
                val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                if (bluetoothManager?.adapter?.isEnabled != true) {
                    statusMessage = "Enable Bluetooth first"
                } else {
                    permissionLauncher.launch(requiredMeshPermissions())
                }
            }
        }) {
            Text(if (meshRunning) "Stop mesh" else "Start mesh")
        }

        if (meshRunning) {
            Text("Connected peers: $connectedCount")
        }
        statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!batteryExempt) {
            Text(
                "For reliable background relay, exempt this app from battery optimization. " +
                    "Some devices (Xiaomi, Oppo, Vivo, realme, Samsung...) also need a separate " +
                    "\"autostart\"/\"protected apps\" setting -- see docs/TRANSPORT.md §6.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { OemBatteryGuidance.requestIgnoreBatteryOptimizations(context) }) {
                Text("Allow background activity")
            }
            Button(onClick = {
                if (!OemBatteryGuidance.openVendorAutostartSettings(context)) {
                    statusMessage = "No known autostart setting for ${Build.MANUFACTURER} -- check device settings manually"
                }
            }) {
                Text("Open device autostart settings")
            }
        }
    }
}
