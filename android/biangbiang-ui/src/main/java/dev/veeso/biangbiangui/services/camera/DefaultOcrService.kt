package dev.veeso.biangbiangui.services.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import kotlinx.coroutines.tasks.await

/**
 * Built-in ML Kit-backed OCR. Used whenever a `LanguageProfile` does not
 * provide an `ocrService`. Behaviour matches the pre-seam recognize path.
 *
 * An ML Kit recognition failure propagates as an exception thrown from
 * [recognize] (via the awaited Task); callers handle it as they did the
 * pre-seam path.
 */
class DefaultOcrService : OcrService {
    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        val bmp = bitmap ?: return emptyList()
        val image = InputImage.fromBitmap(bmp, 0)
        // ML Kit TextRecognizer is Closeable; close it after each call to avoid
        // leaking the native model handle. TODO(Task 6): lift recognizer
        // construction to the pipeline so it is created once per camera session
        // instead of per recognize() call.
        val result = recognizerFor(recognizer).use { it.process(image).await() }
        return result.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { el ->
                el.boundingBox?.let { b ->
                    toTextBox(el.text, b.left, b.top, b.right, b.bottom)
                }
            }
    }

    companion object {
        fun toTextBox(
            text: String,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): OcrTextBox =
            OcrTextBox(text, left, top, right - left, bottom - top)
    }
}
