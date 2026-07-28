package app.betar.comm.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Corner radius scale transcribed from the border-radius values actually used across
// design/Betar Design System.dc.html (8/14/20/26/28/999), not an invented M3 default scale.
val BetarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val PillShape: Shape = RoundedCornerShape(percent = 50)

private const val TWO_PI = (Math.PI * 2).toFloat()

/**
 * Reproduces the `lobed(n, base, amp, rot)` polar path from
 * design/Betar Design System.dc.html verbatim (radius at angle `a` is
 * `base + amp * cos(n * (a - rot))`, sampled at 200 points), rather than substituting a
 * library shape with different proportions. material3's own [androidx.compose.material3.MaterialShapes]
 * would give a similar silhouette family but not this exact one, and pulling it in requires
 * the 1.5.0-alpha artifact (AGP 9.1.0 + compileSdk 37) -- see build.gradle.kts's note.
 */
private fun lobedShape(n: Int, base: Float, amp: Float, rot: Float = 0f, steps: Int = 200): Shape =
    GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until steps) {
            val a = (i.toFloat() / steps) * TWO_PI
            val r = base + amp * cos(n * (a - rot))
            val x = cx + r * cx * cos(a)
            val y = cy + r * cy * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** Reproduces the `regular(n, rot)` even-sided polygon path from the same design file. */
private fun regularShape(n: Int, rot: Float = 0f): Shape =
    GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * TWO_PI + rot
            val x = cx + cx * cos(a)
            val y = cy + cy * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

private val DiamondShape: Shape = GenericShape { size, _ ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.5f)
    close()
}

/**
 * The full named-shape set from design/Betar Design System.dc.html's `SHAPES` object, geometry
 * transcribed exactly (same base/amp/rot per shape). Category tiles, the bottom nav's per-tab
 * icon clip, and the trust chip all draw from this one registry rather than each hand-rolling
 * their own shape, so a silhouette means the same thing everywhere it appears.
 */
object BetarPolygonShapes {
    val cookie9: Shape = lobedShape(n = 9, base = 0.84f, amp = 0.16f)
    val clover4: Shape = lobedShape(n = 4, base = 0.74f, amp = 0.26f, rot = (Math.PI / 4).toFloat())
    val burst8: Shape = lobedShape(n = 8, base = 0.72f, amp = 0.28f)
    val scallop12: Shape = lobedShape(n = 12, base = 0.88f, amp = 0.12f)
    val flower6: Shape = lobedShape(n = 6, base = 0.78f, amp = 0.22f)
    val pentagon: Shape = regularShape(n = 5, rot = (-Math.PI / 2).toFloat())
    val hexagon: Shape = regularShape(n = 6, rot = (-Math.PI / 2).toFloat())
    val diamond: Shape = DiamondShape
}

/**
 * DESIGN-BRIEF.md §6: "Give every emergency and supply category its own shape as well as its
 * own pictogram, so a category is recognisable by silhouette alone." Mapping transcribed from
 * design/Betar Design System.dc.html's `cats()` table: medical=clover, trapped=pentagon,
 * fire=burst, danger=diamond, other=cookie.
 */
object BetarCategoryShapes {
    val medical: Shape = BetarPolygonShapes.clover4
    val trapped: Shape = BetarPolygonShapes.pentagon
    val fire: Shape = BetarPolygonShapes.burst8
    val danger: Shape = BetarPolygonShapes.diamond
    val other: Shape = BetarPolygonShapes.cookie9
}
