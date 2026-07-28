package app.betar.comm.ui.screens.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.betar.comm.R
import app.betar.comm.messaging.GeoPoint
import app.betar.comm.ui.theme.BetarPolygonShapes

/**
 * Community/supply pin categories for the offline map (DESIGN-BRIEF.md §9 screen 32's "category
 * grid"). Shape choices echo design/Betar Board Map and Nearby.dc.html's own pin set (medical,
 * water, shelter, power) with one substitution: that file uses an `arch` silhouette for shelter,
 * which isn't in [BetarPolygonShapes]'s registry (only the Design System file's core shape set
 * was ported there); [hexagon] is used instead rather than adding new shape geometry outside
 * this screen's own scope.
 */
enum class MapPinCategory(val code: Int, val shape: Shape, val color: Color, val labelRes: Int) {
    Medical(0, BetarPolygonShapes.clover4, Color(0xFFC8102E), R.string.category_medical),
    Water(1, BetarPolygonShapes.scallop12, Color(0xFF12608F), R.string.category_water),
    Shelter(2, BetarPolygonShapes.hexagon, Color(0xFF12608F), R.string.category_shelter),
    Power(3, BetarPolygonShapes.flower6, Color(0xFF12608F), R.string.category_charging),
    ;

    companion object {
        fun fromCode(code: Int): MapPinCategory = entries.find { it.code == code } ?: Shelter
    }
}

/**
 * A pin dropped on the offline map. [position] is a real WGS84 lat/lon ([GeoPoint]), not a
 * screen-relative offset -- a screen offset means nothing on another device, or even on this one
 * after panning/zooming, so it can't be what gets shared over the mesh. [idHex] is the envelope
 * ID once synced ([MapPinMessenger]), or a locally-generated placeholder for a pin not yet sent
 * (dedup key so a pin doesn't get drawn twice once its own broadcast round-trips back).
 */
data class MapPin(
    val idHex: String,
    val category: MapPinCategory,
    val position: GeoPoint,
    val droppedAtSeconds: Long,
)
