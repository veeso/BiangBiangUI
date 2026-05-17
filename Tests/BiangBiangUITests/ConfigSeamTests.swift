import Testing
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
