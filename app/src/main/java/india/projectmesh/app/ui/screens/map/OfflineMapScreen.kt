package india.projectmesh.app.ui.screens.map

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import india.projectmesh.app.R
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Same zero-network style as the top-level MapScreen.kt this replaces; see that file's doc
 *  comment for why a JSON literal instead of a style URL matters here. */
private const val OFFLINE_BLANK_STYLE = """
{
  "version": 8,
  "name": "Betar Offline Blank",
  "sources": {},
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#dde5d5" } }
  ]
}
"""

/**
 * DESIGN-BRIEF.md §9 screen 30: "Offline map with community pins." Reuses the same MapLibre
 * setup as the original MapScreen.kt (real GL surface, verified rendering, zero network calls,
 * see that file's doc comment).
 *
 * Pins are held in local, in-memory, this-device-only state ([MapPin]), positioned by where the
 * user actually tapped the map surface, not by any real latitude/longitude: there is no
 * geo-referenced tile data and no pin-over-mesh transport yet
 * (docs/IMPLEMENTATION-STATUS.md's "Offline maps" row), so nothing here is synced to other
 * devices or geographically real. Tapping the map surface drops a marker at the tap point
 * directly (a lightweight placeholder for the design's own drop-pin flow); the floating diamond
 * button opens the full category picker ([DropPinScreen]) instead, for a categorised pin.
 */
@Composable
fun OfflineMapScreen(
    onOpenDropPin: () -> Unit,
    pins: List<MapPin>,
    onQuickPinAt: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context)
        }
        DisposableEffect(mapView) {
            mapView.onCreate(Bundle())
            mapView.onStart()
            mapView.onResume()
            mapView.getMapAsync { map -> map.setStyle(Style.Builder().fromJson(OFFLINE_BLANK_STYLE)) }
            onDispose {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset -> onQuickPinAt(offset) })
                },
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            Text(
                stringResource(R.string.map_offline_tiles_label),
                color = Color(0xFF5A6B77),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(14.dp)
                    .background(Color.White.copy(alpha = .85f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            pins.forEach { pin ->
                Box(
                    modifier = Modifier
                        .pinOffset(pin.position)
                        .size(40.dp)
                        .clip(pin.category.shape)
                        .background(pin.category.color),
                )
            }
        }

        FloatingActionButton(
            onClick = onOpenDropPin,
            containerColor = Color(0xFF4BA3E0),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Box(modifier = Modifier.size(22.dp).clip(india.projectmesh.app.ui.theme.BetarPolygonShapes.diamond).background(Color(0xFF0B3A57)))
        }
    }
}

private fun Modifier.pinOffset(offset: Offset): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(offset.x.toInt() - placeable.width / 2, offset.y.toInt() - placeable.height / 2)
    }
}
