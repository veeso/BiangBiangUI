package dev.veeso.biangbiangui.services.camera

/**
 * Filter the candidate zoom presets to those supported by the device.
 *
 * Ported verbatim from the reference BiangBiang Hanzi Android `CameraZoom`.
 */
fun availablePresets(
    maxZoom: Float,
    candidates: List<Float> = listOf(1f, 2f, 5f),
): List<Float> = candidates.filter { it <= maxZoom }

/**
 * Clamp a zoom factor to a closed range.
 *
 * Ported verbatim from the reference BiangBiang Hanzi Android `CameraZoom`.
 */
fun clampZoom(value: Float, min: Float, max: Float): Float =
    value.coerceIn(min, max)
