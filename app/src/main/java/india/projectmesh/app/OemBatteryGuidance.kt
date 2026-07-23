package india.projectmesh.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

private const val TAG = "OemBatteryGuidance"

/**
 * Indian budget Android devices ship aggressive background-process killers well beyond stock
 * Android's Doze/App Standby -- `docs/TRANSPORT.md` §6 names Xiaomi/MIUI, realme, vivo,
 * Oppo/ColorOS, and Samsung specifically as needing vendor-specific battery-whitelisting
 * guidance for reliable background relay. This is a best-effort guide to (1) the standard
 * Android battery-optimization exemption, which works generically, and (2) known vendor-specific
 * "autostart"/"protected apps" settings screens the standard exemption alone doesn't reach on
 * these OEM skins.
 *
 * **Honest limit:** the vendor-specific component names below are reasoned from
 * publicly-documented, community-collected values (the standard reference point every
 * cross-platform background-service library uses, since no OEM publishes an official API for
 * this) — **not verified against every OEM/OS-version combination**, and will drift as vendors
 * rename or remove these screens across updates. Flagged plainly rather than presented as
 * guaranteed to work, matching this project's convention for every other untested-at-scale
 * default (rate limits, puzzle difficulty, Argon2id parameters, BLE connection cap).
 */
object OemBatteryGuidance {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /** Standard Android exemption request -- works generically, available since API 23. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity handles ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", e)
        }
    }

    /**
     * Try known vendor-specific autostart/protected-apps settings screens for this device's
     * manufacturer, falling back through alternatives (some OEMs renamed the activity across
     * ColorOS/MIUI versions). Returns true iff one actually launched.
     */
    fun openVendorAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates: List<Intent> = when {
            manufacturer.contains("xiaomi") -> listOf(
                vendorIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            manufacturer.contains("oppo") -> listOf(
                vendorIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                vendorIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            manufacturer.contains("vivo") -> listOf(
                vendorIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            manufacturer.contains("realme") -> listOf(
                // realme ships on a ColorOS-derived skin; same activity as Oppo.
                vendorIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            )
            manufacturer.contains("samsung") -> listOf(
                vendorIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )
            manufacturer.contains("oneplus") -> listOf(
                vendorIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )
            else -> emptyList()
        }
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                continue // this OS version doesn't have this activity -- try the next candidate
            }
        }
        return false
    }

    private fun vendorIntent(packageName: String, className: String): Intent =
        Intent().apply {
            component = ComponentName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
