package dev.veeso.biangbiangui.services.camera

/**
 * Pure, Android-runtime-free coordinate model for live OCR.
 *
 * This is the single library-owned rotation + YUV-normalisation path, so
 * Harakat Android's live-OCR coordinate drift has one authoritative place to
 * be reasoned about rather than being patched per call site. It is
 * deliberately a plain object with pure functions so the coordinate math is
 * unit-testable without a camera/emulator.
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
     * The single library-owned `ImageProxy` -> upright
     * [android.graphics.Bitmap] helper. Rasterises the YUV_420_888 camera
     * frame to a JPEG, decodes it, then applies [rotationDegrees] so the
     * bitmap is in the same upright space the boxes are later validated
     * against. Keeping ALL rotation + YUV math in this one object is the
     * architectural invariant.
     *
     * This drains every plane of the [proxy] eagerly (synchronously, before
     * returning), so the caller MAY — and should — `close()` the
     * [androidx.camera.core.ImageProxy] immediately after this returns; no
     * plane buffer is retained past this call.
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
            85,
            out,
        )
        val bytes = out.toByteArray()
        val raw = android.graphics.BitmapFactory
            .decodeByteArray(bytes, 0, bytes.size)
        if (rotationDegrees == 0) return raw
        val m = android.graphics.Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = android.graphics.Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height, m, true,
        )
        // The pre-rotation intermediate is never returned in this branch; free
        // it now so live OCR doesn't churn one leaked bitmap per frame.
        raw.recycle()
        return rotated
    }

    /**
     * Stride-aware YUV_420_888 -> NV21. Real CameraX frames have
     * `rowStride > width` row padding and frequently semi-planar U/V with
     * `pixelStride == 2`; a bulk `buffer.get(remaining())` copy corrupts every
     * frame on most Samsung/Qualcomm devices. Uses absolute-index `get(index)`
     * so buffer positions are never mutated and there is no race with CameraX
     * recycling the proxy.
     */
    private fun yuv420ToNv21(proxy: androidx.camera.core.ImageProxy): ByteArray {
        val w = proxy.width
        val h = proxy.height
        val nv21 = ByteArray(w * h * 3 / 2)

        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]

        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        var pos = 0
        if (yPixStride == 1 && yRowStride == w) {
            yBuf.get(nv21, 0, w * h)
            pos = w * h
        } else {
            for (r in 0 until h) {
                var col = r * yRowStride
                for (c in 0 until w) {
                    nv21[pos++] = yBuf.get(col)
                    col += yPixStride
                }
            }
        }

        // NV21 chroma is interleaved V,U,V,U... after the full Y plane.
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uvRowStride = uPlane.rowStride // U and V share stride/pixelStride
        val uvPixStride = uPlane.pixelStride
        val cw = w / 2
        val ch = h / 2
        for (r in 0 until ch) {
            var uCol = r * uvRowStride
            var vCol = r * uvRowStride
            for (c in 0 until cw) {
                nv21[pos++] = vBuf.get(vCol)
                nv21[pos++] = uBuf.get(uCol)
                uCol += uvPixStride
                vCol += uvPixStride
            }
        }
        return nv21
    }
}
