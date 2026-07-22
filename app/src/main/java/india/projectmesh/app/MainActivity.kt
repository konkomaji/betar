package india.projectmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uniffi.mesh_core.FfiIdentity

/**
 * Phase 1 skeleton screen. Proves the UniFFI pipe end-to-end: generates an identity in the
 * Rust core (`core/src/ffi.rs`) and displays its fingerprint from Kotlin. Not wired to any
 * transport, persistence, or the rest of the app yet -- see docs/IMPLEMENTATION-STATUS.md.
 *
 * **Known gap:** this will throw `UnsatisfiedLinkError` at runtime until `mesh-core` is
 * cross-compiled for an Android target (arm64-v8a etc.) and the resulting `libmesh_core.so`
 * is placed under `app/src/main/jniLibs/<abi>/`. No Android NDK is available in this repo's
 * current dev environment, so that cross-compile has not been done -- see
 * `android/app/src/main/jniLibs/README.md`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IdentityScreen()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
