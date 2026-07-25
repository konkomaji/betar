package india.projectmesh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FontFamily.Default for now. DESIGN-BRIEF.md §6 calls for Noto Sans plus Noto Sans Bengali
// and Noto Sans Devanagari bundled with the app (not loaded from a CDN, since Betar requests
// no INTERNET permission at all) -- the actual font files are not yet vendored under
// res/font/, so this is a real, flagged gap, not a silent substitution. Swap
// FontFamily.Default below for the bundled FontFamily once those files land.
private val BetarFontFamily = FontFamily.Default

// Sizes transcribed from the font-size values in design/Betar Design System.dc.html
// (7-40px scale), with DESIGN-BRIEF.md §5.7's "body text 18sp minimum" hard constraint
// applied to every body role, not just bodyLarge.
val BetarTypography = Typography(
    displayLarge = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.02).sp),
    displayMedium = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).sp),
    displaySmall = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    // Meta/caption use only (timestamps, redline annotations) -- deliberately below the 18sp
    // body floor because it never carries content a user must read to act.
    labelSmall = TextStyle(fontFamily = BetarFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
)
