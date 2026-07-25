package india.projectmesh.app.ui.screens.onboarding

import android.content.Context
import kotlin.random.Random

/**
 * A nickname is a public, non-secret display label ("Only phones near you ever see this"),
 * so plain `SharedPreferences` is enough, the same choice already made for Direct contacts'
 * public fingerprints in `DirectMessaging.kt`. No backend/FFI concept of a nickname exists yet;
 * this is new local-only storage introduced by this screen, not wired to anything else.
 */
object NicknameStore {
    private const val PREFS = "betar_nickname"
    private const val KEY_NICKNAME = "nickname"

    private val adjectives = listOf("Swift", "Calm", "Steady", "Bright", "Quiet", "Kind", "Ready", "Warm")
    private val nouns = listOf("River", "Hill", "Star", "Field", "Harbour", "Grove", "Bridge", "Lantern")

    fun loadOrGenerate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_NICKNAME, null)?.let { return it }
        val generated = "${adjectives.random()}${nouns.random()}${Random.nextInt(10, 99)}"
        prefs.edit().putString(KEY_NICKNAME, generated).apply()
        return generated
    }

    fun save(context: Context, nickname: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NICKNAME, nickname).apply()
    }
}
