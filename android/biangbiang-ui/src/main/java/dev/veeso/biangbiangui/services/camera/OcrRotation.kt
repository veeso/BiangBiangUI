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

    /**
     * The single correct `ImageProxy` -> upright [android.graphics.Bitmap]
     * helper. Rasterises the YUV_420_888 camera frame to a JPEG, decodes it,
     * then applies [rotationDegrees] so the bitmap is in the same upright space
     * the boxes are later validated against. Keeping ALL rotation math in this
     * one object is the architectural invariant (no drift by construction).
     */
    @androidx.camera.core.ExperimentalGetImage
    fun toUprightBitmap(
        proxy: androidx.camera.core.ImageProxy,
        rotationDegrees: Int,
    ): android.graphics.Bitmap {
        val nv21 = yuv420ToNv21(proxy)
        val yuv = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            proxy.width,
            proxy.height,
            null,
        )
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(
            android.graphics.Rect(0, 0, proxy.width, proxy.height),
            100,
            out,
        )
        val bytes = out.toByteArray()
        val raw = android.graphics.BitmapFactory
            .decodeByteArray(bytes, 0, bytes.size)
        if (rotationDegrees == 0) return raw
        val m = android.graphics.Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return android.graphics.Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height, m, true,
        )
    }

    private fun yuv420ToNv21(
        proxy: androidx.camera.core.ImageProxy,
    ): ByteArray {
        val y = proxy.planes[0].buffer
        val u = proxy.planes[1].buffer
        val v = proxy.planes[2].buffer
        val ySize = y.remaining()
        val uSize = u.remaining()
        val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize)
        v.get(nv21, ySize, vSize)
        u.get(nv21, ySize + vSize, uSize)
        return nv21
    }
}
