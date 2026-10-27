import ArabicExample
@testable import BiangBiangUI
import ChineseExample
import SwiftUI
import Testing

struct UpcaseTransliterator: Transliterator {
    func transliterate(_ scriptSpan: String) -> String {
        scriptSpan.uppercased()
    }
}

@Test func transliteratorConforms() {
    #expect(UpcaseTransliterator().transliterate("ab") == "AB")
}

@Test func processedTextHoldsSourceAndVariant() {
    let p = ProcessedText(original: "你好", transliteration: "ni hao",
                          variantId: "mandarin", source: .text)
    #expect(p.source == .text)
    #expect(p.variantId == "mandarin")
}

@Test func languageVariantsSeparateTTSAndTranslationCodes() {
    let mandarin = LanguageVariant(id: "mandarin", displayName: "Simplified",
                                   transliterator: UpcaseTransliterator(), ttsLanguageCode: "zh-CN",
                                   translationLanguageCode: "zh-CN")
    let canto = LanguageVariant(id: "cantonese", displayName: "Cantonese",
                                transliterator: UpcaseTransliterator(), ttsLanguageCode: "zh-HK",
                                translationLanguageCode: "zh-CN")
    let unavailable = LanguageVariant(id: "unavailable", displayName: "Unavailable",
                                      transliterator: UpcaseTransliterator(), ttsLanguageCode: "ja-JP",
                                      translationLanguageCode: nil)
    let profile = LanguageProfile(id: "chinese", displayName: "Chinese",
                                  scriptRanges: [0x4E00 ... 0x9FFF], ocrRecognizer: .chinese,
                                  variants: [mandarin, mandarin, canto])
    #expect(profile.variants.count == 3)
    #expect(canto.ttsLanguageCode == "zh-HK")
    #expect(canto.translationLanguageCode == "zh-CN")
    #expect(unavailable.translationLanguageCode == nil)
    #expect(profile.scriptRanges.first?.contains(0x4F60) == true)
}

@MainActor
@Test func chineseConfigBuildsAndTransliterates() throws {
    let cfg = ChineseConfig.chineseConfig
    let v = try #require(cfg.languages[0].variants.first { $0.id == "simplified" })
    #expect(v.transliterator.transliterate("你好").contains("n")) // pinyin produced
    let canto = try #require(cfg.languages[0].variants.first { $0.id == "cantonese" })
    #expect(canto.ttsLanguageCode == "zh-HK")
    #expect(canto.translationLanguageCode == "zh-CN")
}

@MainActor
struct NoopPlugin: FeaturePlugin {
    var tabs: [PluginTab] {
        []
    }
}

@MainActor
@Test func pluginDefaultsAreEmpty() {
    let p = NoopPlugin()
    #expect(p.tabs.isEmpty)
    #expect(p.audioProvider == nil)
    p.onProcessedText(ProcessedText(original: "x", transliteration: "x",
                                    variantId: "v", source: .text))
    #expect(p.inlineResultView(for: ProcessedText(original: "x", transliteration: "x",
                                                  variantId: "v", source: .text)) == nil)
}

@MainActor
@Test func configExposesBrandingAndDefaultsStringsToEnglish() {
    let cfg = BiangBiangConfig(
        branding: Branding(appName: "Demo", accentColorHex: "#DE2910",
                           logoAssetName: "Logo", buttonLogoAssetName: "Button",
                           githubRepo: "veeso/Demo", supportEmail: "info@veeso.dev",
                           appStoreId: "1", playStoreId: "dev.veeso.demo"),
        languages: [], extraSettings: [], plugins: [],
        features: FeatureFlags(), strings: nil
    )
    #expect(cfg.branding.appName == "Demo")
    #expect(cfg.features.history && cfg.features.ratePrompt && cfg.features.tts)
    #expect(cfg.minimumOcrScaleFactor == 0.6)
    #expect(cfg.strings.clearAll == "Clear All")
    #expect(cfg.strings.cameraLogTitle == "Found text")
    #expect(cfg.strings.cameraLogClear == "Clear")
    #expect(cfg.strings.cameraLogEmpty == "Point the camera at text")
}

@MainActor
@Test func stringOverridesMergeOverDefaults() {
    let cfg = BiangBiangConfig(
        branding: Branding(appName: "D", accentColorHex: "#000",
                           logoAssetName: "", buttonLogoAssetName: "", githubRepo: "",
                           supportEmail: "", appStoreId: "", playStoreId: ""),
        languages: [], extraSettings: [], plugins: [],
        features: FeatureFlags(), strings: [
            "clearAll": "Wipe",
            "cameraLogTitle": "Detected",
            "cameraLogClear": "Reset",
            "cameraLogEmpty": "Aim here",
        ]
    )
    #expect(cfg.strings.clearAll == "Wipe")
    #expect(cfg.strings.cameraLogTitle == "Detected")
    #expect(cfg.strings.cameraLogClear == "Reset")
    #expect(cfg.strings.cameraLogEmpty == "Aim here")
}

@MainActor @Test func arabicConfigHasQuranPluginAndDescriptor() {
    let cfg = ArabicConfig.arabicConfig
    #expect(cfg.plugins.count == 1)
    #expect(cfg.extraSettings.contains { $0.key == "quranMode" })
    #expect(cfg.languages[0].variants[0].ttsLanguageCode == "ar")
    #expect(cfg.languages[0].variants[0].translationLanguageCode == "ar")
}

@Test func arabicTransliteratorRomanises() {
    #expect(ArabicTransliterator().transliterate("السلام").isEmpty == false)
}
