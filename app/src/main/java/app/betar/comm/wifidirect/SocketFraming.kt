package app.betar.comm.wifidirect

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Length-delimited framing for one opaque frame (a `ContactMessage`'s wire bytes, same payload
 * `ble/Fragmentation.kt` chunks for the BLE transport) over a TCP socket stream. Purely
 * mechanical -- this never looks at frame content.
 *
 * Unlike BLE's ATT-MTU-bounded fragmentation, a socket is an ordered, reliable byte stream with
 * no natural message boundary of its own, so the only job here is delimiting one frame from the
 * next: a 4-byte big-endian length prefix (`DataOutputStream.writeInt`/`DataInputStream.readInt`
 * network-byte-order default) followed by that many payload bytes. No chunking or reassembly is
 * needed -- TCP already guarantees in-order, complete delivery within one connection.
 */
object SocketFraming {
    /** Writes one frame. Throws on any I/O failure (caller treats the connection as dead). */
    @Throws(IOException::class)
    fun writeFrame(out: DataOutputStream, frame: ByteArray) {
        out.writeInt(frame.size)
        out.write(frame)
        out.flush()
    }

    fun writeFrame(out: OutputStream, frame: ByteArray) = writeFrame(DataOutputStream(out), frame)

    /**
     * Blocking read of one frame. Returns null on clean stream end (peer closed the socket);
     * throws [IOException] on a malformed length prefix or any other read failure.
     */
    @Throws(IOException::class)
    fun readFrame(input: DataInputStream): ByteArray? {
        val length = try {
            input.readInt()
        } catch (e: EOFException) {
            return null
        }
        if (length < 0 || length > WifiDirectConfig.MAX_FRAME_SIZE) {
            throw IOException("frame length $length out of bounds (max ${WifiDirectConfig.MAX_FRAME_SIZE})")
        }
        val buf = ByteArray(length)
        input.readFully(buf)
        return buf
    }

    fun readFrame(input: InputStream): ByteArray? = readFrame(DataInputStream(input))
}
