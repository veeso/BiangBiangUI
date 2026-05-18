package dev.veeso.biangbiangui.protocols

import android.graphics.Bitmap
import dev.veeso.biangbiangui.config.OcrRecognizer

/** A recognised text run in image-pixel coordinates (top-left origin). */
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
 */
fun interface OcrService {
    suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox>
}
