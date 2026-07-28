package app.betar.comm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import uniffi.mesh_core.FfiIdentity

private const val TAG = "KeystoreIdentityStore"
private const val KEY_ALIAS = "mesh_identity_wrap"
private const val PREFS_NAME = "mesh_identity"
private const val PREFS_KEY_WRAPPED = "identity_wrapped_b64"
private const val IDENTITY_BYTES_SIZE = 64

/**
 * Loads-or-creates the app's stable device identity, encrypted at rest via [KeystoreSecretBox] --
 * closes the single biggest gap flagged in this session's own product-review demo
 * (`docs/PROGRESS.md`'s "Channel messaging demo" entry): identity used to be `FfiIdentity.generate()`
 * called fresh on every process start, so the fingerprint changed on every restart and silently
 * orphaned every Direct contact that had been added against the old one.
 *
 * Uses `FfiIdentity::toBytes`/`FfiIdentity::fromBytes` (exported this pass specifically to make
 * this possible -- no persistence primitive existed at the Rust/FFI layer before). See
 * [KeystoreSecretBox]'s doc comment for the key-wrapping design, shared with [KeystoreMasterKey].
 */
object KeystoreIdentityStore {

    fun loadOrCreate(context: Context): FfiIdentity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.getString(PREFS_KEY_WRAPPED, null)?.let { encoded ->
            KeystoreSecretBox.unwrap(KEY_ALIAS, encoded, IDENTITY_BYTES_SIZE)?.let { bytes ->
                try {
                    return FfiIdentity.fromBytes(bytes)
                } catch (e: Exception) {
                    Log.w(TAG, "stored identity bytes failed to reconstruct -- regenerating", e)
                }
            } ?: Log.w(TAG, "stored wrapped identity failed to decrypt -- regenerating")
        }

        return generateAndStore(prefs)
    }

    private fun generateAndStore(prefs: SharedPreferences): FfiIdentity {
        val identity = FfiIdentity.generate()
        prefs.edit().putString(PREFS_KEY_WRAPPED, KeystoreSecretBox.wrap(KEY_ALIAS, identity.toBytes())).apply()
        return identity
    }
}
