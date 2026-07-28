package app.betar.comm.ui.screens.map

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.betar.comm.R
import app.betar.comm.messaging.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
 * Pins ([MapPin]) carry a real WGS84 [GeoPoint] now, not a screen offset -- see [MapPin]'s own
 * doc comment for why. [OFFLINE_BLANK_STYLE] has no imagery, but the MapLibre surface still has a
 * real camera (lat/lon/zoom), so tapping converts the tap point through [MapLibreMap.getProjection]
 * to a real coordinate, and pins are drawn back at [MapLibreMap.getProjection]'s screen position
 * for the current camera -- both directions go through the same projection, so pins stay under
 * the tap point across pan/zoom, and (once synced) line up the same way on every device.
 * [cameraTick] forces recomposition of the pin overlay on every camera move; MapLibre's camera
 * lives outside Compose's snapshot system so nothing else would trigger a redraw of stale screen
 * positions. Tapping the map surface drops a marker at the tap point directly (a lightweight
 * placeholder for the design's own drop-pin flow, routed through the same category picker,
 * [DropPinScreen], as the floating diamond button); the diamond button instead defaults to the
 * current camera centre, for when there's nothing worth tapping precisely.
 */
@Composable
fun OfflineMapScreen(
    onOpenDropPin: (GeoPoint) -> Unit,
    pins: List<MapPin>,
    onQuickPinAt: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context)
        }
        DisposableEffect(mapView) {
            mapView.onCreate(Bundle())
            mapView.onStart()
            mapView.onResume()
            mapView.getMapAsync { loadedMap ->
                loadedMap.setStyle(Style.Builder().fromJson(OFFLINE_BLANK_STYLE))
                loadedMap.addOnCameraIdleListener { cameraTick++ }
                map = loadedMap
            }
            onDispose {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(map) {
                    val currentMap = map ?: return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        val latLng = currentMap.projection.fromScreenLocation(PointF(offset.x, offset.y))
                        onQuickPinAt(GeoPoint(latLng.latitude, latLng.longitude))
                    })
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

            val currentMap = map
            if (currentMap != null) {
                key(cameraTick) {
                    pins.forEach { pin ->
                        val screenPoint = currentMap.projection.toScreenLocation(
                            LatLng(pin.position.latitude, pin.position.longitude),
                        )
                        Box(
                            modifier = Modifier
                                .pinOffset(Offset(screenPoint.x, screenPoint.y))
                                .size(40.dp)
                                .clip(pin.category.shape)
                                .background(pin.category.color),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val target = map?.cameraPosition?.target
                onOpenDropPin(if (target != null) GeoPoint(target.latitude, target.longitude) else GeoPoint(0.0, 0.0))
            },
            containerColor = Color(0xFF4BA3E0),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Box(modifier = Modifier.size(22.dp).clip(app.betar.comm.ui.theme.BetarPolygonShapes.diamond).background(Color(0xFF0B3A57)))
        }
    }
}

private fun Modifier.pinOffset(offset: Offset): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(offset.x.toInt() - placeable.width / 2, offset.y.toInt() - placeable.height / 2)
    }
}
