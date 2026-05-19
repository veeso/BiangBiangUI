package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.services.TextProcessingEngine
import dev.veeso.biangbiangui.services.camera.LiveOcrAnalyzer
import dev.veeso.biangbiangui.services.camera.OcrBox
import dev.veeso.biangbiangui.services.camera.OcrRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Asserts the single correct live-OCR rotation/coordinate math (the fix, by
 * construction, for Harakat Android's coordinate-drift bug).
 *
 * ML Kit returns boxes in the upright/display space, while the camera buffer
 * dims are unrotated. So for 90/270 the upright dimensions are the *swapped*
 * `(bufferHeight, bufferWidth)`, and for 0/180 they are unswapped — and the
 * overlay must scale boxes against that SAME upright basis.
 */
class LiveOcrAnalyzerTest {

    // A 1080x1920 portrait sensor buffer (typical phone, rotationDegrees 90).
    private val bufferWidth = 1080
    private val bufferHeight = 1920

    @Test
    fun uprightDimensionsSwapFor90And270() {
        val u90 = OcrRotation.uprightSize(bufferWidth, bufferHeight, 90)
        assertEquals(bufferHeight, u90.width)
        assertEquals(bufferWidth, u90.height)

        val u270 = OcrRotation.uprightSize(bufferWidth, bufferHeight, 270)
        assertEquals(bufferHeight, u270.width)
        assertEquals(bufferWidth, u270.height)

        assertTrue(OcrRotation.isQuarterTurn(90))
        assertTrue(OcrRotation.isQuarterTurn(270))
    }

    @Test
    fun uprightDimensionsUnswappedFor0And180() {
        val u0 = OcrRotation.uprightSize(bufferWidth, bufferHeight, 0)
        assertEquals(bufferWidth, u0.width)
        assertEquals(bufferHeight, u0.height)

        val u180 = OcrRotation.uprightSize(bufferWidth, bufferHeight, 180)
        assertEquals(bufferWidth, u180.width)
        assertEquals(bufferHeight, u180.height)

        assertFalse(OcrRotation.isQuarterTurn(0))
        assertFalse(OcrRotation.isQuarterTurn(180))
    }

    @Test
    fun sampleBoxMapsToExpectedUprightViewCoordinates() {
        // Box detected by ML Kit in the 90°-rotated upright space
        // (width=1920, height=1080). Overlay view is 960 x 540 — exactly
        // half scale on both axes, so the box halves.
        val upright = OcrRotation.uprightSize(bufferWidth, bufferHeight, 90)
        val box = OcrRotation.Box(left = 200, top = 100, width = 400, height = 80)

        val mapped = OcrRotation.mapBoxToView(
            box = box,
            upright = upright,
            viewWidth = 960f,
            viewHeight = 540f,
        )
        // sx = 960/1920 = 0.5, sy = 540/1080 = 0.5
        assertEquals(100f, mapped[0], 0.0001f)
        assertEquals(50f, mapped[1], 0.0001f)
        assertEquals(200f, mapped[2], 0.0001f)
        assertEquals(40f, mapped[3], 0.0001f)
    }

    /**
     * Regression: the live-OCR result MUST be delivered on the injected
     * result dispatcher (the UI marshals it to the main thread), not on the
     * background recognition scope. Delivering off-main-thread let
     * `onResult` mutate the Compose `SnapshotStateList` while the draw phase
     * iterated it in `OcrOverlay`, throwing `ConcurrentModificationException`.
     */
    @Test
    fun resultIsDeliveredOnTheInjectedDispatcher() = runBlocking {
        val deliveryExecutor =
            Executors.newSingleThreadExecutor { r -> Thread(r, "ocr-delivery") }
        try {
            val deliveryDispatcher = deliveryExecutor.asCoroutineDispatcher()
            var deliveredOnThread: String? = null
            val analyzer = LiveOcrAnalyzer(
                service = { _, _ -> emptyList() },
                recognizer = OcrRecognizer.CHINESE,
                engine = TextProcessingEngine(
                    listOf(0x4E00u..0x9FFFu),
                    { it },
                ),
                resultDispatcher = deliveryDispatcher,
                onResult = { _, _, _ ->
                    deliveredOnThread = Thread.currentThread().name
                },
            )
            // Invoke delivery from a background thread, as analyze() does.
            withContext(Dispatchers.Default) {
                analyzer.deliverResult(
                    listOf(OcrBox("你好", "ni hao", 0, 0, 10, 10)),
                    100,
                    200,
                )
            }
            assertTrue(
                "delivered on $deliveredOnThread, expected the ocr-delivery thread",
                deliveredOnThread?.startsWith("ocr-delivery") == true,
            )
        } finally {
            deliveryExecutor.shutdown()
        }
    }

    @Test
    fun saneFilterUsesUprightBasis() {
        val upright = OcrRotation.uprightSize(bufferWidth, bufferHeight, 90)
        // upright is 1920 x 1080; a 200x80 box is sane.
        assertTrue(
            OcrRotation.isSane(OcrRotation.Box(0, 0, 200, 80), upright),
        )
        // A box taller than half the upright height (1080) is garbage.
        assertFalse(
            OcrRotation.isSane(OcrRotation.Box(0, 0, 200, 600), upright),
        )
        // A box nearly the full upright width (1920) is garbage.
        assertFalse(
            OcrRotation.isSane(OcrRotation.Box(0, 0, 1900, 80), upright),
        )
    }
}
