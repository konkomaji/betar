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
import kotlinx.coroutines.delay
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

/**
 * Phase 1 screen. Two independently-testable pieces:
 * - [IdentityScreen]: proves the UniFFI pipe end-to-end (`FfiIdentity.generate()`); verified
 *   working on an emulator (`docs/PROGRESS.md`).
 * - [MeshScreen]: drives the real BLE transport driver (`ble/BleTransportDriver.kt`) via
 *   [MeshApplication.coordinator]. Requests Bluetooth runtime permissions, starts/stops the
 *   mesh, shows connected-peer count, and offers a manual send/check pair (built on the already-
 *   exported `envelopePack`/`envelopeUnpack`/`composeLocal`/`containsHex`) as an
 *   application-level correctness signal beyond "link-layer bytes moved" -- see the plan doc's
 *   verification section.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        IdentityScreen()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        MeshScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun IdentityScreen() {
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var safetyString by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Project Mesh", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Phase 1 skeleton -- identity generation via the Rust core",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = {
            try {
                val identity = FfiIdentity.generate()
                fingerprint = identity.fingerprintHex()
                safetyString = identity.safetyString()
                error = null
            } catch (e: UnsatisfiedLinkError) {
                error = "Native library not loaded: ${e.message}"
            }
        }) {
            Text("Generate identity")
        }

        fingerprint?.let {
            Text("Fingerprint:")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        safetyString?.let {
            Text("Safety string:")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun MeshScreen() {
    val context = LocalContext.current
    val coordinator = remember { (context.applicationContext as MeshApplication).coordinator }

    var meshRunning by remember { mutableStateOf(false) }
    var connectedCount by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastEnvelopeIdHex by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            try {
                coordinator.start()
                meshRunning = true
                statusMessage = null
            } catch (e: Exception) {
                statusMessage = "Failed to start mesh: ${e.message}"
            }
        } else {
            statusMessage = "Bluetooth permissions denied -- mesh needs all of them to run"
        }
    }

    LaunchedEffect(meshRunning) {
        while (meshRunning) {
            connectedCount = coordinator.connectedPeerCount()
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mesh (BLE)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Real android.bluetooth.* driver -- advertise + scan, GATT server + client. " +
                "No foreground service yet, so this stops if the app leaves the foreground.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = {
            if (meshRunning) {
                coordinator.stop()
                meshRunning = false
                connectedCount = 0
            } else {
                val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                if (bluetoothManager?.adapter?.isEnabled != true) {
                    statusMessage = "Enable Bluetooth first"
                } else {
                    permissionLauncher.launch(requiredBluetoothPermissions())
                }
            }
        }) {
            Text(if (meshRunning) "Stop mesh" else "Start mesh")
        }

        if (meshRunning) {
            Text("Connected peers: $connectedCount")
        }
        statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = {
            try {
                val payload = "mesh-test-${System.currentTimeMillis()}".toByteArray()
                val expiresAt = (System.currentTimeMillis() / 1000L + 3600L).toULong()
                val bytes = envelopePack(
                    addressingTag = 0u, // Broadcast
                    addressingTarget = null,
                    priorityTag = 2u, // Normal
                    ttlHops = 8u,
                    expiresAt = expiresAt,
                    sealed = payload,
                )
                lastEnvelopeIdHex = envelopeUnpack(bytes).idHex
                checkResult = null
                coordinator.node().composeLocal(bytes, (System.currentTimeMillis() / 1000L).toULong())
                statusMessage = null
            } catch (e: Exception) {
                statusMessage = "compose_local failed: ${e.message}"
            }
        }) {
            Text("Send test broadcast")
        }

        lastEnvelopeIdHex?.let { idHex ->
            Text("Last envelope ID:")
            Text(idHex, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { checkResult = coordinator.node().containsHex(idHex) }) {
                Text("Check received (other device)")
            }
            checkResult?.let { Text("Store contains it: $it") }
        }
    }
}
