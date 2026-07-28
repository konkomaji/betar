package app.betar.comm.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * DESIGN-BRIEF.md §9 screens 1-5, sequenced. A plain step index rather than a nested NavHost
 * route: onboarding is strictly linear and only runs once, so there is nothing here that needs
 * back-stack semantics beyond "next step."
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    when (step) {
        0 -> LanguagePickerScreen(onLanguageSelected = { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            step = 1
        })
        1 -> IntroPagerScreen(onFinished = { step = 2 })
        2 -> NicknameScreen(onDone = { step = 3 })
        3 -> PermissionsExplainerScreen(onDone = { step = 4 })
        4 -> BatteryGuidanceScreen(onDone = onFinished)
    }
}
