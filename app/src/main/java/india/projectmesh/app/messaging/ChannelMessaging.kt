package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.FfiChannel
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "ChannelMessaging"
private const val CHANNEL_TTL_HOPS: UByte = 8u

// Channels are meant to behave like a durable community board (a relief camp's "north-gate-42"
// board someone might check hours later), not ephemeral chat -- longer TTL than plain broadcast's.
private const val CHANNEL_EXPIRES_SECONDS = 7L * 24L * 3600L

data class ChannelPost(val text: String, val idHex: String)

/**
 * One joined channel's live state. `channel` and `selectorHex` are fixed for the session's
 * lifetime (re-deriving from the same passphrase always yields the same pair, per
 * `crate::crypto::channel::Channel::from_passphrase`'s determinism) -- only `posts` mutates.
 */
class ChannelSession(val label: String, internal val channel: FfiChannel) {
    val selectorHex: String = channel.selectorHex()
    val posts = mutableStateListOf<ChannelPost>()
    internal val seenEnvelopeIds = mutableSetOf<String>()
}

/**
 * Passphrase-derived shared channels -- `CRYPTOGRAPHY.md` §6, `ROUTING-PROTOCOL.md` §5's
 * `Addressing::Channel` row. No owner, no server, no membership list: anyone who knows the
 * passphrase (spoken aloud, "the channel is north-gate-42") derives the identical key + routing
 * selector independently and can post/read, with zero other coordination.
 *
 * Wired via `FfiChannel` (`core/src/ffi.rs`), exported over UniFFI earlier this session. Reuses
 * `envelopePack`/`envelopeUnpack`'s existing `addressingTag=1` path rather than any new
 * pack/unpack function -- `FfiChannel::seal`'s output becomes `sealed`, `selectorHex`'s decoded
 * bytes become `addressingTarget`, exactly like every other addressing kind already works. See
 * `core/src/ffi.rs`'s module doc comment for why no new Rust code was needed for this.
 *
 * **Supports multiple simultaneously-joined channels** (a relief coordinator might track both
 * "north-gate-42" and "relief-camp-1" at once) -- same multi-session shape [DirectMessenger] uses
 * for contacts, not [BroadcastMessenger]'s single global feed, since a channel is something you
 * explicitly join by passphrase rather than something that's just always there.
 *
 * **Not done, stated plainly:** unsigned -- same `FfiIdentity.sign`-not-exported-yet gap every
 * other messaging feature in this app already has. Joined channels are session-only, not
 * persisted (lost on process restart, mirrors identity's own non-persistence). No leave/forget
 * action, only join -- re-entering the same passphrase is idempotent (returns the existing
 * session) rather than duplicating it, but there's no way to remove one from the list.
 */
class ChannelMessenger(private val coordinator: MeshCoordinator) {
    val sessions = mutableStateListOf<ChannelSession>()

    /** Derive and join a channel from a passphrase. Idempotent -- re-joining the same passphrase
     *  returns the already-joined session rather than creating a duplicate. Null on a blank
     *  passphrase or an FFI-layer failure (logged, not surfaced as a crash). */
    fun join(passphrase: String): ChannelSession? {
        val trimmed = passphrase.trim()
        if (trimmed.isEmpty()) return null
        val channel = try {
            FfiChannel.fromPassphrase(trimmed)
        } catch (e: Exception) {
            Log.w(TAG, "FfiChannel.fromPassphrase failed", e)
            return null
        }
        val selectorHex = channel.selectorHex()
        sessions.find { it.selectorHex == selectorHex }?.let { return it }
        val session = ChannelSession(trimmed, channel)
        sessions.add(session)
        return session
    }

    fun send(session: ChannelSession, text: String) {
        val sealed = try {
            session.channel.seal(text.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "channel seal failed", e)
            return
        }
        val selectorBytes = hexToBytes(session.selectorHex)
        if (selectorBytes == null) {
            Log.w(TAG, "selectorHex failed to decode -- this should never happen (it's our own output)")
            return
        }
        val now = nowSeconds()
        val bytes = envelopePack(
            addressingTag = 1u, // Channel
            addressingTarget = selectorBytes,
            priorityTag = 2u, // Normal
            ttlHops = CHANNEL_TTL_HOPS,
            expiresAt = (now + CHANNEL_EXPIRES_SECONDS).toULong(),
            sealed = sealed,
        )
        try {
            coordinator.node().composeLocal(bytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for channel post", e)
        }
    }

    /** Call periodically -- see [DirectMessenger]'s class doc for why polling, not a callback. */
    fun pollForNewPosts() {
        if (sessions.isEmpty()) return
        val node = coordinator.node()
        for (idHex in node.allIdsHex()) {
            val bytes = node.getEnvelopeHex(idHex) ?: continue
            val parsed = try {
                envelopeUnpack(bytes)
            } catch (e: Exception) {
                continue
            }
            if (parsed.addressingTag != 1.toUByte()) continue // not Channel
            val targetHex = parsed.addressingTarget?.toHexString() ?: continue
            val session = sessions.find { it.selectorHex == targetHex } ?: continue // not one we've joined
            if (!session.seenEnvelopeIds.add(idHex)) continue
            val text = try {
                String(session.channel.open(parsed.sealed), Charsets.UTF_8)
            } catch (e: Exception) {
                // Selector match already makes this practically unreachable (32-byte selector
                // space), but don't trust that instead of handling it -- corrupt/foreign data on
                // a matching selector shouldn't crash the poll loop.
                continue
            }
            session.posts.add(0, ChannelPost(text, parsed.idHex)) // newest first
        }
    }
}

private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

private fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(hex[i * 2], 16)
        val lo = Character.digit(hex[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) return null // invalid hex digit -- don't silently produce garbage bytes
        out[i] = ((hi shl 4) + lo).toByte()
    }
    return out
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
