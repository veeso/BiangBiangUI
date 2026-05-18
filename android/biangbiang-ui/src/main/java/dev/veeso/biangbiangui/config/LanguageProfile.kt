package dev.veeso.biangbiangui.config

/** Which OCR engine recognises this script. Mirrors iOS `OCRRecognizer`. */
enum class OcrRecognizer {
    CHINESE,
    LATIN,
    ARABIC,
    JAPANESE,
    KOREAN,
}

/**
 * Describes a recognisable script and its transliteration variants.
 * Mirrors iOS `LanguageProfile`.
 */
data class LanguageProfile(
    val id: String,
    val displayName: String,
    /** Unicode scalar ranges that delimit a script span (e.g. 0x4E00..0x9FFF). */
    val scriptRanges: List<UIntRange>,
    val ocrRecognizer: OcrRecognizer,
    /** May be empty; the variant picker renders only when non-empty. */
    val variants: List<LanguageVariant>,
)
