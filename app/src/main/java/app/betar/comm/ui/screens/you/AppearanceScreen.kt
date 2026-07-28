package app.betar.comm.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.R
import app.betar.comm.ui.theme.AppearancePrefs
import app.betar.comm.ui.theme.BetarPolygonShapes
import app.betar.comm.ui.theme.ThemeMode

/**
 * DESIGN-BRIEF.md §9 screen 37: light, dark, follow system, plus the sunlight/high-contrast
 * toggle. Reads/writes [AppearancePrefs], the app-wide state MainActivity's root composable
 * feeds into [app.betar.comm.ui.theme.BetarTheme]'s `darkTheme`/`highContrast` params.
 */
@Composable
fun AppearanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = AppearancePrefs.themeMode

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeOption(stringResource(R.string.you_appearance_light), BetarPolygonShapes.cookie9, current == ThemeMode.Light, Modifier.weight(1f)) {
                AppearancePrefs.setThemeMode(context, ThemeMode.Light)
            }
            ThemeOption(stringResource(R.string.you_appearance_dark), BetarPolygonShapes.cookie9, current == ThemeMode.Dark, Modifier.weight(1f)) {
                AppearancePrefs.setThemeMode(context, ThemeMode.Dark)
            }
            ThemeOption(stringResource(R.string.you_appearance_follow_system), BetarPolygonShapes.cookie9, current == ThemeMode.FollowSystem, Modifier.weight(1f)) {
                AppearancePrefs.setThemeMode(context, ThemeMode.FollowSystem)
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.you_appearance_sunlight_title), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.you_appearance_sunlight_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = AppearancePrefs.sunlightMode, onCheckedChange = { AppearancePrefs.setSunlightMode(context, it) })
        }
    }
}

@Composable
private fun ThemeOption(label: String, shape: androidx.compose.ui.graphics.Shape, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(34.dp).clip(shape).background(if (selected) Color.White else MaterialTheme.colorScheme.primaryContainer))
        Spacer(Modifier.size(10.dp))
        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
