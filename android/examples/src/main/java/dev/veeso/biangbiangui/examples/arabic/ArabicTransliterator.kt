package dev.veeso.biangbiangui.examples.arabic

import android.icu.text.Transliterator as IcuTransliterator
import dev.veeso.biangbiangui.protocols.Transliterator

/**
 * Arabic romanisation conforming to the library [Transliterator] interface.
 * Pipeline (per the Harakat-Lens approach, mirroring iOS
 * `ArabicExample/ArabicTransliterator`): vocalize each word from the vocab
 * dictionary -> [ArabicNormalizer] (`TRANSLITERATION` mode) cleanup ->
 * romanise.
 *
 * The iOS port used `CFStringTransform(kCFStringTransformToLatin)`. On
 * Android the equivalent transform is `android.icu.text.Transliterator`
 * `"Any-Latin"`, bundled in the platform (`android.icu`, API 24+) so no new
 * dependency is added. ICU lives only in this example, never the library —
 * exactly mirroring iOS.
 *
 * The library's [dev.veeso.biangbiangui.services.TextProcessingEngine] owns
 * span detection and spacing.
 */
class ArabicTransliterator(private val vocalizer: Vocalizer) : Transliterator {

    private val anyLatin: IcuTransliterator = IcuTransliterator.getInstance("Any-Latin")
    private val normalizer = ArabicNormalizer(NormalizationMode.TRANSLITERATION)

    override fun transliterate(scriptSpan: String): String {
        // Vocalize word-by-word: the dictionary is keyed on bare words.
        val vocalized = scriptSpan
            .split(" ")
            .joinToString(" ") { vocalizer.vocalize(it) }

        val normalized = normalizer.normalize(vocalized)

        return anyLatin.transliterate(normalized)
    }
}
