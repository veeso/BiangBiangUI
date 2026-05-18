//
//  ArabicConfig.swift
//  ArabicExample
//
//  A complete `BiangBiangConfig` proving the library's plugin slot: one
//  Arabic `LanguageProfile` (single ICU-transliterator variant) plus the
//  `QuranPlugin` and a "Quran mode" extra setting. Branding mirrors the
//  Harakat-Lens app.
//

import BiangBiangUI

public enum ArabicConfig {
    @MainActor
    public static let arabicConfig: BiangBiangConfig = .init(
        branding: Branding(
            appName: "Harakat Lens",
            accentColorHex: "#006C35",
            logoAssetName: "Logo",
            buttonLogoAssetName: "ButtonLogo",
            githubRepo: "veeso/Harakat-Lens",
            supportEmail: "info@veeso.dev",
            appStoreId: "6767614189",
            playStoreId: "dev.veeso.harakatlens"
        ),
        languages: [
            LanguageProfile(
                id: "arabic",
                displayName: "Arabic",
                scriptRanges: [0x0600 ... 0x06FF],
                ocrRecognizer: .arabic,
                variants: [
                    LanguageVariant(
                        id: "arabic",
                        displayName: "Arabic",
                        transliterator: ArabicTransliterator(),
                        ttsLanguageCode: "ar",
                        translatable: true
                    ),
                ]
            ),
        ],
        extraSettings: [
            SettingDescriptor(
                key: "quranMode",
                kind: .toggle,
                label: "Quran mode",
                defaultValue: "false"
            ),
        ],
        plugins: [QuranPlugin()],
        features: FeatureFlags(),
        strings: nil
    )
}
