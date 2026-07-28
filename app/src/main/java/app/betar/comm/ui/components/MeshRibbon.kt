package app.betar.comm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * DESIGN-BRIEF.md §8's "mesh ribbon": "a thin strip under the app bar on every screen ...
 * showing Off, Looking, or Connected with a count, plus a small permanent mark meaning no
 * network in use." Colours and copy transcribed from design/Betar Design System.dc.html's
 * `ribbonStates` table.
 *
 * The ripple in [MeshRibbonState.Looking] and [MeshRibbonState.Connected] only animates while
 * that state is current, per the design file's own note: "the ripple animates on state change
 * only, then settles, a living indicator that costs no battery at rest." This uses a simple
 * always-on infinite ripple while Looking (there is no cheaper "settled" sub-state to detect
 * yet), which is close but not identical to that spec; flagged here rather than silently
 * presented as the finished behaviour.
 */
sealed class MeshRibbonState {
    data object Off : MeshRibbonState()
    data object Looking : MeshRibbonState()
    data class Connected(val peerCount: Int) : MeshRibbonState()
}

@Composable
fun MeshRibbon(state: MeshRibbonState, modifier: Modifier = Modifier) {
    val (bg, fg, dot) = when (state) {
        is MeshRibbonState.Off -> Triple(Color(0xFFE7EDF2), Color(0xFF4A5B67), Color(0xFF8A99A4))
        is MeshRibbonState.Looking -> Triple(Color(0xFFDCE9F5), Color(0xFF12608F), Color(0xFF4BA3E0))
        is MeshRibbonState.Connected -> Triple(Color(0xFFDCEEDC), Color(0xFF1B5E20), Color(0xFF2E7D32))
    }
    val label = when (state) {
        is MeshRibbonState.Off -> "Off, tap to start looking"
        is MeshRibbonState.Looking -> "Looking for phones nearby"
        is MeshRibbonState.Connected -> "Connected, ${state.peerCount} phones nearby"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color = bg)
            .padding(PaddingValues(horizontal = 14.dp)),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RippleDot(color = dot, animate = state !is MeshRibbonState.Off)
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RippleDot(color: Color, animate: Boolean, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val transition = rememberInfiniteTransition(label = "mesh-ribbon-ripple")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple-progress",
    )
    Box(Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            if (animate) {
                val scale = 0.45f + progress * 1.15f
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
                drawCircle(color = color.copy(alpha = alpha), radius = (size.toPx() / 2f) * scale, center = center, style = Stroke(width = 2.dp.toPx()))
            }
            drawCircle(color = color, radius = 4.dp.toPx(), center = center)
        }
    }
}
