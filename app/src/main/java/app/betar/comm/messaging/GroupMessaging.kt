package app.betar.comm.messaging

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import app.betar.comm.KeystoreMasterKey
import app.betar.comm.KeystoreSecretBox
import app.betar.comm.MeshCoordinator
import java.io.File
import uniffi.mesh_core.FfiAddMemberOutput
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.FfiMlsGroupHandle
import uniffi.mesh_core.FfiMlsMember
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "GroupMessaging"
private const val GROUP_TTL_HOPS: UByte = 16u
private const val GROUP_EXPIRES_SECONDS = 7L * 24L * 3600L // matches Channel's "durable board" TTL

// Persistence: group snapshots are AEAD-sealed files (`FfiMlsGroupHandle`'s own format), keyed by
// this app's shared master key (same one `FfiMeshNode.open` uses -- one already-Keystore-wrapped
// secret, not a new one per group). Each group's MLS *signer* is a distinct keypair though (a
// fresh `FfiMlsMember` per `createGroup`/`joinFromWelcome` call), so signers are Keystore-wrapped
// individually under one shared wrapping-key alias, looked up by group ID. Labels and the list of
// known group IDs are plain (metadata, not secret), same as Direct's contact-fingerprint list.
private const val GROUP_SIGNER_KEY_ALIAS = "mesh_group_signers_wrap"
private const val GROUP_PREFS_NAME = "mesh_groups"
private const val GROUP_IDS_PREFS_KEY = "group_ids"
private const val GROUP_IDS_DELIMITER = ","

data class GroupPost(val text: String, val idHex: String)

/** One joined/founded MLS group's live state -- `handle` is a real, mutable `FfiMlsGroupHandle`
 *  (add-member/process-commit/seal/open all advance its epoch), unlike [ChannelSession]'s
 *  immutable derived key. */
class GroupSession(val label: String, internal val handle: FfiMlsGroupHandle) {
    val selectorHex: String = handle.groupSelectorHex()
    val groupIdHex: String = handle.groupIdHex()
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
 * **Group/session persistence, wired this pass:** each `createGroup`/`joinFromWelcome` call
 * makes a *fresh* `FfiMlsMember` (a distinct MLS signer per group, not one shared per app
 * identity) via `FfiMlsMember(identity)` -- so persistence captures that group's signer bytes
 * (`FfiMlsMember::signerBytes`, Keystore-wrapped) alongside an AEAD-sealed group snapshot
 * (`FfiMlsGroupHandle::snapshotToDisk`, keyed by this app's shared master key -- same one
 * `FfiMeshNode.open` already uses, not a new secret per group). Snapshotted after every call that
 * can advance the group's epoch/tree/ratchet state: creation, `addMember`, `processCommit`,
 * `send`, and receiving a post. Restored in [init] via `FfiMlsMember::fromIdentityAndSigner` +
 * `FfiMlsGroupHandle::loadGroupFromDisk`.
 *
 * **Not done, stated plainly:** unsigned application content is not a concern here (MLS
 * application messages are inherently signed by the sending member's credential — unlike
 * Broadcast/Channel, this is the one messaging mode that already has real sender authenticity);
 * no member-removal/self-update/external-commit UI (the underlying `groups.rs` doesn't support
 * them yet either); no QR-code KeyPackage/Welcome exchange.
 */
class GroupMessenger(
    private val context: Context,
    private val identity: FfiIdentity,
    private val coordinator: MeshCoordinator,
) {
    val sessions = mutableStateListOf<GroupSession>()

    /** A member identity that has published a `KeyPackage` (via [pendingKeyPackageHex]) but not
     *  yet joined a group with it -- consumed by [joinFromWelcome]. Only one pending invite is
     *  tracked at a time; requesting a fresh key package before joining replaces it. */
    private var pendingMember: FfiMlsMember? = null

    init {
        loadPersistedSessions().forEach { sessions.add(it) }
    }

    fun createGroup(label: String): GroupSession? {
        val member = FfiMlsMember(identity)
        val signerBytes = try {
            member.signerBytes()
        } catch (e: Exception) {
            Log.w(TAG, "signerBytes failed before createGroup", e)
            return null
        }
        val handle = try {
            member.createGroup()
        } catch (e: Exception) {
            Log.w(TAG, "createGroup failed", e)
            return null
        }
        val session = GroupSession(label, handle)
        sessions.add(session)
        persistSignerAndGroupId(session, signerBytes)
        snapshotSession(session)
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
        val output = try {
            session.handle.addMember(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "addMember failed", e)
            return null
        }
        snapshotSession(session) // addMember already merged the commit locally
        return output
    }

    /** The invited member: join using the Welcome hex the founder/admin shared, consuming the
     *  pending identity [pendingKeyPackageHex] created. Null if there's no pending identity (call
     *  [pendingKeyPackageHex] first) or the welcome is malformed/stale. */
    fun joinFromWelcome(label: String, welcomeHex: String): GroupSession? {
        val member = pendingMember ?: run {
            Log.w(TAG, "joinFromWelcome called with no pending key package -- call pendingKeyPackageHex first")
            return null
        }
        val signerBytes = try {
            member.signerBytes()
        } catch (e: Exception) {
            Log.w(TAG, "signerBytes failed before joinFromWelcome", e)
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
        persistSignerAndGroupId(session, signerBytes)
        snapshotSession(session)
        return session
    }

    /** Any existing member: apply a Commit hex shared out-of-band (e.g. after someone else added
     *  a new member) to stay in sync with the group's current epoch. */
    fun processCommit(session: GroupSession, commitHex: String): Boolean {
        val bytes = hexToBytes(commitHex) ?: return false
        return try {
            session.handle.processCommit(bytes)
            snapshotSession(session)
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
        snapshotSession(session) // sealAsEnvelope already advanced the ratchet state
        try {
            coordinator.node().composeLocal(envelopeBytes, now.toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for group post", e)
        }
    }

    private fun snapshotPath(groupIdHex: String): String = File(context.filesDir, "mesh_group_snapshot_$groupIdHex.bin").absolutePath

    private fun snapshotSession(session: GroupSession) {
        try {
            session.handle.snapshotToDisk(snapshotPath(session.groupIdHex), KeystoreMasterKey.loadOrCreate(context))
        } catch (e: Exception) {
            Log.w(TAG, "snapshotToDisk failed for group ${session.groupIdHex}", e)
        }
    }

    private fun persistSignerAndGroupId(session: GroupSession, signerBytes: ByteArray) {
        try {
            val prefs = context.getSharedPreferences(GROUP_PREFS_NAME, Context.MODE_PRIVATE)
            val ids = loadPersistedGroupIds(prefs).toMutableList()
            if (session.groupIdHex !in ids) ids.add(session.groupIdHex)
            prefs.edit()
                .putString(GROUP_IDS_PREFS_KEY, ids.joinToString(GROUP_IDS_DELIMITER))
                .putString("label_${session.groupIdHex}", session.label)
                .putString("signer_${session.groupIdHex}", KeystoreSecretBox.wrap(GROUP_SIGNER_KEY_ALIAS, signerBytes))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "failed to persist signer/group id for ${session.groupIdHex}", e)
        }
    }

    private fun loadPersistedGroupIds(prefs: android.content.SharedPreferences): List<String> {
        val stored = prefs.getString(GROUP_IDS_PREFS_KEY, null) ?: return emptyList()
        return if (stored.isEmpty()) emptyList() else stored.split(GROUP_IDS_DELIMITER)
    }

    /** Restore every persisted group: reconstruct each group's distinct MLS signer
     *  (`FfiMlsMember::fromIdentityAndSigner`) and load its snapshot. A group that fails to
     *  restore (corrupt snapshot, wrong master key after a Keystore reset, etc.) is logged and
     *  skipped rather than blocking every other group from loading. */
    private fun loadPersistedSessions(): List<GroupSession> {
        val prefs = context.getSharedPreferences(GROUP_PREFS_NAME, Context.MODE_PRIVATE)
        val masterKey = KeystoreMasterKey.loadOrCreate(context)
        return loadPersistedGroupIds(prefs).mapNotNull { groupIdHex ->
            try {
                val label = prefs.getString("label_$groupIdHex", groupIdHex) ?: groupIdHex
                val wrappedSigner = prefs.getString("signer_$groupIdHex", null)
                    ?: return@mapNotNull null.also { Log.w(TAG, "no stored signer for group $groupIdHex -- skipping") }
                val signerBytes = KeystoreSecretBox.unwrap(GROUP_SIGNER_KEY_ALIAS, wrappedSigner)
                    ?: return@mapNotNull null.also { Log.w(TAG, "signer for group $groupIdHex failed to decrypt -- skipping") }
                val member = FfiMlsMember.fromIdentityAndSigner(identity, signerBytes)
                val handle = member.loadGroupFromDisk(snapshotPath(groupIdHex), masterKey, groupIdHex)
                GroupSession(label, handle)
            } catch (e: Exception) {
                Log.w(TAG, "failed to restore group $groupIdHex -- skipping", e)
                null
            }
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
            snapshotSession(session) // openFromEnvelope already advanced the ratchet state
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
