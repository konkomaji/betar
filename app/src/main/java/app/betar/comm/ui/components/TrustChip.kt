package app.betar.comm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betar.comm.ui.theme.BetarPolygonShapes
import app.betar.comm.ui.theme.PillShape

/**
 * DESIGN-BRIEF.md §9 screen 18/20's in-person verification state, shown as a two-state chip
 * per design/Betar Design System.dc.html's "Trust chip, two states" spec: hexagon + word +
 * green for "met in person", diamond + word + amber for "not met yet". Deliberately never red:
 * "not having met somebody yet is not an error, and red is spoken for" (DESIGN-BRIEF.md §5.5).
 */
enum class TrustState { MetInPerson, NotMetYet }

private data class TrustChipSpec(val bg: Color, val fg: Color, val dot: Color, val shape: Shape, val label: String)

private fun specFor(state: TrustState): TrustChipSpec = when (state) {
    TrustState.MetInPerson -> TrustChipSpec(Color(0xFFDCEEDC), Color(0xFF1B5E20), Color(0xFF2E7D32), BetarPolygonShapes.hexagon, "Met in person")
    TrustState.NotMetYet -> TrustChipSpec(Color(0xFFF6EBD2), Color(0xFF7A5100), Color(0xFF9A6700), BetarPolygonShapes.diamond, "Not met yet")
}

@Composable
fun TrustChip(state: TrustState, modifier: Modifier = Modifier) {
    val spec = specFor(state)
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(spec.bg)
            .padding(PaddingValues(start = 10.dp, top = 8.dp, end = 14.dp, bottom = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(18.dp).clip(spec.shape).background(spec.dot))
        Spacer(Modifier.size(8.dp))
        Text(spec.label, color = spec.fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}
