package india.projectmesh.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * DESIGN-BRIEF.md §5.4's four-state honest delivery indicator: "Never show a checkmark that
 * implies delivery is guaranteed." Node/link coordinates and colours transcribed verbatim from
 * design/Betar Design System.dc.html's `glyph(kind)` function (a 32x32 unit hop graph: filled
 * node = phone the message reached, hollow node = phone it hasn't reached yet).
 */
enum class DeliveryState { Waiting, Travelling, Spreading, Delivered }

private data class GlyphNode(val x: Float, val y: Float, val r: Float, val filled: Boolean)
private data class GlyphLink(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
private data class Glyph(val color: Color, val links: List<GlyphLink>, val nodes: List<GlyphNode>)

private fun glyphFor(state: DeliveryState): Glyph = when (state) {
    DeliveryState.Waiting -> Glyph(
        color = Color(0xFF4A5B67),
        links = emptyList(),
        nodes = listOf(GlyphNode(16f, 16f, 4.5f, filled = false)),
    )
    DeliveryState.Travelling -> Glyph(
        color = Color(0xFF12608F),
        links = listOf(GlyphLink(10f, 20f, 22f, 12f)),
        nodes = listOf(GlyphNode(9f, 21f, 4f, filled = true), GlyphNode(23f, 11f, 4f, filled = false)),
    )
    DeliveryState.Spreading -> Glyph(
        color = Color(0xFF12608F),
        links = listOf(GlyphLink(8f, 22f, 16f, 8f), GlyphLink(8f, 22f, 25f, 20f), GlyphLink(16f, 8f, 25f, 20f)),
        nodes = listOf(GlyphNode(8f, 23f, 3.6f, filled = true), GlyphNode(16f, 7f, 3.6f, filled = true), GlyphNode(26f, 20f, 3.6f, filled = false)),
    )
    DeliveryState.Delivered -> Glyph(
        color = Color(0xFF2E7D32),
        links = listOf(GlyphLink(8f, 22f, 16f, 8f), GlyphLink(16f, 8f, 25f, 20f), GlyphLink(8f, 22f, 25f, 20f)),
        nodes = listOf(GlyphNode(8f, 23f, 3.6f, filled = true), GlyphNode(16f, 7f, 3.6f, filled = true), GlyphNode(26f, 20f, 5.6f, filled = true)),
    )
}

fun DeliveryState.label(): String = when (this) {
    DeliveryState.Waiting -> "Waiting"
    DeliveryState.Travelling -> "Travelling"
    DeliveryState.Spreading -> "Spreading"
    DeliveryState.Delivered -> "Delivered"
}

fun DeliveryState.means(): String = when (this) {
    DeliveryState.Waiting -> "Still on your phone, nobody nearby yet"
    DeliveryState.Travelling -> "Handed to one phone"
    DeliveryState.Spreading -> "Handed to several phones"
    DeliveryState.Delivered -> "The person you wrote to confirmed it"
}

@Composable
fun DeliveryGlyph(state: DeliveryState, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val glyph = glyphFor(state)
    Canvas(modifier.size(size)) {
        val scale = this.size.width / 32f
        fun px(v: Float) = v * scale
        glyph.links.forEach { l ->
            drawLine(
                color = glyph.color,
                start = Offset(px(l.x1), px(l.y1)),
                end = Offset(px(l.x2), px(l.y2)),
                strokeWidth = px(1.6f),
            )
        }
        glyph.nodes.forEach { n ->
            if (n.filled) {
                drawCircle(color = glyph.color, radius = px(n.r), center = Offset(px(n.x), px(n.y)))
            } else {
                drawCircle(color = glyph.color, radius = px(n.r), center = Offset(px(n.x), px(n.y)), style = Stroke(width = px(1.6f)))
            }
        }
    }
}

/** Glyph plus its label, the form used inline on a message row or conversation header. */
@Composable
fun DeliveryStateIndicator(state: DeliveryState, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        DeliveryGlyph(state)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(state.label(), style = MaterialTheme.typography.labelLarge)
    }
}
