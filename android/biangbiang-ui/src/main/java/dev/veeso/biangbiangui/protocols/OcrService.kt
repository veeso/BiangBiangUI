package dev.veeso.biangbiangui.protocols

import android.graphics.Bitmap
import dev.veeso.biangbiangui.config.OcrRecognizer

/**
 * A recognised text run in image-pixel coordinates (top-left origin).
 * This is the **raw** OCR result — no transliteration. The library maps
 * these to [dev.veeso.biangbiangui.services.camera.OcrBox] after
 * transliteration; implementors of [OcrService] return this type only.
 *
 * Fields mirror iOS `OCRTextBox.rect`; discrete Int fields are used in
 * place of `CGRect` to keep the protocols package framework-free.
 */
data class OcrTextBox(
    val text: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Pluggable OCR backend. The library normalises the frame to an upright
 * still [Bitmap]; the service only performs raw recognition and returns
 * boxes in image-pixel space. Rotation, throttle, debounce, spacing and
 * transliteration stay library-owned. Mirrors iOS `OCRService`.
 *
 * @param bitmap The upright, normalised frame bitmap. `null` is only passed
 *   by unit-test stubs; production callers always provide a real bitmap.
 *   Implementations should return an empty list when `bitmap` is `null`.
 */
fun interface OcrService {
    suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox>
}
