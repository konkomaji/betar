package app.betar.comm.ui.components

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QR encode/decode for the scan-to-add flow (DESIGN-BRIEF.md §9 screens 9-10). Plain ZXing
 * `core`, not a Google Play Services / MLKit dependency, matching this project's F-Droid
 * distribution goal (docs/DISTRIBUTION.md) -- see build.gradle.kts's comment on the same choice.
 */

/** Renders [text] (a contact's 64-hex-character fingerprint) as a black-on-white QR bitmap. */
fun generateQrBitmap(text: String, sizePx: Int = 512): ImageBitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}

/**
 * CameraX [ImageAnalysis.Analyzer] that decodes a QR code from each preview frame and calls
 * [onDecoded] exactly once with the decoded text, then stops decoding further frames (the caller
 * navigates away on a successful scan, same one-shot behaviour a dedicated scanner activity
 * would have). Reads the Y (luma) plane directly -- CameraX's default analysis output format is
 * YUV_420_888, and ZXing's binarizer only needs luminance, not full colour, to read a QR code.
 */
class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)))
    }
    private val consumed = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (consumed.get()) {
            image.close()
            return
        }
        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            val source = PlanarYUVLuminanceSource(
                yBytes, yPlane.rowStride, image.height, 0, 0, image.width, image.height, false,
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            if (consumed.compareAndSet(false, true)) {
                onDecoded(result.text)
            }
        } catch (_: NotFoundException) {
            // No QR code in this frame -- expected on almost every frame, not an error.
        } catch (_: Exception) {
            // Malformed frame data; skip it and wait for the next one rather than crashing the
            // analyzer thread.
        } finally {
            reader.reset()
            image.close()
        }
    }
}
