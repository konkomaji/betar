package app.betar.comm

import android.content.Context
import android.util.Log
import java.security.SecureRandom

private const val TAG = "KeystoreMasterKey"
private const val KEY_ALIAS = "mesh_master_key_wrap"
private const val PREFS_NAME = "mesh_master_key"
private const val PREFS_KEY_WRAPPED = "master_key_wrapped_b64"

// The pre-Keystore scheme's pref key -- a plaintext base64 master key. Migrated away from, not
// just superseded: an app that already ran the old code has this sitting in SharedPreferences
// as a real, live secret, and simply no longer reading it isn't the same as it being gone.
private const val PREFS_KEY_LEGACY_PLAINTEXT = "master_key_b64"

private const val MASTER_KEY_SIZE = 32

/**
 * Loads-or-creates the 32-byte master key `FfiMeshNode.open` needs, encrypted at rest via
 * [KeystoreSecretBox] -- closing the gap `MeshCoordinator`'s doc comment flagged since the BLE
 * driver landed: the master key used to be `SecureRandom`-generated and stored as **plain base64
 * in `SharedPreferences`**, readable by anyone with root or backup-extraction access to app
 * storage -- which, per `docs/PROGRESS.md`'s error-hardening pass, is exactly the access level
 * needed to exploit the (now-fixed) MLS-snapshot allocation bug, and the same access level that
 * makes "encryption at rest" (`core/src/persistence.rs`) meaningless if the key sits in plaintext
 * right next to the encrypted data it protects. See [KeystoreSecretBox]'s doc comment for the
 * full key-wrapping / no-user-authentication reasoning, shared with [KeystoreIdentityStore].
 */
object KeystoreMasterKey {

    fun loadOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // One-time migration cleanup: an app that ran the pre-Keystore build has a real
        // plaintext secret sitting here, already unused by this code (it reads PREFS_KEY_WRAPPED
        // now, not this), but "unused" isn't "gone" -- wipe it rather than leave it as a live
        // leftover for anyone with storage access to read.
        if (prefs.contains(PREFS_KEY_LEGACY_PLAINTEXT)) {
            Log.i(TAG, "removing leftover plaintext master key from the pre-Keystore scheme")
            prefs.edit().remove(PREFS_KEY_LEGACY_PLAINTEXT).apply()
        }

        prefs.getString(PREFS_KEY_WRAPPED, null)?.let { encoded ->
            KeystoreSecretBox.unwrap(KEY_ALIAS, encoded, MASTER_KEY_SIZE)?.let { return it }
            Log.w(TAG, "stored wrapped master key failed to decrypt -- regenerating")
        }

        val fresh = ByteArray(MASTER_KEY_SIZE)
        SecureRandom().nextBytes(fresh)
        prefs.edit().putString(PREFS_KEY_WRAPPED, KeystoreSecretBox.wrap(KEY_ALIAS, fresh)).apply()
        return fresh
    }
}
