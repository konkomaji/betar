package india.projectmesh.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarPolygonShapes

/**
 * DESIGN-BRIEF.md §9 screen 1: "Language picker. Large tiles, each in its own script, no
 * English gate." design/Betar Chats and Onboarding.dc.html's `scrLanguage()` lists five
 * languages; this project ships only English and Bengali (docs/LOCALIZATION-UX.md §1), so only
 * those two tiles exist here, not placeholders for Hindi/Assamese/Bodo.
 *
 * [onLanguageSelected] receives a BCP-47 tag ("en" or "bn"); the caller is responsible for
 * actually applying it (`AppCompatDelegate.setApplicationLocales` or the per-app-language
 * system settings), this screen only captures the choice.
 */
@Composable
fun LanguagePickerScreen(onLanguageSelected: (String) -> Unit) {
    var selected by remember { mutableStateOf("en") }
    val languages = listOf("en" to "English", "bn" to "বাংলা")

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(BetarPolygonShapes.cookie9).background(Color(0xFF4BA3E0)))
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        Text("Betar", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            languages.forEachIndexed { index, (tag, native) ->
                val isSelected = selected == tag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .selectable(selected = isSelected, onClick = { selected = tag })
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        native,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(BetarPolygonShapes.hexagon)
                            .background(if (isSelected) Color.White else Color.Transparent),
                    )
                }
            }
        }

        Button(onClick = { onLanguageSelected(selected) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}
