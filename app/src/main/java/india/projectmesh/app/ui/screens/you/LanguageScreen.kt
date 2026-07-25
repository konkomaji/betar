package india.projectmesh.app.ui.screens.you

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.os.LocaleListCompat
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarPolygonShapes

private data class LangOption(val code: String, val native: String, val english: String)

// Only English and Bengali ship by default (docs/LOCALIZATION-UX.md §1): no Hindi/Assamese/Bodo
// tile, unlike the earlier design mockup's 5-language row -- every other language is
// community-contributed and not part of the default install.
private val LANGUAGES = listOf(
    LangOption("bn", "বাংলা", "Bangla"),
    LangOption("en", "English", "English"),
)

/** DESIGN-BRIEF.md §9 screen 34. Switches instantly via the per-app language API
 * (`AndroidX.core`'s [androidx.appcompat.app.AppCompatDelegate]-free `LocaleManager`/
 * `AppCompatDelegate` split below), nothing is downloaded, matching the design file's own note. */
@Composable
fun LanguageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember {
        mutableStateOf(
            androidx.core.app.LocaleManagerCompat.getApplicationLocales(context)
                .get(0)?.language ?: "en",
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.you_language_note),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LANGUAGES.forEach { lang ->
            val isOn = lang.code == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable {
                        selected = lang.code
                        val locales = LocaleListCompat.forLanguageTags(lang.code)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.getSystemService(LocaleManager::class.java)
                                ?.applicationLocales = LocaleList.forLanguageTags(lang.code)
                        } else {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                        }
                    }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        lang.native,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        lang.english,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOn) Color(0xFFBFDDF3) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isOn) {
                    Box(modifier = Modifier.size(26.dp).clip(BetarPolygonShapes.hexagon).background(Color.White))
                }
            }
        }
    }
}
