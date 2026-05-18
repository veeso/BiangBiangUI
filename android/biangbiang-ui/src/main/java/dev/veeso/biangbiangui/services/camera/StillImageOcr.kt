package dev.veeso.biangbiangui.services.camera

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.services.TextProcessingEngine
import kotlinx.coroutines.tasks.await

/** A recognised text element already in upright/display coordinates. */
data class OcrBox(
    val text: String,
    val transliteration: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Builds the ML Kit [TextRecognizer] that matches a [OcrRecognizer].
 *
 * Ported from the reference BiangBiang Hanzi Android `OcrService` (which only
 * ever instantiated the Chinese recognizer). The generalisation mirrors iOS
 * Phase 2 `OCRCameraModel.recognitionLanguages(for:)`: the recognizer is
 * selected from `LanguageProfile.ocrRecognizer`.
 *
 * - `CHINESE` -> ChineseTextRecognizer
 * - `LATIN` / `ARABIC` -> default Latin `TextRecognition` (ML Kit ships no
 *   on-device Arabic script model yet; this is the documented fallback until
 *   an Arabic model is added — see iOS `preferredArabicLanguages()`)
 * - `JAPANESE` -> JapaneseTextRecognizer
 * - `KOREAN` -> KoreanTextRecognizer
 */
internal fun recognizerFor(recognizer: OcrRecognizer): TextRecognizer = when (recognizer) {
    OcrRecognizer.CHINESE ->
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    OcrRecognizer.JAPANESE ->
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    OcrRecognizer.KOREAN ->
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    OcrRecognizer.LATIN, OcrRecognizer.ARABIC ->
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}

/**
 * One-shot still-image OCR. Ported from the reference `OcrService`; the
 * hard-coded Chinese recognizer/`TextProcessor` are replaced by the injected
 * [recognizer]/[engine] selected from the active `LanguageProfile`.
 */
class StillImageOcr(
    recognizerKind: OcrRecognizer,
    private val engine: TextProcessingEngine,
) {
    private val recognizer = recognizerFor(recognizerKind)

    suspend fun recognize(bitmap: Bitmap): List<OcrBox> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        val upright = OcrRotation.UprightSize(bitmap.width, bitmap.height)
        return result.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { element ->
                val transliteration =
                    engine.process(element.text) ?: return@mapNotNull null
                element.boundingBox?.let { box ->
                    OcrBox(
                        text = element.text,
                        transliteration = transliteration,
                        left = box.left,
                        top = box.top,
                        width = box.width(),
                        height = box.height(),
                    )
                }
            }
            .filter { b ->
                OcrRotation.isSane(
                    OcrRotation.Box(b.left, b.top, b.width, b.height),
                    upright,
                )
            }
    }
}

/**
 * Throttled live-camera OCR analyzer.
 *
 * Ported from the reference `LiveOcrAnalyzer`. The hard-coded Chinese
 * recognizer/`TextProcessor` are replaced by the injected
 * [recognizerKind]/[engine]. The rotation/coordinate math is delegated to the
 * single correct pure [OcrRotation] implementation: ML Kit returns boxes in
 * upright/display space, so the upright dimensions are the buffer dims
 * *swapped* for 90/270 and the overlay must use that same basis. [onResult]
 * receives the sane boxes plus the upright (width, height) to scale against.
 */
class LiveOcrAnalyzer(
    recognizerKind: OcrRecognizer,
    private val engine: TextProcessingEngine,
    private val throttleMs: Long = 1000L,
    private val onResult: (List<OcrBox>, Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = recognizerFor(recognizerKind)
    private var lastProcessedTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttleMs) {
            imageProxy.close()
            return
        }
        lastProcessedTime = now

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Single correct rotation basis: ML Kit boxes are already upright, so
        // the upright dimensions swap for 90/270 (see OcrRotation).
        val upright = OcrRotation.uprightSize(
            bufferWidth = image.width,
            bufferHeight = image.height,
            rotationDegrees = rotationDegrees,
        )

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val boxes = result.textBlocks
                    .flatMap { it.lines }
                    .flatMap { it.elements }
                    .mapNotNull { element ->
                        val transliteration =
                            engine.process(element.text) ?: return@mapNotNull null
                        element.boundingBox?.let { box ->
                            OcrBox(
                                text = element.text,
                                transliteration = transliteration,
                                left = box.left,
                                top = box.top,
                                width = box.width(),
                                height = box.height(),
                            )
                        }
                    }
                    .filter { b ->
                        OcrRotation.isSane(
                            OcrRotation.Box(b.left, b.top, b.width, b.height),
                            upright,
                        )
                    }
                onResult(boxes, upright.width, upright.height)
            }
            .addOnFailureListener { /* ignore for now */ }
            .addOnCompleteListener { imageProxy.close() }
    }
}
