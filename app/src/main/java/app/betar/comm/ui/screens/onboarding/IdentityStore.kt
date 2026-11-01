package app.betar.comm.ui.screens.onboarding

import android.content.Context

/**
 * Renamed 2026-11-01 from `NicknameStore.kt` for the pivot's identity system. Two separate
 * things now, not one:
 *
 * - **The short ID** (e.g. `A3F9K2`) is derived from the real identity keypair
 *   (`MeshApplication.identity.fingerprintHex()`), never stored separately, never user-chosen,
 *   immutable. [shortIdFrom] does the derivation: the first 6 hex characters of the fingerprint,
 *   uppercased. Deterministic and simple, not meant to be cryptographically special, the
 *   fingerprint underneath is already the real source of uniqueness (`docs/THREAT-MODEL.md`'s
 *   identity-system section: a 6-character value is not collision-free at scale if it were
 *   issued independently, so it has to be a derivation of something already unique, not a new
 *   source of uniqueness itself).
 * - **The display username** is a public, non-secret, user-editable, explicitly non-unique
 *   label ("Only phones near you ever see this"), 3 to 8 characters, letters and digits plus
 *   `.` and `_` only. No server exists to check whether a chosen username collides with anyone
 *   else's, so it never claims to be unique, only the short ID is. Plain `SharedPreferences` is
 *   enough, the same choice already made for Direct contacts' public fingerprints in
 *   `DirectMessaging.kt`. No backend/FFI concept of a username exists yet; this is local-only
 *   storage, not wired to anything else.
 *
 * `ui/screens/you/YouHomeScreen.kt` also reads/writes through this object, not a private store
 * of its own: it used to, which let the two screens disagree on the user's name depending on
 * which last wrote it, fixed 2026-11-01 by consolidating onto this one.
 */
object IdentityStore {
    private const val PREFS = "betar_identity"
    private const val KEY_USERNAME = "username"

    private val adjectives = listOf("Swift", "Calm", "Steady", "Bright", "Quiet", "Kind", "Ready", "Warm")
    private val nouns = listOf("River", "Hill", "Star", "Field", "Harbour", "Grove", "Bridge", "Lantern")

    /** First 6 hex characters of the real identity fingerprint, uppercased. Permanent on this
     *  device for as long as the identity itself persists; wiped and regenerated only by panic
     *  wipe or an uninstall/reinstall, since both retire the identity keypair underneath it. */
    fun shortIdFrom(fingerprintHex: String): String =
        fingerprintHex.take(6).uppercase()

    /** 3 to 8 characters, letters and digits plus `.` and `_` only. Empty string is treated as
     *  valid (skippable, falls back to a generated placeholder at display time), since the
     *  username is always optional. */
    fun isValidUsername(username: String): Boolean =
        username.isEmpty() || username.matches(Regex("^[A-Za-z0-9._]{3,8}$"))

    fun loadOrGenerate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USERNAME, null)?.let { return it }
        val generated = "${adjectives.random()}${nouns.random()}${kotlin.random.Random.nextInt(10, 99)}"
        prefs.edit().putString(KEY_USERNAME, generated).apply()
        return generated
    }

    fun save(context: Context, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USERNAME, username).apply()
    }
}
