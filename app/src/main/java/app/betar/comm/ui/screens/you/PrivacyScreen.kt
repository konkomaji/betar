package app.betar.comm.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_DURATION_MS = 3000

/**
 * DESIGN-BRIEF.md §9 screen 38: panic wipe with hold-to-confirm, decoy passphrase setup.
 * Layout/copy transcribed from design/Betar Group and Settings.dc.html's `scrPrivacy()`.
 *
 * **Real gap, stated plainly:** there is no actual "erase everything" implementation wired up
 * here (no call into `KeystoreIdentityStore`/`KeystoreMasterKey`/message stores to actually
 * delete anything), and no decoy-passphrase mechanism exists in the backend. The hold-to-confirm
 * gesture itself is real and functions correctly (a genuine 3-second timed press, cancels
 * cleanly on early release), but the action it triggers is UI-only until real wipe/decoy
 * primitives exist.
 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    var wiped by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.you_privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.size(16.dp))
        Text(stringResource(R.string.you_privacy_decoy_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.you_privacy_decoy_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(24.dp))
        Text(
            if (wiped) stringResource(R.string.you_privacy_wiped_title) else stringResource(R.string.you_privacy_erase_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (wiped) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            if (wiped) stringResource(R.string.you_privacy_wiped_body) else stringResource(R.string.you_privacy_erase_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.large)
                .background(if (wiped) Color(0xFFDCEEDC) else Color(0xFFF3D8DC))
                .pointerInput(wiped) {
                    if (wiped) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            holdJob?.cancel()
                            holdJob = scope.launch {
                                val steps = 30
                                for (i in 1..steps) {
                                    delay((HOLD_DURATION_MS / steps).toLong())
                                    holdProgress = i / steps.toFloat()
                                }
                                wiped = true
                                holdProgress = 0f
                            }
                            tryAwaitRelease()
                            holdJob?.cancel()
                            holdProgress = 0f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (holdProgress > 0f) holdProgress else 0.0001f)
                    .background(Color(0xFFC8102E).copy(alpha = 0.9f)),
            )
            Text(
                when {
                    wiped -> stringResource(R.string.you_privacy_wipe_done)
                    holdProgress > 0f -> stringResource(R.string.you_privacy_keep_holding)
                    else -> stringResource(R.string.you_privacy_hold_to_erase)
                },
                fontWeight = FontWeight.ExtraBold,
                color = if (wiped) Color(0xFF1B5E20) else if (holdProgress > 0.35f) Color.White else Color(0xFF8C0B20),
            )
        }
        if (!wiped) {
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.you_privacy_hold_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
