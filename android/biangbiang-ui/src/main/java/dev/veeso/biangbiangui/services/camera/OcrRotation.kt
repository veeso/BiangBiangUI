package dev.veeso.biangbiangui.services.camera

/**
 * Pure, Android-runtime-free coordinate model for live OCR.
 *
 * This is the single correct rotation implementation the plan requires, so
 * Harakat Android's live-OCR coordinate drift is fixed by construction rather
 * than patched later. It is deliberately a plain object with pure functions so
 * it is unit-testable without a camera/emulator.
 *
 * ## The coordinate model
 *
 * `InputImage.fromMediaImage(mediaImage, rotationDegrees)` makes ML Kit return
 * every detected bounding box in the **upright / display** coordinate space
 * (the rotation has *already* been applied to the boxes). However the camera
 * buffer's own `width`/`height` (`imageProxy.width` / `imageProxy.height`) are
 * the **unrotated** sensor-buffer dimensions.
 *
 * Therefore, when `rotationDegrees` is 90 or 270, the upright space the boxes
 * live in is the *swapped* pair `(bufferHeight, bufferWidth)`. For 0 / 180 it
 * is the buffer dimensions unswapped. The overlay must scale boxes against the
 * SAME upright basis the boxes were produced in — using the raw buffer dims for
 * 90/270 is exactly the drift bug.
 */
object OcrRotation {

    /** Upright image dimensions a detected box is expressed in. */
    data class UprightSize(val width: Int, val height: Int)

    /** A detected box already in upright/display coordinates. */
    data class Box(val left: Int, val top: Int, val width: Int, val height: Int)

    /** True for the rotations that swap the buffer's width/height. */
    fun isQuarterTurn(rotationDegrees: Int): Boolean =
        rotationDegrees == 90 || rotationDegrees == 270

    /**
     * Returns the upright (width, height) the ML Kit boxes are expressed in.
     *
     * @param bufferWidth  the unrotated camera buffer width
     *   (`imageProxy.width`).
     * @param bufferHeight the unrotated camera buffer height
     *   (`imageProxy.height`).
     * @param rotationDegrees `imageProxy.imageInfo.rotationDegrees` (0/90/180/270).
     */
    fun uprightSize(
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
    ): UprightSize = if (isQuarterTurn(rotationDegrees)) {
        UprightSize(width = bufferHeight, height = bufferWidth)
    } else {
        UprightSize(width = bufferWidth, height = bufferHeight)
    }

    /**
     * Scales a box (already in the upright basis returned by [uprightSize])
     * onto an overlay/view of [viewWidth] x [viewHeight], using the same
     * upright basis so offsets cannot drift.
     */
    fun mapBoxToView(
        box: Box,
        upright: UprightSize,
        viewWidth: Float,
        viewHeight: Float,
    ): FloatArray {
        val sx = viewWidth / upright.width.toFloat()
        val sy = viewHeight / upright.height.toFloat()
        return floatArrayOf(
            box.left * sx,
            box.top * sy,
            box.width * sx,
            box.height * sy,
        )
    }

    /**
     * Reference sanity filter: a real text element never spans half the upright
     * frame height (or nearly its full width). These garbage boxes are what
     * render as random HUGE text. Ported verbatim from the reference
     * `LiveOcrAnalyzer`.
     */
    fun isSane(box: Box, upright: UprightSize): Boolean =
        box.height < upright.height * 0.5f && box.width < upright.width * 0.9f
}
