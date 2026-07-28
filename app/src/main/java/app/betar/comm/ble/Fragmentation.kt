package app.betar.comm.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "MeshBle"

/** Bytes of our own header prepended to every ATT payload; see the header layout below. */
const val FRAGMENT_HEADER_SIZE = 4

private const val FLAG_FIN: Int = 0x01

/**
 * Link-layer chunking for one opaque frame (a `ContactMessage`'s wire bytes) across BLE ATT
 * writes/notifications. Purely mechanical -- this never looks at frame content.
 *
 * Header (4 bytes, prepended to every fragment):
 * ```
 * byte 0    message_id      (u8, wraps; per-connection outbound counter)
 * byte 1    flags           (bit0 = FIN -- last fragment of this message; rest reserved, must be 0)
 * bytes 2-3 fragment_index  (u16 LE, 0-based)
 * bytes 4.. chunk payload
 * ```
 */
object Fragmentation {
    /**
     * Split `payload` into `<= chunkSize`-byte chunks, each prefixed with the fragment header.
     * `chunkSize` must already have the header size subtracted out by the caller (i.e. it's the
     * usable payload budget per ATT write, not the raw MTU).
     */
    fun encodeFragments(messageId: Byte, payload: ByteArray, chunkSize: Int): List<ByteArray> {
        require(chunkSize > 0) { "chunkSize must be positive, got $chunkSize" }
        val chunks: List<List<Byte>> =
            if (payload.isEmpty()) listOf(emptyList()) else payload.toList().chunked(chunkSize)
        return chunks.mapIndexed { index, chunk ->
            val isLast = index == chunks.size - 1
            val header = ByteBuffer.allocate(FRAGMENT_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.put(messageId)
            header.put((if (isLast) FLAG_FIN else 0).toByte())
            header.putShort(index.toShort())
            header.array() + chunk.toByteArray()
        }
    }
}

/**
 * Reassembles fragmented messages per peer. One instance shared by the driver; callers key
 * reassembly by `peerHandle`, since a given peer's fragments always arrive on the same logical
 * connection (one at a time -- BLE delivers writes/notifications on one connection in order).
 */
class Reassembler {
    private class InProgress(val messageId: Byte) {
        val chunks = ArrayList<ByteArray>()
        var nextExpectedIndex = 0
    }

    private val perPeer = ConcurrentHashMap<ULong, InProgress>()

    /**
     * Offer one received fragment (header + chunk, as it arrived off the wire) for `peerHandle`.
     * Returns the fully reassembled message once the `FIN` fragment arrives, else null.
     */
    fun offer(peerHandle: ULong, raw: ByteArray): ByteArray? {
        if (raw.size < FRAGMENT_HEADER_SIZE) {
            Log.w(TAG, "dropping undersized fragment from peer=$peerHandle (${raw.size}B < header)")
            return null
        }
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val messageId = buf.get()
        val flags = buf.get().toInt()
        val fragmentIndex = buf.short.toInt() and 0xFFFF
        val chunk = raw.copyOfRange(FRAGMENT_HEADER_SIZE, raw.size)

        var state = perPeer[peerHandle]
        if (state == null || state.messageId != messageId) {
            if (state != null) {
                Log.w(
                    TAG,
                    "peer=$peerHandle started message_id=$messageId before finishing " +
                        "message_id=${state.messageId} (${state.chunks.size} fragments buffered) -- discarding partial",
                )
            }
            state = InProgress(messageId)
            perPeer[peerHandle] = state
        }

        if (fragmentIndex != state.nextExpectedIndex) {
            Log.w(
                TAG,
                "peer=$peerHandle message_id=$messageId fragment_index=$fragmentIndex " +
                    "!= expected ${state.nextExpectedIndex} (BLE should deliver in order on one connection)",
            )
        }
        state.chunks.add(chunk)
        state.nextExpectedIndex = fragmentIndex + 1

        if (flags and FLAG_FIN != 0) {
            perPeer.remove(peerHandle)
            val total = state.chunks.sumOf { it.size }
            val out = ByteArray(total)
            var offset = 0
            for (c in state.chunks) {
                c.copyInto(out, offset)
                offset += c.size
            }
            return out
        }
        return null
    }

    /** Drop any in-flight reassembly state for a peer, e.g. on disconnect. */
    fun clear(peerHandle: ULong) {
        perPeer.remove(peerHandle)
    }
}

/** Per-connection outbound message-id counter, wrapping u8 semantics like the header field. */
class MessageIdCounter {
    private val counters = ConcurrentHashMap<ULong, AtomicInteger>()

    fun next(peerHandle: ULong): Byte {
        val counter = counters.computeIfAbsent(peerHandle) { AtomicInteger(0) }
        return (counter.getAndIncrement() and 0xFF).toByte()
    }

    fun clear(peerHandle: ULong) {
        counters.remove(peerHandle)
    }
}
