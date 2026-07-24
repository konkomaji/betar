package india.projectmesh.app.messaging

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import india.projectmesh.app.MeshCoordinator
import uniffi.mesh_core.FfiAddMemberOutput
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.FfiMlsGroupHandle
import uniffi.mesh_core.FfiMlsMember
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "GroupMessaging"
private const val GROUP_TTL_HOPS: UByte = 16u
private const val GROUP_EXPIRES_SECONDS = 7L * 24L * 3600L // matches Channel's "durable board" TTL

data class GroupPost(val text: String, val idHex: String)

/** One joined/founded MLS group's live state -- `handle` is a real, mutable `FfiMlsGroupHandle`
 *  (add-member/process-commit/seal/open all advance its epoch), unlike [ChannelSession]'s
 *  immutable derived key. */
class GroupSession(val label: String, internal val handle: FfiMlsGroupHandle) {
    val selectorHex: String = handle.groupSelectorHex()
    val posts = mutableStateListOf<GroupPost>()
    internal val seenEnvelopeIds = mutableSetOf<String>()
}

/**
 * MLS groups (RFC 9420) — `CRYPTOGRAPHY.md` §6, wired via `FfiMlsMember`/`FfiMlsGroupHandle`
 * (`core/src/ffi_groups.rs`, exported earlier this session). Unlike [ChannelMessenger] (anyone
 * with a shared passphrase can join with zero coordination), MLS groups have real membership:
 * joining requires the founder/an existing member to add you from your published `KeyPackage`,
 * producing a `Welcome` only you can use.
 *
 * **Deliberately manual/out-of-band key exchange this pass, same simplification
 * [DirectMessenger] already uses for fingerprints (paste text, no QR code yet):** a `KeyPackage`
 * is shared as hex text for someone to paste into [addMember]; the resulting Commit and Welcome
 * come back as hex text too. **Real gap, not silently glossed over:** distributing the Commit to
 * every *other* existing member (so they stay in sync with the new epoch) has no automated
 * transport yet — this pass only wires application-message traffic (`seal_as_envelope`/
 * `open_from_envelope`, `Addressing::Group`) through the mesh automatically, same as
 * [ChannelMessenger]. Commit distribution to the rest of the group is [processCommit], exposed as
 * a manual paste-and-apply action, not an automatic mesh broadcast.
 *
 * **Not done, stated plainly:** unsigned application content is not a concern here (MLS
 * application messages are inherently signed by the sending member's credential — unlike
 * Broadcast/Channel, this is the one messaging mode that already has real sender authenticity);
 * no member-removal/self-update/external-commit UI (the underlying `groups.rs` doesn't support
 * them yet either); no group/session persistence (lost on restart, same identity-tied gap every
 * other messenger has); no QR-code KeyPackage/Welcome exchange.
 */
class GroupMessenger(private val identity: FfiIdentity, private val coordinator: MeshCoordinator) {
    val sessions = mutableStateListOf<GroupSession>()

    /** A member identity that has published a `KeyPackage` (via [pendingKeyPackageHex]) but not
     *  yet joined a group with it -- consumed by [joinFromWelcome]. Only one pending invite is
     *  tracked at a time; requesting a fresh key package before joining replaces it. */
    private var pendingMember: FfiMlsMember? = null

    fun createGroup(label: String): GroupSession? {
        val handle = try {
            FfiMlsMember(identity).createGroup()
        } catch (e: Exception) {
            Log.w(TAG, "createGroup failed", e)
            return null
        }
        val session = GroupSession(label, handle)
        sessions.add(session)
        return session
    }

    /** This device's `KeyPackage`, hex-encoded, to share out-of-band with whoever will invite it
     *  into their group. Starts (or reuses) the pending member identity that [joinFromWelcome]
     *  later consumes -- calling this again before joining replaces the pending identity with a
     *  fresh one, matching `FfiMlsMember::key_package_bytes`'s "callable repeatedly" contract. */
    fun pendingKeyPackageHex(): String? {
        val member = pendingMember ?: FfiMlsMember(identity).also { pendingMember = it }
        return try {
            member.keyPackageBytes().toHexString()
        } catch (e: Exception) {
            Log.w(TAG, "keyPackageBytes failed", e)
            null
        }
    }

    /** Founder/existing member: add someone from their shared `KeyPackage` hex. Returns the
     *  Commit (for every other existing member) and Welcome (for the new member) as hex to
     *  distribute out-of-band -- see the class doc for why this isn't automatic yet. */
    fun addMember(session: GroupSession, keyPackageHex: String): FfiAddMemberOutput? {
        val bytes = hexToBytes(keyPackageHex) ?: return null
        return try {
            session.handle.addMember(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "addMember failed", e)
            null
        }
    }

    /** The invited member: join using the Welcome hex the founder/admin shared, consuming the
     *  pending identity [pendingKeyPackageHex] created. Null if there's no pending identity (call
     *  [pendingKeyPackageHex] first) or the welcome is malformed/stale. */
    fun joinFromWelcome(label: String, welcomeHex: String): GroupSession? {
        val member = pendingMember ?: run {
            Log.w(TAG, "joinFromWelcome called with no pending key package -- call pendingKeyPackageHex first")
            return null
        }
        val bytes = hexToBytes(welcomeHex) ?: return null
        val handle = try {
            member.joinFromWelcome(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "joinFromWelcome failed", e)
            return null
        }
        pendingMember = null
        val session = GroupSession(label, handle)
        sessions.add(session)
        return session
    }

    /** Any existing member: apply a Commit hex shared out-of-band (e.g. after someone else added
     *  a new member) to stay in sync with the group's current epoch. */
    fun processCommit(session: GroupSession, commitHex: String): Boolean {
        val bytes = hexToBytes(commitHex) ?: return false
        return try {
            session.handle.processCommit(bytes)
            true
        } catch (e: Exception) {
            Log.w(TAG, "processCommit failed", e)
            false
        }
    }

    fun send(session: GroupSession, text: String) {
        val now = nowSeconds()
        val envelopeBytes = try {
            session.handle.sealAsEnvelope(
                text.toByteArray(Charsets.UTF_8),
                2u, // Normal
                GROUP_TTL_HOPS,
                (now + GROUP_EXPIRES_SECONDS).toULong(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "sealAsEnvelope failed", e)
            return
        }
        try {
            coordinator.node().composeLocal(envelopeBytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for group post", e)
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
            if (parsed.addressingTag != 2.toUByte()) continue // not Group
            val targetHex = parsed.addressingTarget?.toHexString() ?: continue
            val session = sessions.find { it.selectorHex == targetHex } ?: continue // not one we're in
            if (!session.seenEnvelopeIds.add(idHex)) continue
            val text = try {
                String(session.handle.openFromEnvelope(bytes), Charsets.UTF_8)
            } catch (e: Exception) {
                continue // wrong epoch, not yet caught up on a Commit, or corrupted -- don't crash the poll loop
            }
            session.posts.add(0, GroupPost(text, parsed.idHex)) // newest first
        }
    }
}

private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

private fun hexToBytes(hex: String): ByteArray? {
    val trimmed = hex.trim()
    if (trimmed.isEmpty() || trimmed.length % 2 != 0) return null
    val out = ByteArray(trimmed.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(trimmed[i * 2], 16)
        val lo = Character.digit(trimmed[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) + lo).toByte()
    }
    return out
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
