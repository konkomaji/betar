package india.projectmesh.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import india.projectmesh.app.ui.BetarApp
import india.projectmesh.app.ui.theme.AppearancePrefs
import india.projectmesh.app.ui.theme.BetarTheme
import india.projectmesh.app.ui.theme.ThemeMode

/**
 * Entry point. The real screen tree lives in [india.projectmesh.app.ui.BetarApp]
 * (onboarding gate, then the five-destination shell with the emergency flow reachable from its
 * persistent SOS button) -- this class now only owns the theme wrapper.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode = AppearancePrefs.themeMode
            BetarTheme(
                darkTheme = mode == ThemeMode.Dark,
                highContrast = AppearancePrefs.sunlightMode,
            ) {
                BetarApp()
            }
        }
    }
}

/** Reused by the onboarding permissions-explainer screen so there is exactly one place that
 *  decides which permissions the mesh actually needs. */
internal fun requiredMeshPermissions(): Array<String> {
    val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // Wi-Fi Direct discovery (WifiDirectTransportDriver, now driven via MultiTransport/
    // MeshCoordinator) needs its own runtime grant independent of Bluetooth's: the framework
    // requires ACCESS_FINE_LOCATION for P2P service discovery on API 29-32 regardless of
    // Bluetooth's own permission model (which stopped needing location at API 31), and
    // NEARBY_WIFI_DEVICES from API 33+. Without this, WifiDirectTransportDriver.start() throws
    // at runtime on a real device even though `bluetooth` above already grants everything BLE
    // needs -- caught by re-reading what the driver actually calls, not assumed from the BLE list.
    val wifiDirect = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyArray() // already covered by `bluetooth`'s ACCESS_FINE_LOCATION branch above
    }
    // The foreground service's persistent notification needs this at runtime on API 33+.
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    return (bluetooth.toSet() + wifiDirect.toSet() + notifications.toSet()).toTypedArray()
}
