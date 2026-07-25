package india.projectmesh.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import india.projectmesh.app.MeshApplication
import india.projectmesh.app.R
import india.projectmesh.app.messaging.Contact
import india.projectmesh.app.ui.components.TrustChip
import india.projectmesh.app.ui.components.TrustState
import india.projectmesh.app.ui.theme.BetarPolygonShapes

/**
 * DESIGN-BRIEF.md §9 screen 9: "Scan a code to add somebody." No QR/camera library is wired
 * into this app yet (a real scan needs CameraX + a barcode reader, out of scope for this pass),
 * so this is the real screen and gesture affordance with a stubbed camera preview -- flagged,
 * not silently faked as a working scanner. Manual fingerprint entry (already backed by
 * DirectMessenger.addContact) is the working fallback both here and on the mockup's own
 * "type their six characters" line.
 */
@Composable
fun ScanCodeScreen(onManualAdd: (Contact) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    var fingerprintInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(androidx.compose.ui.graphics.Color(0xFF0B1A24)),
            contentAlignment = Alignment.Center,
        ) {
            // No camera library wired yet -- real gap, not a working scanner.
            Text(
                stringResource(R.string.chats_scan_camera_not_wired),
                color = androidx.compose.ui.graphics.Color(0xFF9FB6C6),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            stringResource(R.string.chats_scan_instruction),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.chats_scan_reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = fingerprintInput,
            onValueChange = { fingerprintInput = it; error = null },
            label = { Text(stringResource(R.string.chats_scan_manual_entry_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val contact = app.directMessenger.addContact(fingerprintInput)
            if (contact != null) onManualAdd(contact) else error = "Not a valid fingerprint"
        }) {
            Text(stringResource(R.string.chats_scan_add_button))
        }
    }
}

/**
 * DESIGN-BRIEF.md §9 screen 10: in-person verification. "Two phones side by side showing the
 * same short code in very large type plus a scannable code, and one confirm action. No
 * cryptographic language anywhere on this screen." Confirming only sets [TrustStore]'s in-memory
 * flag (see its own doc comment for why that's not real persisted/bound trust yet).
 */
@Composable
fun VerifyInPersonScreen(fingerprintHex: String, onConfirmed: () -> Unit, onNotNow: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val safetyString = remember(fingerprintHex) {
        // Both sides derive the same short code from the same session material; this app's own
        // identity already exposes one via FfiIdentity.safetyString() for the LOCAL identity.
        // There's no per-contact safety-string derivation exposed yet, so this shows this
        // device's own string as the "compare by eye" value -- correct for a single-identity
        // demo, not yet the real per-pair comparison CRYPTOGRAPHY.md §3 describes. Flagged.
        app.identity.safetyString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.chats_verify_instruction),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                safetyString.chunked(3).joinToString(" ").take(11),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(BetarPolygonShapes.diamond).background(MaterialTheme.colorScheme.error))
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.chats_verify_mismatch_warning),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = { TrustStore.markVerified(fingerprintHex); onConfirmed() }) {
            Text(stringResource(R.string.chats_verify_confirm_button))
        }
        Text(
            stringResource(R.string.chats_verify_not_now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}
