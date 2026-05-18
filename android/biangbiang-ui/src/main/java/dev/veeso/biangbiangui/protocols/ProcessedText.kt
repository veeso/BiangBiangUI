package dev.veeso.biangbiangui.protocols

/**
 * The result of processing a Text-input string or a Camera OCR box.
 * Mirrors iOS `ProcessedText`.
 */
data class ProcessedText(
    val original: String,
    val transliteration: String,
    val variantId: String,
    val source: Source,
) {
    /** Mirrors iOS `ProcessedText.Source`. */
    enum class Source {
        TEXT,
        CAMERA,
    }
}
