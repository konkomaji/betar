package india.projectmesh.app.ui.screens.chats

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import india.projectmesh.app.MeshApplication
import india.projectmesh.app.R
import india.projectmesh.app.messaging.Contact
import india.projectmesh.app.ui.components.QrAnalyzer
import india.projectmesh.app.ui.components.generateQrBitmap
import india.projectmesh.app.ui.components.SafetyCode
import india.projectmesh.app.ui.components.TrustChip
import india.projectmesh.app.ui.components.TrustState
import india.projectmesh.app.ui.theme.BetarPolygonShapes
import java.util.concurrent.Executors

/**
 * DESIGN-BRIEF.md §9 screen 9: "Scan a code to add somebody." Real camera preview + live QR
 * decode now wired (CameraX + ZXing, see QrCode.kt) -- was a stubbed placeholder box before this
 * pass. Camera permission is requested contextually here, not folded into the onboarding
 * permission set, since it's a single, occasional-use need. Manual fingerprint entry stays the
 * always-available fallback, both for a denied permission and for the "type their six characters"
 * path the mockup itself lists as an alternative.
 */
@Composable
fun ScanCodeScreen(onManualAdd: (Contact) -> Unit, onShowMyCode: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshApplication
    var fingerprintInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraDeniedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (!granted) cameraDeniedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun tryAdd(hex: String) {
        val contact = app.directMessenger.addContact(hex)
        if (contact != null) onManualAdd(contact) else error = "Not a valid fingerprint"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackTopBar(stringResource(R.string.chats_new_scan_code), onBack)
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Color(0xFF0B1A24)),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraGranted) {
                QrScannerPreview(onDecoded = { text -> tryAdd(text.trim()) })
            } else {
                Text(
                    stringResource(R.string.chats_scan_camera_not_wired),
                    color = Color(0xFF9FB6C6),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
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
        if (cameraDeniedOnce && !cameraGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFFF6EBD2))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chats_scan_camera_denied),
                    color = Color(0xFF5C3D00),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        TextButton(onClick = onShowMyCode) {
            Text(stringResource(R.string.chats_scan_show_my_code))
        }
        OutlinedTextField(
            value = fingerprintInput,
            onValueChange = { fingerprintInput = it; error = null },
            label = { Text(stringResource(R.string.chats_scan_manual_entry_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { tryAdd(fingerprintInput) }) {
            Text(stringResource(R.string.chats_scan_add_button))
        }
    }
}

/** Live CameraX preview with a ZXing [QrAnalyzer] bound to it; calls [onDecoded] once and then
 * stops (the caller navigates away on success, so a second decode is never needed). */
@Composable
private fun QrScannerPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor, QrAnalyzer(onDecoded))
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
            )
        } catch (_: Exception) {
            // Camera bind can fail if the device has no usable back camera; the amber
            // fallback card plus manual entry field on this screen still cover that case.
        }
    }
}

/**
 * DESIGN-BRIEF.md §9 screen 10: in-person verification. "Two phones side by side showing the
 * same short code in very large type plus a scannable code, and one confirm action. No
 * cryptographic language anywhere on this screen." Confirming only sets [TrustStore]'s in-memory
 * flag (see its own doc comment for why that's not real persisted/bound trust yet).
 *
 * Code shown is [SafetyCode], derived from [fingerprintHex] (the contact being verified, not this
 * device's own identity -- an earlier pass showed the wrong side's value here since no per-contact
 * derivation existed yet; fixed now that the derivation lives client-side and can take either
 * fingerprint). Both people compare the 3 emoji: this device computed them independently from the
 * fingerprint it received, the other person's device computes the same 3 emoji from its own
 * identity bytes on [ShowMyCodeScreen] -- a match only happens if both sides really hold the same
 * key material, catching a substituted identity the same way a Signal-style safety number would,
 * in a friendlier compare-by-eye form.
 */
@Composable
fun VerifyInPersonScreen(fingerprintHex: String, onConfirmed: () -> Unit, onNotNow: () -> Unit) {
    val sixDigit = remember(fingerprintHex) { SafetyCode.sixDigitCode(fingerprintHex) }
    val emoji = remember(fingerprintHex) { SafetyCode.threeEmoji(fingerprintHex) }
    val qrBitmap = remember(fingerprintHex) { generateQrBitmap(fingerprintHex, sizePx = 256) }

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
                sixDigit,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                emoji.joinToString("  "),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics {
                    contentDescription = "Compare these three: ${emoji.joinToString(", ")}"
                },
            )
            Image(
                bitmap = qrBitmap,
                contentDescription = stringResource(R.string.chats_verify_qr_description),
                modifier = Modifier
                    .size(160.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White)
                    .padding(8.dp),
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
            modifier = Modifier.padding(8.dp).clickable(onClick = onNotNow),
        )
    }
}

/**
 * DESIGN-BRIEF.md §9 screen 9 ALT: "Show my code instead" -- the other half of scan-to-add. One
 * device shows this, the other scans it via [ScanCodeScreen]. New this pass: previously nothing
 * rendered the local identity as a scannable code at all, so "scan a code" had nothing to scan.
 */
@Composable
fun ShowMyCodeScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeshApplication
    val fingerprintHex = remember { app.identity.fingerprintHex() }
    val qrBitmap = remember { generateQrBitmap(fingerprintHex, sizePx = 512) }
    val sixDigit = remember { SafetyCode.sixDigitCode(fingerprintHex) }
    val emoji = remember { SafetyCode.threeEmoji(fingerprintHex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackTopBar(stringResource(R.string.chats_show_my_code_title), onBack)
        Text(
            stringResource(R.string.chats_show_my_code_instruction),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Image(
            bitmap = qrBitmap,
            contentDescription = stringResource(R.string.chats_verify_qr_description),
            modifier = Modifier
                .size(260.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Color.White)
                .padding(16.dp),
        )
        Text(
            sixDigit,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            emoji.joinToString("  "),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics {
                contentDescription = "Your three: ${emoji.joinToString(", ")}"
            },
        )
        Text(
            stringResource(R.string.chats_show_my_code_reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
