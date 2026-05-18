package dev.veeso.biangbiangui.examples.arabic

/**
 * Ported verbatim from the Harakat-Lens iOS app (`Vocalizer/Vocalizer.swift`)
 * and the already-complete iOS `ArabicExample/Vocalizer`.
 */
class Vocalizer(private val dictionary: VocalizationDictionary) {

    /**
     * If [word] is bare (no harakat), return its dictionary vocalization or
     * the original word on miss. If [word] already contains any harakat
     * character, return it unchanged.
     */
    fun vocalize(word: String): String {
        if (containsHarakat(word)) return word
        return dictionary.lookup(word) ?: word
    }

    private fun containsHarakat(word: String): Boolean {
        var index = 0
        while (index < word.length) {
            val cp = word.codePointAt(index)
            index += Character.charCount(cp)
            if (cp in 0x064B..0x0652 || cp == 0x0670) return true
        }
        return false
    }
}
