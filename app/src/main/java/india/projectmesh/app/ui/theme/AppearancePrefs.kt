package india.projectmesh.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** DESIGN-BRIEF.md §9 screen 37: light, dark, follow system, plus the sunlight/high-contrast
 * toggle. Plain (unencrypted) SharedPreferences: this is a display preference, not sensitive
 * data, unlike [india.projectmesh.app.KeystoreIdentityStore]'s identity bytes. */
enum class ThemeMode { Light, Dark, FollowSystem }

private const val PREFS_NAME = "betar_appearance"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_SUNLIGHT = "sunlight_mode"

object AppearancePrefs {
    var themeMode by mutableStateOf(ThemeMode.Light)
        private set
    var sunlightMode by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.Light.name)!!)
        } catch (e: IllegalArgumentException) {
            ThemeMode.Light
        }
        sunlightMode = prefs.getBoolean(KEY_SUNLIGHT, false)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setSunlightMode(context: Context, on: Boolean) {
        sunlightMode = on
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SUNLIGHT, on).apply()
    }
}
