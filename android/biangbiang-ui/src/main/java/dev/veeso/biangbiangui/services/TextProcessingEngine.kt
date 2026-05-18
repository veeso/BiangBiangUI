package dev.veeso.biangbiangui.services

import dev.veeso.biangbiangui.protocols.Transliterator

/**
 * Detects script spans via a regex built from `scriptRanges`, transliterates
 * each span using an injected [Transliterator], and applies the reference
 * spacing and cleanup rules.
 *
 * Ported from the reference BiangBiang Hanzi Android `TextProcessor`. The
 * generalisations mirror iOS Phase 2 `TextProcessingEngine`:
 * - The hard-coded CJK regex is replaced by a character class built from the
 *   supplied [scriptRanges] (`TextProcessor` -> `TextProcessingEngine`).
 * - The `pinyin4j`/`JyutpingDictionary` mode switch is replaced by a single
 *   injected [Transliterator] (one span in, Latin out).
 * - The post-pass cleanup (strip whitespace before punctuation, collapse
 *   double spaces) matches the canonical iOS surface.
 */
class TextProcessingEngine(
    scriptRanges: List<UIntRange>,
    private val transliterator: Transliterator,
) {
    // Build a character-class pattern: [\uXXXX-\uYYYY...]+
    // All CJK ranges in use are BMP, so 4-hex \u escapes are unambiguous;
    // this matches the iOS NSRegularExpression class-body construction.
    private val pattern: Regex = run {
        val classBody = scriptRanges.joinToString("") { range ->
            "\\u%04X-\\u%04X".format(range.first.toInt(), range.last.toInt())
        }
        Regex("[$classBody]+")
    }

    /**
     * Transliterates all script spans in [text], preserving non-script
     * characters and applying the reference leading/trailing spacing rules and
     * post-pass cleanup.
     *
     * @return the processed string, or `null` if [text] contained no script
     *   spans.
     */
    fun process(text: String): String? {
        if (!containsScript(text)) return null

        // Iterate matches in reverse so replacing a span does not invalidate
        // the offsets of earlier (lower-index) spans.
        val matches = pattern.findAll(text).toList().asReversed()
        val result = StringBuilder(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            val span = result.substring(start, end)
            val transliterated = transliterator.transliterate(span)

            // Leading space: insert unless the span is at string start or the
            // immediately preceding character is a space or sentence punctuation.
            val needsLeadingSpace = when {
                start == 0 -> false
                result[start - 1] == ' ' -> false
                ".,!?;:".contains(result[start - 1]) -> false
                else -> true
            }

            // Trailing space: insert only when the immediately following
            // character is an ASCII letter or digit.
            val needsTrailingSpace = when {
                end >= result.length -> false
                else -> {
                    val next = result[end]
                    next.code < 128 && (next.isLetter() || next.isDigit())
                }
            }

            val replacement = buildString {
                if (needsLeadingSpace) append(' ')
                append(transliterated)
                if (needsTrailingSpace) append(' ')
            }

            result.replace(start, end, replacement)
        }

        // Post-pass 1: remove whitespace immediately before punctuation.
        // Post-pass 2: collapse runs of 2+ whitespace into a single space.
        return result
            .toString()
            .replace(Regex("\\s+([.,!?;:])"), "$1")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    /** Returns `true` iff [text] contains at least one matchable script span. */
    fun containsScript(text: String): Boolean = pattern.containsMatchIn(text)
}
