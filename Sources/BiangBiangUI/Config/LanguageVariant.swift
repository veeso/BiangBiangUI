public struct LanguageVariant: Sendable {
    public let id: String
    public let displayName: String
    public let transliterator: any Transliterator
    /// BCP-47 code for TTS; `nil` hides the Listen control for this variant.
    public let ttsLanguageCode: String?
    /// BCP-47 source code for translation; `nil` hides translation controls.
    public let translationLanguageCode: String?

    public init(id: String, displayName: String, transliterator: any Transliterator,
                ttsLanguageCode: String?, translationLanguageCode: String?)
    {
        self.id = id; self.displayName = displayName
        self.transliterator = transliterator
        self.ttsLanguageCode = ttsLanguageCode
        self.translationLanguageCode = translationLanguageCode
    }
}
