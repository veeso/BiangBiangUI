//
//  ChineseConfig.swift
//  ChineseExample
//
//  A complete `BiangBiangConfig` proving the library's config seam: one
//  Chinese `LanguageProfile` with three variants (Simplified, Traditional,
//  Cantonese). Branding mirrors the BiangBiang Hanzi app. No plugins.
//

import BiangBiangUI

public enum ChineseConfig {
    @MainActor
    public static let chineseConfig: BiangBiangConfig = .init(
        branding: Branding(
            appName: "BiangBiang Hanzi",
            accentColorHex: "#DE2910",
            logoAssetName: "Logo",
            buttonLogoAssetName: "ButtonLogo",
            githubRepo: "veeso/BiangBiang-Hanzi",
            supportEmail: "info@veeso.dev",
            appStoreId: "6754869174",
            playStoreId: "dev.veeso.biangbianghanzi"
        ),
        languages: [
            LanguageProfile(
                id: "chinese",
                displayName: "Chinese",
                scriptRanges: [0x4E00 ... 0x9FFF],
                ocrRecognizer: .chinese,
                variants: [
                    LanguageVariant(
                        id: "simplified",
                        displayName: "Simplified",
                        transliterator: PinyinTransliterator(),
                        ttsLanguageCode: "zh-CN",
                        translatable: true
                    ),
                    LanguageVariant(
                        id: "traditional",
                        displayName: "Traditional",
                        transliterator: PinyinTransliterator(),
                        ttsLanguageCode: "zh-CN",
                        translatable: true
                    ),
                    LanguageVariant(
                        id: "cantonese",
                        displayName: "Cantonese",
                        transliterator: JyutpingTransliterator(),
                        ttsLanguageCode: "zh-HK",
                        translatable: false
                    ),
                ]
            ),
        ],
        extraSettings: [],
        plugins: [],
        features: FeatureFlags(),
        strings: nil
    )
}
