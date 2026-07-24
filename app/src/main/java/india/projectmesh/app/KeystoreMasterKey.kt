package india.projectmesh.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "KeystoreMasterKey"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val WRAPPING_KEY_ALIAS = "mesh_master_key_wrap"
private const val PREFS_NAME = "mesh_master_key"
private const val PREFS_KEY_WRAPPED = "master_key_wrapped_b64"

// The pre-Keystore scheme's pref key -- a plaintext base64 master key. Migrated away from, not
// just superseded: an app that already ran the old code has this sitting in SharedPreferences
// as a real, live secret, and simply no longer reading it isn't the same as it being gone.
private const val PREFS_KEY_LEGACY_PLAINTEXT = "master_key_b64"

private const val MASTER_KEY_SIZE = 32
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_BITS = 128

/**
 * Loads-or-creates the 32-byte master key `FfiMeshNode.open` needs, encrypted at rest under a
 * hardware-backed (TEE/StrongBox, device-dependent) Android Keystore AES key that never leaves
 * secure hardware -- closing the gap `MeshCoordinator`'s doc comment flagged since the BLE driver
 * landed: the master key used to be `SecureRandom`-generated and stored as **plain base64 in
 * `SharedPreferences`**, readable by anyone with root or backup-extraction access to app storage
 * -- which, per `docs/PROGRESS.md`'s error-hardening pass, is exactly the access level needed to
 * exploit the (now-fixed) MLS-snapshot allocation bug, and the same access level that makes
 * "encryption at rest" (`core/src/persistence.rs`) meaningless if the key sits in plaintext right
 * next to the encrypted data it protects.
 *
 * **Why key-wrapping, not "use the Keystore key directly":** Android Keystore keys are
 * non-extractable by design -- there is no API to read their raw bytes back out, only to perform
 * `Cipher` operations through the Keystore itself. `FfiMeshNode.open` needs actual raw bytes to
 * hand to Rust's AEAD (Rust has no notion of Android Keystore), so this still generates an
 * ordinary random 32-byte key exactly as before, but now **encrypts it** with a Keystore-backed
 * AES-GCM key before persisting the ciphertext. The wrapping key itself never touches disk or
 * leaves secure hardware as extractable bytes -- an attacker who extracts `SharedPreferences` now
 * gets ciphertext that's useless without also compromising the device's secure hardware, a much
 * higher bar than "read a file."
 *
 * **Honest scope, stated plainly:** `setUserAuthenticationRequired(false)` -- the wrapping key is
 * usable without a biometric/lock-screen prompt, since `MeshRelayService` (a foreground service)
 * needs to open the store automatically in the background, not block on user presence every time
 * the mesh restarts. This protects against **offline extraction** (root, ADB backup, physical
 * storage access while powered off) but not a scenario where the unlocked, running device itself
 * is compromised -- the same threat-model boundary `docs/THREAT-MODEL.md` already draws for
 * on-device secrets generally, not a new limitation introduced here.
 */
object KeystoreMasterKey {

    fun loadOrCreate(context: Context): ByteArray {
        val wrappingKey = getOrCreateWrappingKey()
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
            unwrap(wrappingKey, encoded)?.let { return it }
            Log.w(TAG, "stored wrapped master key failed to decrypt -- regenerating")
        }

        val fresh = ByteArray(MASTER_KEY_SIZE)
        SecureRandom().nextBytes(fresh)
        prefs.edit().putString(PREFS_KEY_WRAPPED, wrap(wrappingKey, fresh)).apply()
        return fresh
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(wrappingKey: SecretKey, plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val combined = cipher.iv + cipher.doFinal(plain) // GCM IV is provider-generated on ENCRYPT_MODE init
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Null on any failure (corrupt data, wrong key after a Keystore reset, etc.) -- caller
     *  regenerates rather than crashing, same defensive posture the old plain-prefs code had. */
    private fun unwrap(wrappingKey: SecretKey, encoded: String): ByteArray? = try {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_SIZE) {
            null
        } else {
            val iv = combined.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ciphertext)
            if (plain.size == MASTER_KEY_SIZE) plain else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "unwrap failed", e)
        null
    }
}
