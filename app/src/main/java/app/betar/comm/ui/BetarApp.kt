package app.betar.comm.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.betar.comm.MeshApplication
import app.betar.comm.MeshRelayService
import app.betar.comm.messaging.GeoPoint
import app.betar.comm.requiredMeshPermissions
import app.betar.comm.ui.components.MeshRibbonState
import app.betar.comm.ui.nav.BetarDestination
import app.betar.comm.ui.nav.BetarScaffold
import app.betar.comm.ui.screens.board.BoardScreen
import app.betar.comm.ui.screens.chats.ChatsTab
import app.betar.comm.ui.screens.documents.AboutDocumentScreen
import app.betar.comm.ui.screens.documents.DocumentsIndexScreen
import app.betar.comm.ui.screens.documents.LicensesDocumentScreen
import app.betar.comm.ui.screens.documents.PermissionsDocumentScreen
import app.betar.comm.ui.screens.documents.PrivacyDocumentScreen
import app.betar.comm.ui.screens.documents.SafetyDocumentScreen
import app.betar.comm.ui.screens.documents.StormGuideDocumentScreen
import app.betar.comm.ui.screens.documents.TermsDocumentScreen
import app.betar.comm.ui.screens.emergency.EmergencyFlow
import app.betar.comm.ui.screens.map.DropPinScreen
import app.betar.comm.ui.screens.map.OfflineMapScreen
import app.betar.comm.ui.screens.nearby.NearbyScreen
import app.betar.comm.ui.screens.onboarding.OnboardingFlow
import app.betar.comm.ui.screens.you.AppearanceScreen
import app.betar.comm.ui.screens.you.CarryingForOthersScreen
import app.betar.comm.ui.screens.you.LanguageScreen
import app.betar.comm.ui.screens.you.PrivacyScreen
import app.betar.comm.ui.screens.you.ReadinessScreen
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
 * The real app, replacing MainActivity's old single scrolling column of every feature stacked
 * on one screen. Gates on the onboarding flow (DESIGN-BRIEF.md §9 screens 1-5) once per
 * install, then shows the five-destination shell (§8) with the emergency flow reachable from
 * [app.betar.comm.ui.nav.BetarScaffold]'s persistent SOS button.
 */
@Composable
fun BetarApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppearancePrefs.load(context) }

    var onboarded by rememberSaveable { mutableStateOf(isOnboardingDone(context)) }

    if (!onboarded) {
        // .statusBarsPadding(): OnboardingFlow's own screens are bare full-screen composables,
        // not hosted inside anything that insets them -- same gap the mesh ribbon/emergency
        // header both had before being fixed (see PROGRESS.md's 2026-07-26 entry), just never
        // applied here. Concretely: IntroPagerScreen's top-anchored Skip button sat under the
        // system status bar's touch-intercept zone, unreachable, found via a real on-device tap
        // that silently did nothing.
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

    var current by rememberSaveable { mutableStateOf(BetarDestination.Chats) }
    var showEmergency by rememberSaveable { mutableStateOf(false) }
    var meshRunning by remember { mutableStateOf(app.coordinator.isRunning()) }
    var peerCount by remember { mutableStateOf(0) }

    // Offline is the normal state (DESIGN-BRIEF.md §5.3): the mesh starts itself once onboarding
    // (and its permission grant) is behind us, rather than a manual "start mesh" button the
    // debug-skeleton screen used to show. Guarded on the permissions actually being granted:
    // onboarding's PermissionsExplainerScreen requests them, but a user can deny that system
    // dialog, and starting the foreground service without the grants it needs would crash
    // rather than degrade, so this checks rather than assumes.
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
        onEmergencyClick = { showEmergency = true },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                BetarDestination.Chats -> ChatsTab()
                BetarDestination.Nearby -> NearbyScreen()
                BetarDestination.Board -> BoardScreen(app.sosMessenger, app.bulletinMessenger, app.resourceMessenger)
                BetarDestination.Map -> MapTab()
                BetarDestination.You -> YouTab()
            }
        }
    }

    if (showEmergency) {
        // .statusBarsPadding(): this is a bare full-screen overlay, not hosted inside
        // BetarScaffold's own Scaffold (which insets its content automatically), so without
        // this the header (including the close button) renders underneath the system status
        // bar and its tap target becomes unreachable, caught via a real on-device tap test,
        // not assumed.
        Surface(Modifier.fillMaxSize().statusBarsPadding()) {
            EmergencyFlow(
                messenger = app.sosMessenger,
                connectedPeerCount = { app.coordinator.connectedPeerCount() },
                onDismiss = { showEmergency = false },
            )
        }
    }
}

private enum class MapRoute { Map, DropPin }
private const val MAP_POLL_INTERVAL_MS = 1000L

@Composable
private fun MapTab() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as MeshApplication }
    var route by rememberSaveable { mutableStateOf(MapRoute.Map) }
    var pendingLocation by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            app.mapMessenger.pollForNewPins()
            delay(MAP_POLL_INTERVAL_MS)
        }
    }

    when (route) {
        MapRoute.Map -> OfflineMapScreen(
            onOpenDropPin = { center ->
                pendingLocation = center
                route = MapRoute.DropPin
            },
            pins = app.mapMessenger.pins,
            onQuickPinAt = { location ->
                pendingLocation = location
                route = MapRoute.DropPin
            },
        )
        MapRoute.DropPin -> DropPinScreen(
            onConfirm = { category ->
                pendingLocation?.let { app.mapMessenger.drop(category, it) }
                route = MapRoute.Map
            },
            onCancel = { route = MapRoute.Map },
        )
    }
}

private enum class YouRoute { Home, Language, Readiness, Carrying, Appearance, Privacy, Documents, DocDetail }
private enum class DocRoute { Privacy, Terms, Safety, Permissions, Licenses, About, StormGuide }

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
            onOpenReadiness = { route = YouRoute.Readiness },
            onOpenCarrying = { route = YouRoute.Carrying },
            onOpenAppearance = { route = YouRoute.Appearance },
            onOpenPrivacy = { route = YouRoute.Privacy },
            onOpenDocuments = { route = YouRoute.Documents },
        )
        YouRoute.Language -> backBar("Language", { route = YouRoute.Home }) { LanguageScreen() }
        YouRoute.Readiness -> backBar("Readiness", { route = YouRoute.Home }) { ReadinessScreen() }
        YouRoute.Carrying -> backBar("Carrying for others", { route = YouRoute.Home }) { CarryingForOthersScreen() }
        YouRoute.Appearance -> backBar("Appearance", { route = YouRoute.Home }) { AppearanceScreen() }
        YouRoute.Privacy -> backBar("Privacy", { route = YouRoute.Home }) { PrivacyScreen() }
        YouRoute.Documents -> if (docRoute == null) {
            backBar("Documents", { route = YouRoute.Home }) {
                DocumentsIndexScreen(
                    onOpenPrivacy = { docRoute = DocRoute.Privacy },
                    onOpenTerms = { docRoute = DocRoute.Terms },
                    onOpenSafety = { docRoute = DocRoute.Safety },
                    onOpenPermissions = { docRoute = DocRoute.Permissions },
                    onOpenLicenses = { docRoute = DocRoute.Licenses },
                    onOpenAbout = { docRoute = DocRoute.About },
                    onOpenStormGuide = { docRoute = DocRoute.StormGuide },
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
                    DocRoute.StormGuide -> StormGuideDocumentScreen()
                    null -> Unit
                }
            }
        }
        YouRoute.DocDetail -> Unit
    }
}
