package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.config.BiangBiangConfig
import dev.veeso.biangbiangui.config.Branding
import dev.veeso.biangbiangui.config.FeatureFlags
import dev.veeso.biangbiangui.config.LanguageProfile
import dev.veeso.biangbiangui.config.LanguageVariant
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.config.SettingDescriptor
import dev.veeso.biangbiangui.protocols.FeaturePlugin
import dev.veeso.biangbiangui.protocols.ProcessedText
import dev.veeso.biangbiangui.protocols.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the iOS `ConfigSeamTests` at the config-model/protocol layer that
 * exists in Task 5.1 (no example configs yet — those arrive in Task 5.4).
 */
class ConfigSeamTest {
    private val upcase = Transliterator { it.uppercase() }

    @Test fun transliteratorConforms() {
        assertEquals("AB", upcase.transliterate("ab"))
    }

    @Test fun processedTextHoldsSourceAndVariant() {
        val p = ProcessedText(
            original = "你好",
            transliteration = "ni hao",
            variantId = "mandarin",
            source = ProcessedText.Source.TEXT,
        )
        assertEquals(ProcessedText.Source.TEXT, p.source)
        assertEquals("mandarin", p.variantId)
    }

    @Test fun chineseProfileHasThreeVariantsAndCantoneseNotTranslatable() {
        val mandarin = LanguageVariant("mandarin", "Simplified", upcase, "zh-CN", true)
        val canto = LanguageVariant("cantonese", "Cantonese", upcase, "zh-HK", false)
        val profile = LanguageProfile(
            id = "chinese",
            displayName = "Chinese",
            scriptRanges = listOf(0x4E00u..0x9FFFu),
            ocrRecognizer = OcrRecognizer.CHINESE,
            variants = listOf(mandarin, mandarin, canto),
        )
        assertEquals(3, profile.variants.size)
        assertFalse(profile.variants.last().translatable)
        assertTrue(profile.scriptRanges.first().contains(0x4F60u))
    }

    private class NoopPlugin : FeaturePlugin

    @Test fun pluginDefaultsAreEmpty() {
        val p = NoopPlugin()
        assertTrue(p.tabs.isEmpty())
        assertNull(p.audioProvider)
        // no-op default must not throw
        p.onProcessedText(
            ProcessedText("x", "x", "v", ProcessedText.Source.TEXT),
        )
        assertNull(
            p.inlineResultView(ProcessedText("x", "x", "v", ProcessedText.Source.TEXT)),
        )
    }

    @Test fun configExposesBrandingAndDefaultsStringsToEnglish() {
        val cfg = BiangBiangConfig(
            branding = Branding(
                appName = "Demo",
                accentColorHex = "#DE2910",
                logoAssetName = "Logo",
                buttonLogoAssetName = "Button",
                githubRepo = "veeso/Demo",
                supportEmail = "info@veeso.dev",
                appStoreId = "1",
                playStoreId = "dev.veeso.demo",
            ),
            languages = emptyList(),
            extraSettings = emptyList(),
            plugins = emptyList(),
            features = FeatureFlags(),
            strings = null,
        )
        assertEquals("Demo", cfg.branding.appName)
        assertTrue(cfg.features.history && cfg.features.ratePrompt && cfg.features.tts)
        assertEquals("Clear All", cfg.strings.clearAll)
    }

    @Test fun stringOverridesMergeOverDefaults() {
        val cfg = BiangBiangConfig(
            branding = Branding("D", "#000", "", "", "", "", "", ""),
            languages = emptyList(),
            extraSettings = emptyList(),
            plugins = emptyList(),
            features = FeatureFlags(),
            strings = mapOf("clearAll" to "Wipe"),
        )
        assertEquals("Wipe", cfg.strings.clearAll)
        // unspecified keys keep English defaults
        assertEquals("Copy", cfg.strings.copy)
    }

    @Test fun settingDescriptorKindsAndIdentity() {
        val toggle = SettingDescriptor(
            key = "quranMode",
            kind = SettingDescriptor.Kind.Toggle,
            label = "Quran mode",
            defaultValue = "false",
        )
        val picker = SettingDescriptor(
            key = "theme",
            kind = SettingDescriptor.Kind.Picker(listOf("light", "dark")),
            label = "Theme",
            defaultValue = "light",
            footer = "Pick one",
        )
        assertEquals("quranMode", toggle.id)
        assertTrue(toggle.kind is SettingDescriptor.Kind.Toggle)
        assertEquals(
            listOf("light", "dark"),
            (picker.kind as SettingDescriptor.Kind.Picker).options,
        )
        assertEquals("Pick one", picker.footer)
    }
}
