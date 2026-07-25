package india.projectmesh.app.ui.screens.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import india.projectmesh.app.R
import india.projectmesh.app.ui.theme.BetarPolygonShapes

/**
 * Community/supply pin categories for the offline map (DESIGN-BRIEF.md §9 screen 32's "category
 * grid"). Shape choices echo design/Betar Board Map and Nearby.dc.html's own pin set (medical,
 * water, shelter, power) with one substitution: that file uses an `arch` silhouette for shelter,
 * which isn't in [BetarPolygonShapes]'s registry (only the Design System file's core shape set
 * was ported there); [hexagon] is used instead rather than adding new shape geometry outside
 * this screen's own scope.
 */
enum class MapPinCategory(val shape: Shape, val color: Color, val labelRes: Int) {
    Medical(BetarPolygonShapes.clover4, Color(0xFFC8102E), R.string.category_medical),
    Water(BetarPolygonShapes.scallop12, Color(0xFF12608F), R.string.category_water),
    Shelter(BetarPolygonShapes.hexagon, Color(0xFF12608F), R.string.category_shelter),
    Power(BetarPolygonShapes.flower6, Color(0xFF12608F), R.string.category_charging),
}

/**
 * A pin dropped on the offline map. [position] is a local view-relative offset, not a real
 * latitude/longitude: there is no geo-referenced tile data or pin-over-mesh transport yet
 * (docs/IMPLEMENTATION-STATUS.md's "Offline maps (MapLibre)" row), so this is an honest
 * in-memory, this-device-only placeholder, not a synced or geographically real pin.
 */
data class MapPin(
    val category: MapPinCategory,
    val position: Offset,
    val droppedAtSeconds: Long,
)
