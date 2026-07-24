package india.projectmesh.app

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Offline maps (`FEATURES.md` §3 — "situational awareness with no internet and no Google").
 * Wires up the MapLibre Native Android SDK itself; this pass is the rendering pipeline, not the
 * map data.
 *
 * **What's genuinely offline here, verified not assumed:** [OFFLINE_BLANK_STYLE] is a complete,
 * valid MapLibre/Mapbox style spec document with an empty `sources` object and one `background`
 * layer — there is no URL anywhere in it for MapLibre to fetch, so `map.setStyle` never makes a
 * network call. This matches `AndroidManifest.xml`'s deliberate absence of the `INTERNET`
 * permission (`docs/ARCHITECTURE.md` §1, `docs/DISTRIBUTION.md` §1) — a real map screen that
 * silently needed `INTERNET` to render anything at all would quietly violate that stance the
 * first time someone swapped in a remote style URL "just for testing." Loaded via
 * `Style.Builder().fromJson(...)`, not `fromUri("https://...")`.
 *
 * **Not done, stated plainly — this is a rendering pipeline, not offline maps yet:** no
 * OpenStreetMap vector tiles, no MBTiles/PMTiles regional tile pack loading, no tile-pack
 * sideloading or Wi-Fi-Direct-based pack sharing (`FEATURES.md` §3's "one downloaded map seeds a
 * whole area" needs an actual downloaded pack to seed from — this dev environment has no way to
 * source real geographic tile data), and no pin-dropping-over-the-mesh protocol (would be its own
 * envelope/messenger, same shape as the civic-post classes). What's here is the proof that the
 * SDK integrates cleanly and renders with zero network dependency — the real map-data work is a
 * separate, larger follow-up, tracked in `docs/IMPLEMENTATION-STATUS.md`.
 */
private const val OFFLINE_BLANK_STYLE = """
{
  "version": 8,
  "name": "Mesh Offline Blank",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#dde5d5" }
    }
  ]
}
"""

@Composable
fun MapScreen() {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.map_subtitle), style = MaterialTheme.typography.bodySmall)

        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context)
        }

        DisposableEffect(mapView) {
            mapView.onCreate(Bundle())
            mapView.onStart()
            mapView.onResume()
            mapView.getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(OFFLINE_BLANK_STYLE))
            }
            onDispose {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
    }
}
