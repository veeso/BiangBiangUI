package dev.veeso.biangbiangui.services.camera

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.veeso.biangbiangui.config.LanguageProfile
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.services.TextProcessingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
 * Resolves the OCR backend for a profile: the profile's [OcrService] override
 * if present, otherwise the built-in ML Kit [DefaultOcrService]. Resolved once
 * per camera session; rotation/spacing/transliteration stay library-owned.
 */
fun resolveOcrService(profile: LanguageProfile): OcrService =
    profile.ocrService ?: DefaultOcrService()

/**
 * One-shot still-image OCR. The injected [service] performs raw recognition
 * (image-pixel space, no transliteration); the pipeline adds `engine.process`
 * and the `OcrRotation.isSane` filter so behaviour matches the pre-seam path.
 */
class StillImageOcr(
    private val service: OcrService,
    private val recognizer: OcrRecognizer,
    private val engine: TextProcessingEngine,
) {
    suspend fun recognize(bitmap: Bitmap): List<OcrBox> {
        val upright = OcrRotation.UprightSize(bitmap.width, bitmap.height)
        return service.recognize(bitmap, recognizer)
            .mapNotNull { tb ->
                val tl = engine.process(tb.text) ?: return@mapNotNull null
                OcrBox(tb.text, tl, tb.left, tb.top, tb.width, tb.height)
            }
            .filter {
                OcrRotation.isSane(
                    OcrRotation.Box(it.left, it.top, it.width, it.height),
                    upright,
                )
            }
    }
}

/**
 * Throttled live-camera OCR analyzer.
 *
 * The frame is normalised to an UPRIGHT [Bitmap] by the single library-owned
 * [OcrRotation] path BEFORE the injected [service] sees it, so the service
 * contract (image-pixel space) holds and the rotation authority stays
 * library-owned. ML Kit returns boxes already upright, so the upright basis is
 * the buffer dims *swapped* for 90/270 (see [OcrRotation]). [onResult]
 * receives the sane boxes plus the upright (width, height) to scale against.
 *
 * Lifecycle: holds a private [CoroutineScope]. Callers MUST [close] the
 * analyzer when it is replaced (e.g. profile change) or leaves use, which
 * cancels any in-flight recognition. At most one recognition runs at a time
 * (see [busy]): frames arriving while one is in flight are dropped, which both
 * bounds work and guarantees [onResult] is delivered in frame order.
 */
class LiveOcrAnalyzer(
    private val service: OcrService,
    private val recognizer: OcrRecognizer,
    private val engine: TextProcessingEngine,
    private val throttleMs: Long = 1000L,
    private val onResult: (List<OcrBox>, Int, Int) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {

    private var lastProcessedTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default)
    private val busy = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttleMs) {
            imageProxy.close()
            return
        }
        // Drop this frame if a recognition is still in flight: overlapping
        // recognitions could deliver onResult out of order (stale boxes
        // winning), and this also bounds work to one coroutine at a time.
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastProcessedTime = now

        val mediaImage = imageProxy.image ?: run {
            busy.set(false)
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        // Library-owned rotation basis: feed the UNROTATED buffer dims (same
        // as the pre-seam code) so the isSane basis and the (w,h) sent to the
        // overlay are unchanged. ML Kit boxes are already upright.
        val upright = OcrRotation.uprightSize(
            bufferWidth = mediaImage.width,
            bufferHeight = mediaImage.height,
            rotationDegrees = rotationDegrees,
        )
        // toUprightBitmap drains all planes eagerly, so the proxy is safe to
        // close immediately; the in-flight (w,h) is this frame's upright.
        val bitmap = OcrRotation.toUprightBitmap(imageProxy, rotationDegrees)
        imageProxy.close()

        scope.launch {
            try {
                val boxes = service.recognize(bitmap, recognizer)
                    .mapNotNull { tb ->
                        val tl = engine.process(tb.text) ?: return@mapNotNull null
                        OcrBox(tb.text, tl, tb.left, tb.top, tb.width, tb.height)
                    }
                    .filter {
                        OcrRotation.isSane(
                            OcrRotation.Box(it.left, it.top, it.width, it.height),
                            upright,
                        )
                    }
                onResult(boxes, upright.width, upright.height)
            } finally {
                busy.set(false)
            }
        }
    }

    /** Cancels any in-flight recognition; call when this analyzer is replaced
     *  or leaves use so its [scope] does not outlive it. */
    override fun close() {
        scope.cancel()
    }
}
