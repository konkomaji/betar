package app.betar.comm.ui.nav

import androidx.compose.ui.graphics.Shape
import app.betar.comm.ui.theme.BetarPolygonShapes

/**
 * The four bottom-nav destinations for the pivoted app (decided 2026-10-31: Betar is a
 * censorship-resistant social and chat platform, not a disaster-safety app). Matches the
 * locked Civic Calm design, docs/design-concepts/betar-design-D-civic-calm.html in the betar
 * repo: Feed, Nearby, Chats, You. Board and Map are removed, not repurposed, along with every
 * disaster-specific feature they carried.
 */
enum class BetarDestination(val route: String, val label: String, val icon: Shape) {
    Feed(route = "feed", label = "Feed", icon = BetarPolygonShapes.cookie9),
    Nearby(route = "nearby", label = "Nearby", icon = BetarPolygonShapes.scallop12),
    Chats(route = "chats", label = "Chats", icon = BetarPolygonShapes.flower6),
    You(route = "you", label = "You", icon = BetarPolygonShapes.pentagon),
}

