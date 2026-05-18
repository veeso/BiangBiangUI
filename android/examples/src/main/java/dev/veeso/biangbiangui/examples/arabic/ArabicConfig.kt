package dev.veeso.biangbiangui.examples.arabic

import android.content.Context
import dev.veeso.biangbiangui.config.BiangBiangConfig
import dev.veeso.biangbiangui.config.Branding
import dev.veeso.biangbiangui.config.FeatureFlags
import dev.veeso.biangbiangui.config.LanguageProfile
import dev.veeso.biangbiangui.config.LanguageVariant
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.config.SettingDescriptor
import dev.veeso.biangbiangui.examples.arabic.quran.QuranPlugin

/**
 * A complete [BiangBiangConfig] proving the library's plugin slot: one Arabic
 * [LanguageProfile] (single ICU-transliterator variant) plus the
 * [QuranPlugin] and a "Quran mode" extra setting. Branding mirrors the
 * Harakat-Lens app.
 *
 * Mirrors iOS `ArabicConfig.arabicConfig` exactly (same branding,
 * scriptRanges, ocrRecognizer, single `ar`/translatable variant, `quranMode`
 * toggle descriptor, single [QuranPlugin]). The iOS config is a static `let`;
 * on Android it is a factory function because the transliterator / plugin
 * need a [Context] for asset loading (`Bundle.module` -> `Context.assets`).
 */
fun arabicConfig(context: Context): BiangBiangConfig {
    val appContext = context.applicationContext
    val transliterator = ArabicTransliterator(
        Vocalizer(VocalizationDictionary.get(appContext)),
    )
    return BiangBiangConfig(
        branding = Branding(
            appName = "Harakat Lens",
            accentColorHex = "#006C35",
            logoAssetName = "Logo",
            buttonLogoAssetName = "ButtonLogo",
            githubRepo = "veeso/Harakat-Lens",
            supportEmail = "info@veeso.dev",
            appStoreId = "6767614189",
            playStoreId = "dev.veeso.harakatlens",
        ),
        languages = listOf(
            LanguageProfile(
                id = "arabic",
                displayName = "Arabic",
                scriptRanges = listOf(0x0600u..0x06FFu),
                ocrRecognizer = OcrRecognizer.ARABIC,
                variants = listOf(
                    LanguageVariant(
                        id = "arabic",
                        displayName = "Arabic",
                        transliterator = transliterator,
                        ttsLanguageCode = "ar",
                        translatable = true,
                    ),
                ),
                ocrService = TesseractOcrService(appContext),
            ),
        ),
        extraSettings = listOf(
            SettingDescriptor(
                key = "quranMode",
                kind = SettingDescriptor.Kind.Toggle,
                label = "Quran mode",
                defaultValue = "false",
            ),
        ),
        plugins = listOf(QuranPlugin(appContext)),
        features = FeatureFlags(),
        strings = null,
    )
}
