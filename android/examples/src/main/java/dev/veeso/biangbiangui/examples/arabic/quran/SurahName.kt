package dev.veeso.biangbiangui.examples.arabic.quran

/**
 * Ported verbatim from the Harakat-Lens iOS app and the already-complete iOS
 * `ArabicExample/Quran/SurahName`.
 */
data class SurahName(
    val number: Int,
    val english: String,
    val transliteration: String,
    val arabic: String,
) {
    val id: Int get() = number
}
