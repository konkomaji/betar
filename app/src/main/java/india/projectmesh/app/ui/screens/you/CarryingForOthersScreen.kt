package india.projectmesh.app.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R

/**
 * DESIGN-BRIEF.md §9 screen 36: "You are holding 47 messages for people around you," framed as
 * contribution.
 *
 * **Real gap, stated plainly:** there is no per-message "held for someone else" counter or a
 * configurable storage cap anywhere in the store-and-forward engine yet
 * (`core/`'s `RelayEngine`/envelope store has no such accounting exposed over the UniFFI
 * boundary). This screen is built for real, but the count and the storage control below are
 * UI-only placeholders until that counter exists, not a live figure.
 */
@Composable
fun CarryingForOthersScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.you_carrying_headline, 0),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.you_carrying_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.you_carrying_storage_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.you_carrying_storage_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
