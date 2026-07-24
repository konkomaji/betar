package india.projectmesh.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "KeystoreSecretBox"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val GCM_IV_SIZE = 12
private const val GCM_TAG_BITS = 128

/**
 * Generic Android-Keystore-backed encrypt/decrypt for an arbitrary byte secret, keyed by a
 * caller-chosen Keystore alias. Extracted once a second real caller needed the exact same
 * wrap/unwrap logic (the master key first, then identity persistence) rather than duplicating
 * it -- same "share the primitive" call this project already made for `crypto::passphrase`
 * (shared between duress and Channels) and `crypto::padding` (shared once a second call site
 * existed).
 *
 * **Why key-wrapping, not "use the Keystore key directly" for the secret itself:** Android
 * Keystore keys are non-extractable by design -- there's no API to read their raw bytes back
 * out, only to perform `Cipher` operations through the Keystore. Callers here need actual raw
 * bytes handed to Rust (Rust has no notion of Android Keystore), so each caller still generates
 * or holds an ordinary byte secret and uses this object to **encrypt** it with a Keystore-backed
 * AES-GCM key before persisting the ciphertext. The wrapping key itself never leaves secure
 * hardware as extractable bytes.
 *
 * **`setUserAuthenticationRequired(false)`, deliberately, for every caller:** these keys back
 * things (the mesh master key, the device identity) that `MeshRelayService`, a foreground
 * service, needs to use automatically in the background -- blocking on a biometric/lock-screen
 * prompt every time would break that. This protects against **offline extraction** (root, ADB
 * backup, physical storage access while powered off), not a scenario where the unlocked, running
 * device itself is compromised -- the same threat-model boundary `docs/THREAT-MODEL.md` already
 * draws for on-device secrets generally, not a new limitation introduced here.
 */
object KeystoreSecretBox {
    fun wrap(keyAlias: String, plain: ByteArray): String {
        val key = getOrCreateWrappingKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val combined = cipher.iv + cipher.doFinal(plain) // GCM IV is provider-generated on ENCRYPT_MODE init
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Null on any failure (corrupt data, wrong length after decrypt, Keystore reset, etc.) --
     *  callers regenerate rather than crash. `expectedLength` is an optional integrity check for
     *  fixed-size secrets (32-byte master key, 64-byte identity); pass `null` for variable-length
     *  data (e.g. a joined-channels list) where there's no fixed size to check against. */
    fun unwrap(keyAlias: String, encoded: String, expectedLength: Int? = null): ByteArray? = try {
        val key = getOrCreateWrappingKey(keyAlias)
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_SIZE) {
            null
        } else {
            val iv = combined.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ciphertext)
            if (expectedLength == null || plain.size == expectedLength) plain else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "unwrap failed for alias $keyAlias", e)
        null
    }

    private fun getOrCreateWrappingKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }
}
