package india.projectmesh.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Tokens transcribed from docs/DESIGN-BRIEF.md §6 and design/Betar Design System.dc.html.
// Dark and high-contrast values below that pass does not specify are derived here (kept at
// the same hue, pushed to the correct tone) and are not yet designer-approved -- flagged
// rather than presented as settled, per docs/BETAR-TRANSITION.md Part 6's own open item
// ("run the brief through a designer ... before the full screen set").

private val BrandBlue = Color(0xFF4BA3E0)
private val DeepBlue = Color(0xFF12608F)
private val OffWhiteBlue = Color(0xFFEEF4F9)
private val LiftedSurface = Color(0xFFF7FAFD)
private val Ink = Color(0xFF101A22)

private val EmergencyRed = Color(0xFFC8102E)
private val EmergencyRedDark = Color(0xFF8C0B20)
private val EmergencyContainerLight = Color(0xFFF3D8DC)
private val OnEmergencyContainerLight = Color(0xFF6B3037)

private val ConnectedGreen = Color(0xFF2E7D32)
private val ConnectedGreenDark = Color(0xFF1B5E20)
private val ConnectedContainerLight = Color(0xFFDCEEDC)
private val OnConnectedContainerLight = Color(0xFF1B5E20)

// Standard Material error role is deliberately NOT red here -- DESIGN-BRIEF.md §6: "the
// standard error role is amber here, and a custom emergency role owns red." Red means
// emergency and nothing else, never form errors or destructive actions.
private val CautionAmber = Color(0xFF9A6700)
private val CautionAmberDark = Color(0xFF7A5100)
private val CautionContainerLight = Color(0xFFF6EBD2)

private val NeutralSlate = Color(0xFF4A5B67)
private val NeutralSlateDark = Color(0xFF3A4B57)
private val SecondaryContainerLight = Color(0xFFDCE9F5)
private val OnSecondaryContainerLight = Color(0xFF0B3A57)
private val OutlineLight = Color(0xFFA8BAC7)
private val OutlineVariantLight = Color(0xFFC4D3DE)

fun betarLightColorScheme() = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = SecondaryContainerLight,
    onPrimaryContainer = OnSecondaryContainerLight,
    secondary = NeutralSlate,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Ink,
    tertiary = BrandBlue,
    onTertiary = Color.White,
    background = OffWhiteBlue,
    onBackground = Ink,
    surface = LiftedSurface,
    onSurface = Ink,
    surfaceVariant = SecondaryContainerLight,
    onSurfaceVariant = NeutralSlateDark,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    // Error role carries caution/unverified, not destructive red -- see note above.
    error = CautionAmber,
    onError = Color.White,
    errorContainer = CautionContainerLight,
    onErrorContainer = CautionAmberDark,
)

// Derived: same hues, dark-surface tones. Not present in the design pass yet.
fun betarDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF8FCBF2),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF0B3A57),
    onPrimaryContainer = Color(0xFFCFE6F7),
    secondary = Color(0xFFB9C8D3),
    onSecondary = Color(0xFF23323C),
    secondaryContainer = Color(0xFF344450),
    onSecondaryContainer = Color(0xFFDCE9F5),
    tertiary = BrandBlue,
    onTertiary = Color(0xFF00344F),
    background = Color(0xFF0E161C),
    onBackground = Color(0xFFDEE6EC),
    surface = Color(0xFF16212A),
    onSurface = Color(0xFFDEE6EC),
    surfaceVariant = Color(0xFF344450),
    onSurfaceVariant = OutlineVariantLight,
    outline = Color(0xFF7E909C),
    outlineVariant = Color(0xFF344450),
    error = Color(0xFFE0B34D),
    onError = Color(0xFF3F2E00),
    errorContainer = CautionAmberDark,
    onErrorContainer = CautionContainerLight,
)

// Sunlight / high-contrast variants: DESIGN-BRIEF.md §5.7 requires a high-contrast mode for
// both light and dark for outdoor glare use. Pass here is a straightforward contrast push
// (near-black on near-white, near-white on near-black); the brief calls this "sunlight
// mode" but does not hand down its own token set, so this is an engineering derivation,
// not a designer-supplied one.
fun betarLightHighContrastColorScheme() = betarLightColorScheme().copy(
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = Color(0xFF0B3A57),
    outline = Color(0xFF4A5B67),
)

fun betarDarkHighContrastColorScheme() = betarDarkColorScheme().copy(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFBEE0F7),
    outline = Color(0xFFB9C8D3),
)

/**
 * Semantic roles DESIGN-BRIEF.md §6 calls for that Material3's [androidx.compose.material3.ColorScheme]
 * has no slot for: emergency (red, SOS only -- never errors/destructive actions) and
 * connected/verified (green, always paired with a shape or icon, never colour alone per §5.6).
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
    emergency = EmergencyRed,
    onEmergency = Color.White,
    emergencyContainer = EmergencyContainerLight,
    onEmergencyContainer = OnEmergencyContainerLight,
    connected = ConnectedGreen,
    onConnected = Color.White,
    connectedContainer = ConnectedContainerLight,
    onConnectedContainer = OnConnectedContainerLight,
)

val DarkBetarExtendedColors = BetarExtendedColors(
    emergency = Color(0xFFFFB3AB),
    onEmergency = EmergencyRedDark,
    emergencyContainer = EmergencyRedDark,
    onEmergencyContainer = EmergencyContainerLight,
    connected = Color(0xFF8FDB94),
    onConnected = ConnectedGreenDark,
    connectedContainer = ConnectedGreenDark,
    onConnectedContainer = ConnectedContainerLight,
)

val LocalBetarExtendedColors = staticCompositionLocalOf { LightBetarExtendedColors }
