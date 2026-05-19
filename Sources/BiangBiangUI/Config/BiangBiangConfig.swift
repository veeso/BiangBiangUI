import CoreGraphics

/// `plugins` are @MainActor `FeaturePlugin`s (they vend SwiftUI views), so this
/// config is @MainActor-isolated rather than Sendable. Decided in Task 1.3.
@MainActor
public struct BiangBiangConfig {
    public let branding: Branding
    public let languages: [LanguageProfile]
    public let extraSettings: [SettingDescriptor]
    public let plugins: [any FeaturePlugin]
    public let features: FeatureFlags
    public let strings: UIStrings

    /// Lower clamp for the OCR overlay transliteration font scale ratio
    /// (`RecognizedTextOverlay`). Defaults to 0.6; apps may raise/lower it.
    public let minimumOcrScaleFactor: CGFloat

    public init(branding: Branding, languages: [LanguageProfile],
                extraSettings: [SettingDescriptor], plugins: [any FeaturePlugin],
                features: FeatureFlags, strings: [String: String]?,
                minimumOcrScaleFactor: CGFloat = 0.6)
    {
        self.branding = branding
        self.languages = languages
        self.extraSettings = extraSettings
        self.plugins = plugins
        self.features = features
        self.strings = UIStrings.merged(with: strings)
        self.minimumOcrScaleFactor = minimumOcrScaleFactor
    }
}
