package app.betar.comm.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A Nostr event (NIP-01: https://github.com/nostr-protocol/nips/blob/master/01.md, fetched and
 * read directly from source before writing this, not reconstructed from memory or a
 * secondary summary).
 *
 * The id-computation serialization is hand-rolled here rather than built with a general JSON
 * library: NIP-01 requires byte-for-byte identical output across implementations (otherwise
 * the id, which is itself the sha256 of this exact string, would not match what other clients
 * and relays compute), and a general-purpose JSON encoder does not guarantee the exact
 * escaping rule this spec mandates (only `\n \" \\ \r \t \b \f` are escaped, nothing else, not
 * even a forward slash, which some JSON encoders escape by default). Handled manually so the
 * output is provably exactly what the spec says, not "probably compatible."
 */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    companion object {
        /** Builds and signs a new event with [keyPair]'s Nostr identity. */
        fun create(keyPair: NostrKeyPair, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = System.currentTimeMillis() / 1000L): NostrEvent {
            val pubkeyHex = keyPair.publicKeyHex
            val serialized = canonicalSerialization(pubkeyHex, createdAt, kind, tags, content)
            val idBytes = sha256(serialized.toByteArray(Charsets.UTF_8))
            val idHex = idBytes.toHexString()

            val secp = Secp256k1.get()
            val auxrand = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val sigBytes = secp.signSchnorr(idBytes, keyPair.privateKey, auxrand)

            return NostrEvent(
                id = idHex,
                pubkey = pubkeyHex,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = sigBytes.toHexString(),
            )
        }

        /** The exact `[0,pubkey,created_at,kind,tags,content]` compact-JSON array the id is a
         *  sha256 of. Public so [verify] can recompute it to check a received event's id. */
        fun canonicalSerialization(pubkeyHex: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val sb = StringBuilder()
            sb.append("[0,")
            sb.append('"').append(pubkeyHex).append('"').append(',')
            sb.append(createdAt).append(',')
            sb.append(kind).append(',')
            sb.append('[')
            tags.forEachIndexed { i, tag ->
                if (i > 0) sb.append(',')
                sb.append('[')
                tag.forEachIndexed { j, value ->
                    if (j > 0) sb.append(',')
                    sb.append('"').append(escapeContent(value)).append('"')
                }
                sb.append(']')
            }
            sb.append("],")
            sb.append('"').append(escapeContent(content)).append('"')
            sb.append(']')
            return sb.toString()
        }

        /** NIP-01's exact escape set, nothing more, nothing less. */
        private fun escapeContent(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) {
                when (c) {
                    '\n' -> sb.append("\\n")
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /** Recomputes [id] from this event's own fields and checks it matches, then verifies [sig]
     *  against [pubkey]. Both checks must pass before any received event is trusted; a relay or
     *  a man-in-the-middle could otherwise hand back a tampered event with a stale/mismatched
     *  id, or a correct id with no valid signature behind it. */
    fun verify(): Boolean {
        val serialized = canonicalSerialization(pubkey, createdAt, kind, tags, content)
        val recomputedId = sha256(serialized.toByteArray(Charsets.UTF_8)).toHexString()
        if (recomputedId != id) return false
        val secp = Secp256k1.get()
        return try {
            secp.verifySchnorr(sig.hexToByteArray(), id.hexToByteArray(), pubkey.hexToByteArray())
        } catch (e: Exception) {
            false
        }
    }
}
