/// The result of processing a Text-input string or a Camera OCR box.
public struct ProcessedText: Sendable, Equatable {
    public enum Source: Sendable, Equatable { case text, camera }
    public let original: String
    public let transliteration: String
    public let variantId: String
    public let source: Source

    public init(original: String, transliteration: String, variantId: String, source: Source) {
        self.original = original
        self.transliteration = transliteration
        self.variantId = variantId
        self.source = source
    }
}
