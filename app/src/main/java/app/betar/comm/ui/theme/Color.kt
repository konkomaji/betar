package app.betar.comm.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Tokens transcribed from the locked Civic Calm design, docs/design-concepts/betar-design-D-civic-calm.html
// and docs/assets/style.css in this repo (both already recoloured 2026-10-31 for the pivot).
// Recoloured here 2026-11-01 from the pre-pivot brand blue to Civic Calm's warm sage and
// cream: same structure, same semantic-role decisions, only hue values changed. Dark and
// high-contrast values below that the design pass does not specify are derived here (kept at
// the same hue, pushed to the correct tone) and are not yet designer-approved, flagged
// rather than presented as settled, same caveat the pre-pivot version carried.

private val SageDark = Color(0xFF5F7D64)
private val DeeperSage = Color(0xFF3F5742)
private val Cream = Color(0xFFFAF6EF)
private val LiftedSurface = Color(0xFFFFFDF9)
private val Ink = Color(0xFF2F2B26)

// Civic Calm's danger colour is a soft brick, not an alarm red -- matches the direction's
// "trustworthy civic service, not a hacker tool" character. Still panic-wipe-only, never
// anything else, same rule as before the pivot, just a different hue.
private val EmergencyBrick = Color(0xFFAE5B46)
private val EmergencyBrickDark = Color(0xFF7A3F2F)
private val EmergencyContainerLight = Color(0xFFF3E2DD)
private val OnEmergencyContainerLight = Color(0xFF5C2E22)

// Civic Calm merges "connected/verified" into the same sage as the brand colour itself --
// calm green already reads as trustworthy, no separate bright green needed.
private val ConnectedSage = Color(0xFF5F7D64)
private val ConnectedSageDark = Color(0xFF3F5742)
private val ConnectedContainerLight = Color(0xFFE7EFE7)
private val OnConnectedContainerLight = Color(0xFF3F5742)

// Standard Material error role is deliberately NOT red here, same as before the pivot: the
// error role is amber, and the custom emergency role owns brick. That colour means panic
// wipe and nothing else, never form errors or destructive actions.
private val CautionAmber = Color(0xFFC98A4B)
private val CautionAmberDark = Color(0xFF8A5B27)
private val CautionContainerLight = Color(0xFFF4E6D4)

private val NeutralSlate = Color(0xFF6B6459)
private val NeutralSlateDark = Color(0xFF4A443B)
private val SecondaryContainerLight = Color(0xFFF3EDE2)
private val OnSecondaryContainerLight = Color(0xFF2F2B26)
private val OutlineLight = Color(0xFFE4DBCB)
private val OutlineVariantLight = Color(0xFFEFE8DA)

fun betarLightColorScheme() = lightColorScheme(
    primary = DeeperSage,
    onPrimary = Color.White,
    primaryContainer = SecondaryContainerLight,
    onPrimaryContainer = OnSecondaryContainerLight,
    secondary = NeutralSlate,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Ink,
    tertiary = SageDark,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = LiftedSurface,
    onSurface = Ink,
    surfaceVariant = SecondaryContainerLight,
    onSurfaceVariant = NeutralSlateDark,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    // Error role carries caution/unverified, not destructive brick -- see note above.
    error = CautionAmber,
    onError = Color.White,
    errorContainer = CautionContainerLight,
    onErrorContainer = CautionAmberDark,
)

// Derived: same hues, dark-surface tones. Not present in the design pass yet.
fun betarDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF9FBFA3),
    onPrimary = Color(0xFF1A2E1D),
    primaryContainer = Color(0xFF324236),
    onPrimaryContainer = Color(0xFFE7EFE7),
    secondary = Color(0xFFCFC6B4),
    onSecondary = Color(0xFF332F27),
    secondaryContainer = Color(0xFF433F35),
    onSecondaryContainer = Color(0xFFEDE6D8),
    tertiary = SageDark,
    onTertiary = Color(0xFF1A2E1D),
    background = Color(0xFF1C1A16),
    onBackground = Color(0xFFEFE9DD),
    surface = Color(0xFF262320),
    onSurface = Color(0xFFEFE9DD),
    surfaceVariant = Color(0xFF433F35),
    onSurfaceVariant = OutlineVariantLight,
    outline = Color(0xFF8A8376),
    outlineVariant = Color(0xFF433F35),
    error = Color(0xFFE0B378),
    onError = Color(0xFF3F2E00),
    errorContainer = CautionAmberDark,
    onErrorContainer = CautionContainerLight,
)

// Sunlight / high-contrast variants: DESIGN-BRIEF.md still requires a high-contrast mode for
// both light and dark for outdoor glare use (unchanged by the pivot). Pass here is a
// straightforward contrast push (near-black on near-white, near-white on near-black); still
// an engineering derivation, not a designer-supplied one.
fun betarLightHighContrastColorScheme() = betarLightColorScheme().copy(
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = Color(0xFF2F3F32),
    outline = Color(0xFF4A443B),
)

fun betarDarkHighContrastColorScheme() = betarDarkColorScheme().copy(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFCFE6D1),
    outline = Color(0xFFCFC6B4),
)

/**
 * Semantic roles the design calls for that Material3's [androidx.compose.material3.ColorScheme]
 * has no slot for: emergency (brick, panic wipe only -- never errors/destructive actions) and
 * connected/verified (sage, always paired with a shape or icon, never colour alone).
 */
@Immutable
data class BetarExtendedColors(
    val emergency: Color,
    val onEmergency: Color,
    val emergencyContainer: Color,
    val onEmergencyContainer: Color,
    val connected: Color,
    val onConnected: Color,
    val connectedContainer: Color,
    val onConnectedContainer: Color,
)

val LightBetarExtendedColors = BetarExtendedColors(
    emergency = EmergencyBrick,
    onEmergency = Color.White,
    emergencyContainer = EmergencyContainerLight,
    onEmergencyContainer = OnEmergencyContainerLight,
    connected = ConnectedSage,
    onConnected = Color.White,
    connectedContainer = ConnectedContainerLight,
    onConnectedContainer = OnConnectedContainerLight,
)

val DarkBetarExtendedColors = BetarExtendedColors(
    emergency = Color(0xFFE0A89B),
    onEmergency = EmergencyBrickDark,
    emergencyContainer = EmergencyBrickDark,
    onEmergencyContainer = EmergencyContainerLight,
    connected = Color(0xFF9FBFA3),
    onConnected = ConnectedSageDark,
    connectedContainer = ConnectedSageDark,
    onConnectedContainer = ConnectedContainerLight,
)

val LocalBetarExtendedColors = staticCompositionLocalOf { LightBetarExtendedColors }
