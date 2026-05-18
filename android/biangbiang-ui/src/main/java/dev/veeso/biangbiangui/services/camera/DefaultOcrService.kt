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
 */
class DefaultOcrService : OcrService {
    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        val bmp = bitmap ?: return emptyList()
        val image = InputImage.fromBitmap(bmp, 0)
        val result = recognizerFor(recognizer).process(image).await()
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
            left: Int, top: Int, right: Int, bottom: Int,
        ): OcrTextBox =
            OcrTextBox(text, left, top, right - left, bottom - top)
    }
}
