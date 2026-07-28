package app.betar.comm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * DESIGN-BRIEF.md §9's category picker: "Five very large tiles, each with its own shape,
 * pictogram and word." Also used for the Board/help-and-supply category grid (§9 screens
 * 21-24). States (resting/pressed/selected/unavailable) and colours transcribed from
 * design/Betar Design System.dc.html's `tileStates` table. Tiles are 160x132dp per that file's
 * own note ("so it can be hit one handed, in the rain, without looking"), comfortably above the
 * 56dp minimum touch target DESIGN-BRIEF.md §5.7 requires.
 */
@Composable
fun CategoryTile(
    label: String,
    shape: Shape,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val tileBg = when {
        !available -> Color(0xFFEDF1F5)
        selected -> Color(0xFFC8102E)
        pressed -> Color(0xFFF0D9DD)
        else -> Color(0xFFF7FAFD)
    }
    val shapeFill = when {
        !available -> Color(0xFFA8BAC7)
        selected -> Color.White
        pressed -> Color(0xFF8C0B20)
        else -> Color(0xFFC8102E)
    }
    val fg = when {
        !available -> Color(0xFF7A8892)
        selected -> Color.White
        else -> Color(0xFF101A22)
    }

    val animatedBg by animateColorAsState(tileBg, label = "tile-bg")
    val animatedShapeFill by animateColorAsState(shapeFill, label = "tile-shape-fill")
    // "Shape morphs on press, 180ms, spring 500/34" per the design file's own motion spec.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "tile-scale",
    )

    Column(
        modifier = modifier
            .width(160.dp)
            .height(132.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(animatedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = available,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .background(animatedShapeFill),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        Text(label, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}
