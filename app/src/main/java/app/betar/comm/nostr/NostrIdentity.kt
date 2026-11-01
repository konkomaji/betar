package app.betar.comm.nostr

import android.content.Context
import android.content.SharedPreferences
import app.betar.comm.KeystoreSecretBox
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

private const val KEY_ALIAS = "nostr_identity_wrap"
private const val PREFS_NAME = "betar_nostr_identity"
private const val PREFS_KEY_WRAPPED = "privkey_wrapped_b64"
private const val PRIVKEY_SIZE = 32

/**
 * A secp256k1/BIP-340 keypair for the Nostr relay bridge, deliberately separate from this
 * app's own mesh identity ([app.betar.comm.KeystoreIdentityStore], Ed25519 + X25519, see
 * `core/src/identity.rs`). Nostr mandates Schnorr signatures over secp256k1
 * (https://bips.xyz/340); that is a different curve and signature scheme entirely from the
 * mesh identity's, so the two cannot share key material. There is no cryptographic link
 * between a user's mesh identity and their Nostr identity: anyone bridging content chooses to
 * trust that link only insofar as they trust this device did the bridging honestly, the same
 * trust model Bitchat's own Nostr bridge has (this is the real reason Betar's inspiration
 * bridges to Nostr rather than inventing its own relay protocol: the signature scheme is
 * already settled and widely implemented, not something to relitigate here).
 *
 * Stored Keystore-wrapped via [KeystoreSecretBox], the same pattern and the same underlying
 * Android Keystore-backed AES key wrapping as the mesh identity, under a distinct alias/prefs
 * name so the two never collide.
 */
class NostrKeyPair(val privateKey: ByteArray, val publicKeyXOnly: ByteArray) {
    val publicKeyHex: String get() = publicKeyXOnly.toHexString()
}

object NostrIdentity {

    fun loadOrCreate(context: Context): NostrKeyPair {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(PREFS_KEY_WRAPPED, null)?.let { encoded ->
            KeystoreSecretBox.unwrap(KEY_ALIAS, encoded, PRIVKEY_SIZE)?.let { privkey ->
                return keyPairFrom(privkey)
            }
        }
        return generateAndStore(prefs)
    }

    private fun generateAndStore(prefs: SharedPreferences): NostrKeyPair {
        val secp = Secp256k1.get()
        val random = SecureRandom()
        var privkey: ByteArray
        // secKeyVerify rejects zero and values >= curve order; regenerating on that
        // vanishingly rare case is simpler and just as correct as clamping.
        do {
            privkey = ByteArray(PRIVKEY_SIZE).also { random.nextBytes(it) }
        } while (!secp.secKeyVerify(privkey))

        prefs.edit().putString(PREFS_KEY_WRAPPED, KeystoreSecretBox.wrap(KEY_ALIAS, privkey)).apply()
        return keyPairFrom(privkey)
    }

    private fun keyPairFrom(privkey: ByteArray): NostrKeyPair {
        val secp = Secp256k1.get()
        // pubkeyCreate returns the uncompressed 65-byte point (0x04 prefix, 32-byte X,
        // 32-byte Y). Nostr/BIP-340 public keys are x-only: just the 32-byte X coordinate,
        // bytes[1..33).
        val uncompressed = secp.pubkeyCreate(privkey)
        val xOnly = uncompressed.copyOfRange(1, 33)
        return NostrKeyPair(privkey, xOnly)
    }
}

// `"%02x".format(byte)` is a known Java/Kotlin trap: `Formatter`'s `%x` sign-extends a negative
// `Byte` (anything 0x80-0xFF) before converting, producing an 8-character result instead of 2.
// Masking to `Int and 0xFF` first avoids that ambiguity entirely rather than trusting it won't
// bite, correctness-critical here since every id/pubkey/signature in this file is hex.
fun ByteArray.toHexString(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun String.hexToByteArray(): ByteArray =
    ByteArray(length / 2) { i -> ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte() }
