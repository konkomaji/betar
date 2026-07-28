package app.betar.comm.ui.screens.onboarding

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betar.comm.OemBatteryGuidance
import app.betar.comm.R
import app.betar.comm.ui.theme.BetarPolygonShapes

/**
 * DESIGN-BRIEF.md §9 screen 5: illustrated battery-exemption walkthrough, per manufacturer.
 * Reuses [OemBatteryGuidance] (already has the real vendor-specific intents for Xiaomi/Oppo/
 * Vivo/realme/Samsung/OnePlus) rather than reimplementing that lookup here. The three numbered
 * steps are generic ("Settings > Apps > Battery saver > No restrictions") because there is no
 * per-vendor screenshot asset pipeline this pass; [Build.MANUFACTURER] is shown so the heading
 * is at least honest about which device this is running on.
 */
@Composable
fun BatteryGuidanceScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.onboarding_battery_title), style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFFF6EBD2))
                .padding(16.dp),
        ) {
            Box(Modifier.size(24.dp).clip(BetarPolygonShapes.diamond).background(Color(0xFF9A6700)))
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.onboarding_battery_warning), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C3D00))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.onboarding_battery_manufacturer_heading, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }),
            style = MaterialTheme.typography.titleMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))

        listOf(R.string.onboarding_battery_step_1, R.string.onboarding_battery_step_2, R.string.onboarding_battery_step_3).forEachIndexed { i, res ->
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_battery_other_phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (!OemBatteryGuidance.openVendorAutostartSettings(context)) {
                        OemBatteryGuidance.requestIgnoreBatteryOptimizations(context)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.onboarding_battery_open_settings)) }
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.height(56.dp)) {
                Text(stringResource(R.string.onboarding_battery_later))
            }
        }
    }
}
