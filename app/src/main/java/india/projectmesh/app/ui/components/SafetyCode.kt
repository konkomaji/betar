package india.projectmesh.app.ui.components

import java.security.MessageDigest

/**
 * Human-comparable identity code (DESIGN-BRIEF.md §9 screen 10's "short code in very large type").
 * Two parts, both deterministically derived from a fingerprint hex string, never from anything
 * exchanged over the wire during verification itself:
 *  - a 6-digit number, unique per identity (not shared/paired -- each person has their own)
 *  - 3 emoji, the actual side-by-side comparison target: if two devices show the same fingerprint
 *    (the genuine case), both independently derive the same 3 emoji, so a match confirms nobody
 *    substituted a different identity in transit.
 * UI-layer only, matching this app's existing safety-string approach (see VerifyInPersonScreen's
 * own doc comment) -- SHA-256 here is a mnemonic-derivation choice, not a new security boundary;
 * the fingerprint it's derived from is already the cryptographically meaningful value.
 */
object SafetyCode {
    // 50 entries, no skin-tone/gender/flag variants (nothing to accidentally misrender or
    // mis-render as a different value across devices), all pre-Unicode-10 so they render
    // correctly back to this project's API 26 floor.
    private val EMOJI = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰",
        "🦊", "🐻", "🐼", "🐨", "🐯",
        "🦁", "🐮", "🐷", "🐸", "🐵",
        "🐔", "🐧", "🐦", "🦆", "🦉",
        "🐴", "🦋", "🐢", "🐙", "🦀",
        "🐬", "🐳", "🐘", "🦒", "🐫",
        "⭐", "🌙", "☀", "⚡", "🔥",
        "💧", "🌈", "🍎", "🍌", "🍇",
        "🍉", "🍊", "🍓", "🍑", "🥝",
        "🌽", "🎈", "🎲", "🚗", "🔑",
    )

    private fun digest(fingerprintHex: String): ByteArray {
        val bytes = ByteArray(fingerprintHex.length / 2) { i ->
            fingerprintHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun sixDigitCode(fingerprintHex: String): String {
        val d = digest(fingerprintHex)
        val v = ((d[0].toInt() and 0xFF) shl 16) or ((d[1].toInt() and 0xFF) shl 8) or (d[2].toInt() and 0xFF)
        return "%06d".format(v % 1_000_000)
    }

    /** Deliberately reads bytes 3..5 of the same digest, not 0..2 (the digit derivation above),
     * so the digits and emoji don't trivially correlate byte-for-byte. */
    fun threeEmoji(fingerprintHex: String): List<String> {
        val d = digest(fingerprintHex)
        return (3..5).map { EMOJI[(d[it].toInt() and 0xFF) % EMOJI.size] }
    }
}
