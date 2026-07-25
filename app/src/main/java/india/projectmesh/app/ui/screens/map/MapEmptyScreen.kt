package india.projectmesh.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarPolygonShapes

/**
 * DESIGN-BRIEF.md §9 screen 31: "No map data for this area, offering to receive it from a phone
 * nearby." [nearbySourceName] is whichever contact/peer the caller has determined actually holds
 * this area's tiles; there is no real map-transfer mechanism yet
 * (docs/IMPLEMENTATION-STATUS.md's "Offline maps" row has no tile-pack sideloading), so
 * [onTakeMap] is a callback the caller wires up once that transport exists. No fabricated file
 * size or transfer-time estimate is shown here (the design mockup shows "18 MB, about four
 * minutes" as an example; inventing a number with nothing real behind it would be presenting
 * fake precision as fact).
 */
@Composable
fun MapEmptyScreen(nearbySourceName: String, onTakeMap: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFFDCE9F5)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.map_empty_illustration_label), color = Color(0xFF12608F), style = MaterialTheme.typography.bodyMedium)
        }

        Text(stringResource(R.string.map_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.map_empty_body), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF2A3B47))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(BetarPolygonShapes.clover4)
                    .background(Color(0xFFDCE9F5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(nearbySourceName.take(1).uppercase(), color = Color(0xFF12608F), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(nearbySourceName, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.map_empty_source_has_data), style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = onTakeMap, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.map_empty_take_button, nearbySourceName))
        }
    }
}
