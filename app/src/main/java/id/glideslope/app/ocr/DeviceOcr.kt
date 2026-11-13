// F-09 on the phone: the receipt photo is shrunk like the web does (1568 px, JPEG ≤ 5 MB) and read with ML Kit's bundled
// Latin model, offline. The text goes to the server beside the photo; the server parses and validates it, and only reads
// the photo with its own OCR when that fails.
package id.glideslope.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import id.glideslope.core.OcrLine
import id.glideslope.core.fitSize
import id.glideslope.core.ocrJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class Prepared(val jpeg: ByteArray, val ocrJson: String?)

object DeviceOcr {
    private const val MAX_BYTES = 5 * 1024 * 1024

    /** Null when the photo cannot be read or stays over 5 MB after shrinking. */
    suspend fun prepare(ctx: Context, uri: Uri): Prepared? = withContext(Dispatchers.Default) {
        val bmp = decode(ctx, uri) ?: return@withContext null
        val jpeg = listOf(85, 75, 65).firstNotNullOfOrNull { q ->
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray().takeIf { it.size < MAX_BYTES }
        } ?: return@withContext null
        // a failure here only means the server reads the photo itself
        val json = runCatching { recognize(bmp) }.getOrNull()
        Prepared(jpeg, json)
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1568) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val (w, h) = fitSize(raw.width, raw.height)
        val scaled = if (w == raw.width) raw else Bitmap.createScaledBitmap(raw, w, h, true)
        return if (rotation == 0f) scaled else Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(rotation) }, true)
    }

    private suspend fun recognize(bmp: Bitmap): String? {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { c ->
                client.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) }
            } ?: return null
            val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                val b = l.boundingBox ?: return@mapNotNull null
                OcrLine(l.text, l.confidence.toDouble(), b.left.toDouble(), b.top.toDouble(), b.right.toDouble(), b.bottom.toDouble())
            }
            return ocrJson(bmp.width, lines)
        } finally {
            client.close()
        }
    }
}
