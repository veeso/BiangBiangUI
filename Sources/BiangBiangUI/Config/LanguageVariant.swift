public struct LanguageVariant: Sendable {
    public let id: String
    public let displayName: String
    public let transliterator: any Transliterator
    /// BCP-47 code for TTS; `nil` hides the Listen control for this variant.
    public let ttsLanguageCode: String?
    /// `false` hides the translation UI as data — no conditional code path.
    public let translatable: Bool

    public init(id: String, displayName: String, transliterator: any Transliterator,
                ttsLanguageCode: String?, translatable: Bool)
    {
        self.id = id; self.displayName = displayName
        self.transliterator = transliterator
        self.ttsLanguageCode = ttsLanguageCode
        self.translatable = translatable
    }
}
