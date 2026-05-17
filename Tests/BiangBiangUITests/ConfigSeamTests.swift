import Testing
import SwiftUI
@testable import BiangBiangUI

struct UpcaseTransliterator: Transliterator {
    func transliterate(_ scriptSpan: String) -> String { scriptSpan.uppercased() }
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

@Test func chineseProfileHasThreeVariantsAndCantoneseNotTranslatable() {
    let mandarin = LanguageVariant(id: "mandarin", displayName: "Simplified",
        transliterator: UpcaseTransliterator(), ttsLanguageCode: "zh-CN", translatable: true)
    let canto = LanguageVariant(id: "cantonese", displayName: "Cantonese",
        transliterator: UpcaseTransliterator(), ttsLanguageCode: "zh-HK", translatable: false)
    let profile = LanguageProfile(id: "chinese", displayName: "Chinese",
        scriptRanges: [0x4E00...0x9FFF], ocrRecognizer: .chinese,
        variants: [mandarin, mandarin, canto])
    #expect(profile.variants.count == 3)
    #expect(profile.variants.last?.translatable == false)
    #expect(profile.scriptRanges.first?.contains(0x4F60) == true)
}

@MainActor
struct NoopPlugin: FeaturePlugin {
    var tabs: [PluginTab] { [] }
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
