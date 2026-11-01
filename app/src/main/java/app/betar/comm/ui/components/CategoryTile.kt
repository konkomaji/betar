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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * General-purpose large tile with its own shape, pictogram and word -- used by the new-chat
 * sheet's three options (scan code / join channel / create group) and anywhere else a
 * shape-distinguished choice tile fits. Tiles are 160x132dp, comfortably above the 56dp minimum
 * touch target, large enough to hit one-handed without close attention.
 *
 * States (resting/pressed/selected/unavailable) rewritten 2026-11-01 to reference
 * `MaterialTheme.colorScheme` roles instead of hardcoded hex: the previous version was still
 * the literal pre-pivot brand-blue-era palette, untouched by the Civic Calm recolor, and used
 * the old emergency red as a generic "selected" colour, the same decorative misuse of the
 * reserved emergency role already fixed on the website. `primary` (now sage) replaces that.
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
    val colors = MaterialTheme.colorScheme

    val tileBg = when {
        !available -> colors.surfaceVariant
        selected -> colors.primary
        pressed -> colors.primaryContainer
        else -> colors.surface
    }
    val shapeFill = when {
        !available -> colors.outline
        selected -> colors.onPrimary
        pressed -> colors.primary
        else -> colors.primary
    }
    val fg = when {
        !available -> colors.onSurfaceVariant
        selected -> colors.onPrimary
        else -> colors.onSurface
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
