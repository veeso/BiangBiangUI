package dev.veeso.biangbiangui.examples.chinese

import dev.veeso.biangbiangui.protocols.Transliterator

/**
 * Cantonese romanisation. Faithfully extracted from the BiangBiang Hanzi
 * reference Android `TextProcessor.hanziToJyutping(text)`: per-character
 * dictionary lookup, unknown characters preserved verbatim, tokens joined by
 * single spaces. The library's
 * [dev.veeso.biangbiangui.services.TextProcessingEngine] owns span detection
 * and spacing.
 *
 * Mirrors iOS `ChineseExample/JyutpingTransliterator`.
 */
class JyutpingTransliterator(private val dictionary: JyutpingDictionary) : Transliterator {

    override fun transliterate(scriptSpan: String): String {
        val out = StringBuilder()
        for (ch in scriptSpan) {
            val key = ch.toString()
            val reading = dictionary.reading(key) ?: key
            if (out.isNotEmpty()) out.append(' ')
            out.append(reading)
        }
        return out.toString()
    }
}
