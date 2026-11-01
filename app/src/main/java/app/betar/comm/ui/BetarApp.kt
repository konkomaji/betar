package app.betar.comm.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.betar.comm.MeshApplication
import app.betar.comm.MeshRelayService
import app.betar.comm.requiredMeshPermissions
import app.betar.comm.ui.components.MeshRibbonState
import app.betar.comm.ui.nav.BetarDestination
import app.betar.comm.ui.nav.BetarScaffold
import app.betar.comm.ui.screens.chats.ChatsTab
import app.betar.comm.ui.screens.documents.AboutDocumentScreen
import app.betar.comm.ui.screens.documents.DocumentsIndexScreen
import app.betar.comm.ui.screens.documents.GuideDocumentScreen
import app.betar.comm.ui.screens.documents.LicensesDocumentScreen
import app.betar.comm.ui.screens.documents.PermissionsDocumentScreen
import app.betar.comm.ui.screens.documents.PrivacyDocumentScreen
import app.betar.comm.ui.screens.documents.SafetyDocumentScreen
import app.betar.comm.ui.screens.documents.TermsDocumentScreen
import app.betar.comm.ui.screens.feed.FeedTab
import app.betar.comm.ui.screens.nearby.NearbyScreen
import app.betar.comm.ui.screens.onboarding.OnboardingFlow
import app.betar.comm.ui.screens.you.AppearanceScreen
import app.betar.comm.ui.screens.you.LanguageScreen
import app.betar.comm.ui.screens.you.PrivacyScreen
import app.betar.comm.ui.screens.you.YouHomeScreen
import app.betar.comm.ui.theme.AppearancePrefs
import kotlinx.coroutines.delay

private const val ONBOARDING_PREFS = "betar_onboarding"
private const val KEY_DONE = "done"

private fun isOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

private fun markOnboardingDone(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
}

/**
 * The real app. Gates on the onboarding flow once per install, then shows the four-destination
 * shell (Feed, Nearby, Chats, You), matching the locked Civic Calm design.
 *
 * Pivoted 2026-11-01: no more emergency flow, no persistent SOS button. Board and Map are gone,
 * Feed takes Board's old tab slot in the bottom nav order so the shell is still four destinations
 * wide, not five shrunk to four unevenly.
 */
@Composable
fun BetarApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppearancePrefs.load(context) }

    var onboarded by rememberSaveable { mutableStateOf(isOnboardingDone(context)) }

    if (!onboarded) {
        // .statusBarsPadding(): OnboardingFlow's own screens are bare full-screen composables,
        // not hosted inside anything that insets them -- same gap the mesh ribbon header had
        // before being fixed, just never applied here. Concretely: IntroPagerScreen's
        // top-anchored Skip button sat under the system status bar's touch-intercept zone,
        // unreachable, found via a real on-device tap that silently did nothing.
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            OnboardingFlow(onFinished = {
                markOnboardingDone(context)
                onboarded = true
            })
        }
    } else {
        MainExperience()
    }
}

@Composable
private fun MainExperience() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }

    var current by rememberSaveable { mutableStateOf(BetarDestination.Feed) }
    var meshRunning by remember { mutableStateOf(app.coordinator.isRunning()) }
    var peerCount by remember { mutableStateOf(0) }

    // Offline is the normal state: the mesh starts itself once onboarding (and its permission
    // grant) is behind us, rather than a manual "start mesh" button. Guarded on the permissions
    // actually being granted: onboarding's PermissionsExplainerScreen requests them, but a user
    // can deny that system dialog, and starting the foreground service without the grants it
    // needs would crash rather than degrade, so this checks rather than assumes.
    LaunchedEffect(Unit) {
        val hasAllPermissions = requiredMeshPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!app.coordinator.isRunning() && hasAllPermissions) {
            ContextCompat.startForegroundService(context, MeshRelayService.startIntent(context))
        }
        while (true) {
            meshRunning = app.coordinator.isRunning()
            peerCount = if (meshRunning) app.coordinator.connectedPeerCount() else 0
            delay(1000)
        }
    }

    val ribbonState = when {
        !meshRunning -> MeshRibbonState.Off
        peerCount > 0 -> MeshRibbonState.Connected(peerCount)
        else -> MeshRibbonState.Looking
    }

    BetarScaffold(
        current = current,
        ribbonState = ribbonState,
        onDestinationSelected = { current = it },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                BetarDestination.Feed -> FeedTab(app.feedMessenger, app.notesMessenger)
                BetarDestination.Nearby -> NearbyScreen()
                BetarDestination.Chats -> ChatsTab()
                BetarDestination.You -> YouTab()
            }
        }
    }
}

private enum class YouRoute { Home, Language, Appearance, Privacy, Documents, DocDetail }
private enum class DocRoute { Privacy, Terms, Safety, Permissions, Licenses, About, Guide }

@Composable
private fun YouTab() {
    var route by rememberSaveable { mutableStateOf(YouRoute.Home) }
    var docRoute by rememberSaveable { mutableStateOf<DocRoute?>(null) }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun backBar(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Plain glyph rather than a material-icons dependency this module
                        // doesn't otherwise pull in.
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
            content()
        }
    }

    when (route) {
        YouRoute.Home -> YouHomeScreen(
            onOpenLanguage = { route = YouRoute.Language },
            onOpenAppearance = { route = YouRoute.Appearance },
            onOpenPrivacy = { route = YouRoute.Privacy },
            onOpenDocuments = { route = YouRoute.Documents },
        )
        YouRoute.Language -> backBar("Language", { route = YouRoute.Home }) { LanguageScreen() }
        YouRoute.Appearance -> backBar("Appearance", { route = YouRoute.Home }) { AppearanceScreen() }
        YouRoute.Privacy -> backBar("Privacy", { route = YouRoute.Home }) { PrivacyScreen() }
        YouRoute.Documents -> if (docRoute == null) {
            backBar("Legal, policies and guide", { route = YouRoute.Home }) {
                DocumentsIndexScreen(
                    onOpenPrivacy = { docRoute = DocRoute.Privacy },
                    onOpenTerms = { docRoute = DocRoute.Terms },
                    onOpenSafety = { docRoute = DocRoute.Safety },
                    onOpenPermissions = { docRoute = DocRoute.Permissions },
                    onOpenLicenses = { docRoute = DocRoute.Licenses },
                    onOpenAbout = { docRoute = DocRoute.About },
                    onOpenGuide = { docRoute = DocRoute.Guide },
                )
            }
        } else {
            backBar("Document", { docRoute = null }) {
                when (docRoute) {
                    DocRoute.Privacy -> PrivacyDocumentScreen()
                    DocRoute.Terms -> TermsDocumentScreen()
                    DocRoute.Safety -> SafetyDocumentScreen()
                    DocRoute.Permissions -> PermissionsDocumentScreen()
                    DocRoute.Licenses -> LicensesDocumentScreen()
                    DocRoute.About -> AboutDocumentScreen()
                    DocRoute.Guide -> GuideDocumentScreen()
                    null -> Unit
                }
            }
        }
        YouRoute.DocDetail -> Unit
    }
}
