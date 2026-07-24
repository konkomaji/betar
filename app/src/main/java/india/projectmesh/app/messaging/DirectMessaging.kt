package india.projectmesh.app.messaging

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import india.projectmesh.app.MeshCoordinator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import uniffi.mesh_core.FfiHandshake
import uniffi.mesh_core.FfiHeader
import uniffi.mesh_core.FfiIdentity
import uniffi.mesh_core.FfiSealed
import uniffi.mesh_core.FfiSession
import uniffi.mesh_core.envelopePack
import uniffi.mesh_core.envelopeUnpack

private const val TAG = "DirectMessaging"
private const val MSG_TYPE_HANDSHAKE: Byte = 0
private const val MSG_TYPE_RATCHET: Byte = 1
private const val FINGERPRINT_BYTES = 32
private const val DIRECT_TTL_HOPS: UByte = 8u
private const val DIRECT_EXPIRES_SECONDS = 72L * 3600L // matches ROUTING-PROTOCOL.md's example 24-72h range

// Fingerprints are public values (already meant to be read aloud/shared), so unlike the master
// key/identity/channel-passphrase stores, this deliberately does NOT go through
// KeystoreSecretBox -- there's no confidentiality property to protect, only "did we already know
// this contact." Plain SharedPreferences is the right amount of mechanism here, not more.
private const val PERSIST_PREFS_NAME = "mesh_contacts"
private const val PERSIST_PREFS_KEY = "contact_fingerprints"
private const val PERSIST_DELIMITER = ","

enum class ContactStatus { NO_SESSION, HANDSHAKING, CONNECTED }

data class DirectChatMessage(val fromMe: Boolean, val text: String, val timestampSeconds: Long)

class Contact(val fingerprintHex: String) {
    var handshake: FfiHandshake? = null
    var session: FfiSession? = null
    val messages = mutableStateListOf<DirectChatMessage>()
    var status by mutableStateOf(ContactStatus.NO_SESSION)
}

/**
 * Direct (1:1) messaging: Noise `XX` handshake -> Double Ratchet, per `docs/CRYPTOGRAPHY.md`
 * §4.1/§5, carried as `Addressing::Direct` envelopes through the same store-and-forward mesh as
 * everything else (`FEATURES.md` §5).
 *
 * **Wire framing, an app-layer concern deliberately not pushed into the Rust core** (which keeps
 * `Envelope.sealed` fully opaque, per `docs/ARCHITECTURE.md` §1): a Direct envelope's `sealed`
 * bytes are `[msg_type: 1 byte][sender_fingerprint: 32 bytes][payload]`. The sender fingerprint
 * is needed because Noise `XX` deliberately hides static identity keys until partway through the
 * handshake (that's the whole point of `XX`'s identity-hiding property, `CRYPTOGRAPHY.md` §4.1)
 * — the *envelope's* `Addressing::Direct` target is the *recipient*, so without this prefix the
 * receiver of a first handshake message would have no way to know which contact it came from.
 * This doesn't newly leak much: Direct addressing already reveals "an envelope for fingerprint X
 * exists" to every relay; naming the sender too is a small addition to that same surface, not a
 * new category of exposure, and application ciphertext integrity is Double-Ratchet-AEAD-backed
 * regardless of whether this routing hint is honest.
 *
 * **Who initiates:** both sides may `addContact()` each other around the same time, so — same
 * pattern already used for the BLE dual-role connect race (`ble/BlePeerRegistry.kt`) — whichever
 * fingerprint sorts lower initiates the Noise handshake; the other waits for message 1.
 *
 * **Receiving is polling-based**, not event-driven: `FfiMeshNode` has no "a new envelope
 * arrived" callback (see its `all_ids_hex`/`get_envelope_hex` doc comments in `ffi_node.rs`), so
 * [pollForNewEnvelopes] must be called periodically (the UI does this the same way it already
 * polls connected-peer count).
 *
 * **Contact list now persists across restarts** (this pass) -- fingerprints are saved to plain
 * `SharedPreferences` (see the file-level constants' comment for why no encryption is needed
 * here) and reloaded on construction. **Sessions and message history still do not persist** --
 * a restored contact starts at `NO_SESSION` and needs a fresh Noise handshake next contact
 * window, same as any other new contact; only the fact that you'd already added them survives.
 * Persisting the live ratchet session itself is a separate, larger task (it's forward-secret key
 * material, not inert metadata like a fingerprint — serializing it to disk is a real security
 * design question, not attempted this pass) — not silently folded into "Direct persists now."
 * No QR-code trust establishment (`CRYPTOGRAPHY.md` §3's ideal is scanning a QR in person — this
 * pass only supports pasting a fingerprint hex, with the safety string still available for
 * spoken/visual comparison); no prekey/async bootstrap wiring (the interactive Noise handshake
 * requires both parties to have a mesh contact window open around the same time —
 * `crypto::prekey`'s X3DH bootstrap for genuinely offline first contact isn't exported over FFI
 * yet).
 */
class DirectMessenger(
    private val context: Context,
    private val identity: FfiIdentity,
    private val coordinator: MeshCoordinator,
) {
    val myFingerprintHex: String = identity.fingerprintHex()
    private val myFingerprintBytes: ByteArray = hexToBytes(myFingerprintHex)

    val contacts = mutableStateListOf<Contact>()
    private val seenEnvelopeIds = mutableSetOf<String>()

    init {
        loadPersistedContacts().forEach { fingerprintHex -> addContactInternal(fingerprintHex) }
    }

    /** Add (or return the existing) contact by their fingerprint hex, kicking off a handshake
     * if we're the side that should initiate. Returns null if `fingerprintHex` isn't a
     * well-formed 64-hex-char fingerprint or is our own. */
    fun addContact(fingerprintHex: String): Contact? {
        val contact = addContactInternal(fingerprintHex) ?: return null
        persistContacts()
        return contact
    }

    private fun addContactInternal(fingerprintHex: String): Contact? {
        val normalized = fingerprintHex.trim().lowercase()
        if (normalized.length != FINGERPRINT_BYTES * 2 || normalized.any { it !in "0123456789abcdef" }) return null
        if (normalized == myFingerprintHex) return null
        contacts.find { it.fingerprintHex == normalized }?.let { return it }

        val contact = Contact(normalized)
        contacts.add(contact)
        maybeInitiateHandshake(contact)
        return contact
    }

    private fun persistContacts() {
        val prefs = context.getSharedPreferences(PERSIST_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PERSIST_PREFS_KEY, contacts.joinToString(PERSIST_DELIMITER) { it.fingerprintHex }).apply()
    }

    private fun loadPersistedContacts(): List<String> {
        val prefs = context.getSharedPreferences(PERSIST_PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PERSIST_PREFS_KEY, null) ?: return emptyList()
        return if (stored.isEmpty()) emptyList() else stored.split(PERSIST_DELIMITER)
    }

    fun sendMessage(contact: Contact, text: String) {
        val session = contact.session
        if (session == null) {
            Log.w(TAG, "sendMessage: no session yet with ${contact.fingerprintHex}")
            return
        }
        try {
            val sealed = session.encrypt(text.toByteArray(Charsets.UTF_8))
            sendFrame(contact.fingerprintHex, MSG_TYPE_RATCHET, packSealed(sealed))
            contact.messages.add(DirectChatMessage(fromMe = true, text = text, timestampSeconds = nowSeconds()))
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage failed to ${contact.fingerprintHex}", e)
        }
    }

    /** Call periodically -- see the class doc's "receiving is polling-based" note. */
    fun pollForNewEnvelopes() {
        val node = coordinator.node()
        for (idHex in node.allIdsHex()) {
            if (!seenEnvelopeIds.add(idHex)) continue
            val bytes = node.getEnvelopeHex(idHex) ?: continue
            handleEnvelope(bytes)
        }
    }

    private fun iAmInitiator(contact: Contact): Boolean = myFingerprintHex < contact.fingerprintHex

    private fun maybeInitiateHandshake(contact: Contact) {
        if (contact.session != null || contact.handshake != null) return
        if (!iAmInitiator(contact)) return // wait for their message 1 instead
        try {
            val hs = FfiHandshake.newInitiator(identity)
            contact.handshake = hs
            contact.status = ContactStatus.HANDSHAKING
            sendFrame(contact.fingerprintHex, MSG_TYPE_HANDSHAKE, hs.writeMessage())
        } catch (e: Exception) {
            Log.w(TAG, "failed to start handshake with ${contact.fingerprintHex}", e)
        }
    }

    private fun sendFrame(recipientFingerprintHex: String, msgType: Byte, payload: ByteArray) {
        val frame = ByteArray(1 + FINGERPRINT_BYTES + payload.size)
        frame[0] = msgType
        myFingerprintBytes.copyInto(frame, 1)
        payload.copyInto(frame, 1 + FINGERPRINT_BYTES)

        val bytes = envelopePack(
            addressingTag = 3u, // Direct
            addressingTarget = hexToBytes(recipientFingerprintHex),
            priorityTag = 2u, // Normal
            ttlHops = DIRECT_TTL_HOPS,
            expiresAt = (nowSeconds() + DIRECT_EXPIRES_SECONDS).toULong(),
            sealed = frame,
        )
        try {
            coordinator.node().composeLocal(bytes, nowSeconds().toULong())
        } catch (e: Exception) {
            Log.w(TAG, "composeLocal failed for direct frame to $recipientFingerprintHex", e)
        }
    }

    private fun handleEnvelope(bytes: ByteArray) {
        val parsed = try {
            envelopeUnpack(bytes)
        } catch (e: Exception) {
            return
        }
        if (parsed.addressingTag != 3.toUByte()) return // not Direct
        if (parsed.addressingTarget?.contentEquals(myFingerprintBytes) != true) return // not addressed to me

        val sealed = parsed.sealed
        if (sealed.size < 1 + FINGERPRINT_BYTES) return
        val msgType = sealed[0]
        val senderFingerprintHex = bytesToHex(sealed.copyOfRange(1, 1 + FINGERPRINT_BYTES))
        if (senderFingerprintHex == myFingerprintHex) return // our own message, seen again via gossip
        val payload = sealed.copyOfRange(1 + FINGERPRINT_BYTES, sealed.size)

        val contact = contacts.find { it.fingerprintHex == senderFingerprintHex }
            ?: Contact(senderFingerprintHex).also { contacts.add(it) }

        when (msgType) {
            MSG_TYPE_HANDSHAKE -> handleHandshakeFrame(contact, payload)
            MSG_TYPE_RATCHET -> handleRatchetFrame(contact, payload)
            else -> Log.w(TAG, "unknown direct message type $msgType from $senderFingerprintHex")
        }
    }

    private fun handleHandshakeFrame(contact: Contact, message: ByteArray) {
        try {
            when {
                contact.session != null -> return // already connected; ignore stray handshake bytes
                contact.handshake == null -> {
                    // Their message 1 ("-> e") -- we're the responder.
                    val hs = FfiHandshake.newResponder(identity)
                    contact.handshake = hs
                    contact.status = ContactStatus.HANDSHAKING
                    hs.readMessage(message)
                    val result = hs.finishAsResponder()
                    contact.session = result.session
                    contact.handshake = null
                    contact.status = ContactStatus.CONNECTED
                    sendFrame(contact.fingerprintHex, MSG_TYPE_HANDSHAKE, result.messageToSend)
                }
                iAmInitiator(contact) -> {
                    // Their one follow-up message, completing our initiator side. `handshake`
                    // is provably non-null here (mutually exclusive with the `== null` branch
                    // above), but guarded rather than force-unwrapped -- defense in depth so a
                    // future edit that breaks that invariant logs-and-drops via the catch below
                    // instead of crashing the app.
                    val hs = contact.handshake
                        ?: throw IllegalStateException("handshake missing while completing as initiator")
                    contact.session = hs.finishAsInitiator(message)
                    contact.handshake = null
                    contact.status = ContactStatus.CONNECTED
                }
                else -> {
                    // We're the responder mid-exchange -- feed message 3 ("-> s, se").
                    val hs = contact.handshake
                        ?: throw IllegalStateException("handshake missing mid-exchange")
                    hs.readMessage(message)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "handshake step failed with ${contact.fingerprintHex}", e)
        }
    }

    private fun handleRatchetFrame(contact: Contact, payload: ByteArray) {
        val session = contact.session
        if (session == null) {
            Log.w(TAG, "ratchet frame from ${contact.fingerprintHex} but no session yet -- dropping")
            return
        }
        val sealed = unpackSealed(payload) ?: return
        try {
            val plaintext = session.decrypt(sealed)
            contact.messages.add(
                DirectChatMessage(fromMe = false, text = String(plaintext, Charsets.UTF_8), timestampSeconds = nowSeconds()),
            )
        } catch (e: Exception) {
            Log.w(TAG, "decrypt failed from ${contact.fingerprintHex}", e)
        }
    }
}

private fun packSealed(sealed: FfiSealed): ByteArray {
    val buf = ByteBuffer.allocate(FINGERPRINT_BYTES + 4 + 4 + sealed.ciphertext.size).order(ByteOrder.LITTLE_ENDIAN)
    buf.put(sealed.header.dhPub)
    buf.putInt(sealed.header.pn.toInt())
    buf.putInt(sealed.header.n.toInt())
    buf.put(sealed.ciphertext)
    return buf.array()
}

private fun unpackSealed(bytes: ByteArray): FfiSealed? {
    val headerSize = FINGERPRINT_BYTES + 4 + 4
    if (bytes.size < headerSize) return null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val dhPub = ByteArray(FINGERPRINT_BYTES)
    buf.get(dhPub)
    val pn = buf.int.toUInt()
    val n = buf.int.toUInt()
    val ciphertext = ByteArray(bytes.size - headerSize)
    buf.get(ciphertext)
    return FfiSealed(FfiHeader(dhPub, pn, n), ciphertext)
}

private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte() }

private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L
