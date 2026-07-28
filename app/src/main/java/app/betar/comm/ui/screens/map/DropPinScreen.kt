package app.betar.comm.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import app.betar.comm.R
import app.betar.comm.ui.components.CategoryTile

/**
 * DESIGN-BRIEF.md §9 screen 32: "Drop a pin, with the category grid." Category set and shapes
 * documented in [MapPinCategory]. This screen only picks the category -- the coordinate is
 * decided by the caller before it navigates here (either the map-tap point or the current camera
 * centre, see [OfflineMapScreen]'s doc comment) and carried across the navigation, not something
 * this screen touches itself.
 */
@Composable
fun DropPinScreen(onConfirm: (MapPinCategory) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<MapPinCategory?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.map_drop_pin_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.map_drop_pin_category_label), style = MaterialTheme.typography.bodyMedium)

        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), modifier = Modifier.weight(1f, fill = false)) {
            items(MapPinCategory.entries) { category ->
                CategoryTile(
                    label = stringResource(category.labelRes),
                    shape = category.shape,
                    selected = selected == category,
                    available = true,
                    onClick = { selected = category },
                    modifier = Modifier.padding(6.dp),
                )
            }
        }

        Button(
            onClick = { selected?.let(onConfirm) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(stringResource(R.string.map_drop_pin_button))
        }
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}
