package dev.veeso.biangbiangui.examples.arabic

/**
 * Ported faithfully from the Harakat-Lens iOS app (`ArabicNormalizer.swift`)
 * and the already-complete iOS `ArabicExample/ArabicNormalizer`. Dual-mode
 * normalizer: aggressive for fuzzy matching, minimal for ICU transliteration.
 * Logic unchanged from the reference.
 */
enum class NormalizationMode {
    /**
     * Aggressive normalization for fuzzy matching: strips harakat, unifies
     * alef variants and alef maqsura, drops tatweel.
     */
    MATCHING,

    /**
     * Minimal cleanup for ICU transliteration: drops tatweel only, preserves
     * harakat and alef variants so ICU can render them as vowels/glyphs.
     */
    TRANSLITERATION,
}

/**
 * @param mode normalization mode.
 * @param unifyTaMarbuta when `true` and `mode == MATCHING`, ta marbuta (ة) is
 *   unified to ha (ه). Ignored in [NormalizationMode.TRANSLITERATION] mode.
 */
class ArabicNormalizer(
    private val mode: NormalizationMode = NormalizationMode.MATCHING,
    private val unifyTaMarbuta: Boolean = false,
) {
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)

        var index = 0
        while (index < input.length) {
            val cp = input.codePointAt(index)
            index += Character.charCount(cp)

            // Tatweel — always dropped.
            if (cp == 0x0640) continue

            when (mode) {
                NormalizationMode.MATCHING -> {
                    // Harakat (U+064B–U+0652) + superscript alef (U+0670)
                    if (cp in 0x064B..0x0652 || cp == 0x0670) continue
                    // Alef variants → ا
                    if (cp == 0x0623 || cp == 0x0625 || cp == 0x0622 || cp == 0x0671) {
                        sb.appendCodePoint(0x0627)
                        continue
                    }
                    // Alef maqsura → ي
                    if (cp == 0x0649) {
                        sb.appendCodePoint(0x064A)
                        continue
                    }
                    // Optional ta marbuta → ه
                    if (unifyTaMarbuta && cp == 0x0629) {
                        sb.appendCodePoint(0x0647)
                        continue
                    }
                }

                NormalizationMode.TRANSLITERATION -> Unit
            }

            sb.appendCodePoint(cp)
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
