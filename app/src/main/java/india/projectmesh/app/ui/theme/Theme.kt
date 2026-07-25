package india.projectmesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Betar's design system entry point. Four themes per DESIGN-BRIEF.md §6: light (primary),
 * light high contrast (the outdoor "sunlight mode"), dark, and dark high contrast. No
 * dynamic/wallpaper-derived color -- the brief locks a specific brand palette, so this
 * intentionally does not branch on Android 12+ dynamic color like a stock M3 app would.
 *
 * This is stable Material 3, not Material 3 Expressive: [androidx.compose.material3.MaterialExpressiveTheme],
 * `MaterialShapes` and `MotionScheme` were stripped from material3's stable API in 1.4.0 and
 * only exist as public-experimental starting the 1.5.0-alpha artifact, which in turn needs
 * AGP 9.1.0 + compileSdk 37 -- a full toolchain migration out of scope for this pass (see
 * app/build.gradle.kts's note). The category shapes DESIGN-BRIEF.md §6 calls for are
 * hand-rolled in Shape.kt to match the design file's own path math instead of borrowed from
 * MaterialShapes. Spring-based motion on the mesh ribbon and send/receive (also §6) will need
 * its own `animateFloatAsState(spring(...))` calls at the component level when those
 * components are built, rather than a theme-wide MotionScheme.
 */
@Composable
fun BetarTheme(
    darkTheme: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && highContrast -> betarDarkHighContrastColorScheme()
        darkTheme -> betarDarkColorScheme()
        highContrast -> betarLightHighContrastColorScheme()
        else -> betarLightColorScheme()
    }
    val extendedColors = if (darkTheme) DarkBetarExtendedColors else LightBetarExtendedColors

    CompositionLocalProvider(LocalBetarExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = BetarShapes,
            typography = BetarTypography,
            content = content,
        )
    }
}

/** Shorthand for the two roles [androidx.compose.material3.ColorScheme] has no slot for. */
object BetarColors {
    val extended: BetarExtendedColors
        @Composable get() = LocalBetarExtendedColors.current
}
