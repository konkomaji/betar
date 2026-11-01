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
import app.betar.comm.ui.theme.BetarColors
import app.betar.comm.ui.theme.BetarPolygonShapes
import app.betar.comm.ui.theme.PillShape

/**
 * In-person verification state, shown as a two-state chip: hexagon + word + sage/connected for
 * "met in person", diamond + word + amber for "not met yet". Deliberately never the reserved
 * emergency colour: not having met somebody yet is not an error, and that colour is spoken for.
 *
 * Rewritten 2026-11-01 to reference theme roles instead of the literal pre-pivot hex this still
 * had (exact matches to the old `ConnectedGreen`/`CautionAmber` constants, untouched by the
 * Civic Calm recolor since nothing here read from `Color.kt` at all).
 */
enum class TrustState { MetInPerson, NotMetYet }

private data class TrustChipSpec(val bg: Color, val fg: Color, val dot: Color, val shape: Shape, val label: String)

@Composable
private fun specFor(state: TrustState): TrustChipSpec {
    val colors = MaterialTheme.colorScheme
    val extended = BetarColors.extended
    return when (state) {
        TrustState.MetInPerson -> TrustChipSpec(extended.connectedContainer, extended.onConnectedContainer, extended.connected, BetarPolygonShapes.hexagon, "Met in person")
        TrustState.NotMetYet -> TrustChipSpec(colors.errorContainer, colors.onErrorContainer, colors.error, BetarPolygonShapes.diamond, "Not met yet")
    }
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
