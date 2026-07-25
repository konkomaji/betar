package india.projectmesh.app.ui.nav

import androidx.compose.ui.graphics.Shape
import india.projectmesh.app.ui.theme.BetarPolygonShapes

/**
 * The five bottom-nav destinations, DESIGN-BRIEF.md §8: "Five destinations in the bottom bar,
 * plus one emergency control that never goes away." Route name, label and icon shape
 * transcribed from design/Betar Design System.dc.html's `nav(active)` function.
 */
enum class BetarDestination(val route: String, val label: String, val icon: Shape) {
    Chats(route = "chats", label = "Chats", icon = BetarPolygonShapes.cookie9),
    Nearby(route = "nearby", label = "Nearby", icon = BetarPolygonShapes.scallop12),
    Board(route = "board", label = "Board", icon = BetarPolygonShapes.hexagon),
    Map(route = "map", label = "Map", icon = BetarPolygonShapes.clover4),
    You(route = "you", label = "You", icon = BetarPolygonShapes.pentagon),
}

/** Top-level routes reachable outside the bottom nav (emergency flow, onboarding). */
object BetarRoutes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val EMERGENCY = "emergency"
}
