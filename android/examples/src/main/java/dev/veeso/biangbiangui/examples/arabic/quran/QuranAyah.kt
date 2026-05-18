package dev.veeso.biangbiangui.examples.arabic.quran

/**
 * Ported verbatim from the Harakat-Lens iOS app and the already-complete iOS
 * `ArabicExample/Quran/QuranAyah`.
 */
data class QuranAyah(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val normalized: String,
    val transliteration: String,
    val translationEn: String,
) {
    val id: String get() = "$surah:$ayah"
}
