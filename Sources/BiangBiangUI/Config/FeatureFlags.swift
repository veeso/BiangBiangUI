public struct FeatureFlags: Sendable {
    public var history: Bool
    public var ratePrompt: Bool
    public var tts: Bool
    public init(history: Bool = true, ratePrompt: Bool = true, tts: Bool = true) {
        self.history = history; self.ratePrompt = ratePrompt; self.tts = tts
    }
}
