package dev.veeso.biangbiangui.examples.chinese

import dev.veeso.biangbiangui.protocols.Transliterator
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * Mandarin romanisation. Faithfully extracted from the BiangBiang Hanzi
 * reference Android `TextProcessor.hanziToPinyin(text)`: a single
 * `PinyinHelper.toHanYuPinyinString` over the isolated script span. The
 * library's [dev.veeso.biangbiangui.services.TextProcessingEngine] owns span
 * detection, passthrough and spacing.
 *
 * Mirrors iOS `ChineseExample/PinyinTransliterator` (which used
 * `CFStringTransform` to Latin); on Android `pinyin4j` provides the
 * equivalent CJK -> Latin transform. `pinyin4j` is an example-only
 * dependency and is never referenced by the library.
 */
class PinyinTransliterator : Transliterator {

    private val format: HanyuPinyinOutputFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    override fun transliterate(scriptSpan: String): String =
        PinyinHelper.toHanYuPinyinString(scriptSpan, format, " ", true)
            .trim()
}
