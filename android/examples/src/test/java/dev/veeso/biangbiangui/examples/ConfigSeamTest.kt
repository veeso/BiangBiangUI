package dev.veeso.biangbiangui.examples

import dev.veeso.biangbiangui.examples.arabic.ArabicTransliterator
import dev.veeso.biangbiangui.examples.arabic.Vocalizer
import dev.veeso.biangbiangui.examples.arabic.VocalizationDictionary
import dev.veeso.biangbiangui.examples.arabic.arabicConfig
import dev.veeso.biangbiangui.examples.chinese.chineseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.content.Context

/**
 * The real example-layer seam assertions deferred from the 5.1 library
 * `ConfigSeamTest`. Mirrors the iOS `ConfigSeamTests` Chinese + Arabic cases
 * (`chineseConfigBuildsAndTransliterates`,
 * `arabicConfigHasQuranPluginAndDescriptor`, `arabicTransliteratorRomanises`).
 *
 * Runs under Robolectric: the example configs load JSON assets via
 * `Context.assets` and `ArabicTransliterator` uses the bundled
 * `android.icu` transform.
 */
@RunWith(RobolectricTestRunner::class)
class ConfigSeamTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun chineseConfigBuildsAndTransliterates() {
        val cfg = chineseConfig(context)
        val simplified = cfg.languages[0].variants.first { it.id == "simplified" }
        // pinyin produced
        assertTrue(simplified.transliterator.transliterate("你好").contains("n"))
        val canto = cfg.languages[0].variants.first { it.id == "cantonese" }
        assertFalse(canto.translatable)
    }

    @Test fun chineseProfileHasThreeVariantsAndCantoneseNotTranslatable() {
        val cfg = chineseConfig(context)
        val profile = cfg.languages[0]
        assertEquals(3, profile.variants.size)
        assertFalse(profile.variants.last().translatable)
        assertTrue(profile.scriptRanges.first().contains(0x4F60u))
    }

    @Test fun arabicConfigHasQuranPluginAndDescriptor() {
        val cfg = arabicConfig(context)
        assertEquals(1, cfg.plugins.size)
        assertTrue(cfg.extraSettings.any { it.key == "quranMode" })
        assertEquals("ar", cfg.languages[0].variants[0].ttsLanguageCode)
    }

    @Test fun arabicTransliteratorRomanises() {
        val transliterator = ArabicTransliterator(
            Vocalizer(VocalizationDictionary.get(context)),
        )
        assertFalse(transliterator.transliterate("السلام").isEmpty())
    }
}
