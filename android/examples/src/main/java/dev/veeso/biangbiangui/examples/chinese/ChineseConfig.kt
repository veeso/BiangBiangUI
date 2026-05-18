package dev.veeso.biangbiangui.examples.chinese

import android.content.Context
import dev.veeso.biangbiangui.config.BiangBiangConfig
import dev.veeso.biangbiangui.config.Branding
import dev.veeso.biangbiangui.config.FeatureFlags
import dev.veeso.biangbiangui.config.LanguageProfile
import dev.veeso.biangbiangui.config.LanguageVariant
import dev.veeso.biangbiangui.config.OcrRecognizer

/**
 * A complete [BiangBiangConfig] proving the library's config seam: one
 * Chinese [LanguageProfile] with three variants (Simplified, Traditional,
 * Cantonese). Branding mirrors the BiangBiang Hanzi app. No plugins.
 *
 * Mirrors iOS `ChineseConfig.chineseConfig` exactly (same branding,
 * scriptRanges, ocrRecognizer, variant ids / ttsLanguageCode / translatable).
 * The iOS config is a static `let`; on Android it is a factory function
 * because [JyutpingDictionary] needs a [Context] for asset loading
 * (`Bundle.module` -> `Context.assets`).
 */
fun chineseConfig(context: Context): BiangBiangConfig {
    val pinyin = PinyinTransliterator()
    val jyutping = JyutpingTransliterator(JyutpingDictionary.get(context.applicationContext))
    return BiangBiangConfig(
        branding = Branding(
            appName = "BiangBiang Hanzi",
            accentColorHex = "#DE2910",
            logoAssetName = "Logo",
            buttonLogoAssetName = "ButtonLogo",
            githubRepo = "veeso/BiangBiang-Hanzi",
            supportEmail = "info@veeso.dev",
            appStoreId = "6754869174",
            playStoreId = "dev.veeso.biangbianghanzi",
        ),
        languages = listOf(
            LanguageProfile(
                id = "chinese",
                displayName = "Chinese",
                scriptRanges = listOf(0x4E00u..0x9FFFu),
                ocrRecognizer = OcrRecognizer.CHINESE,
                variants = listOf(
                    LanguageVariant(
                        id = "simplified",
                        displayName = "Simplified",
                        transliterator = pinyin,
                        ttsLanguageCode = "zh-CN",
                        translatable = true,
                    ),
                    LanguageVariant(
                        id = "traditional",
                        displayName = "Traditional",
                        transliterator = pinyin,
                        ttsLanguageCode = "zh-CN",
                        translatable = true,
                    ),
                    LanguageVariant(
                        id = "cantonese",
                        displayName = "Cantonese",
                        transliterator = jyutping,
                        ttsLanguageCode = "zh-HK",
                        translatable = false,
                    ),
                ),
            ),
        ),
        extraSettings = emptyList(),
        plugins = emptyList(),
        features = FeatureFlags(),
        strings = null,
    )
}
