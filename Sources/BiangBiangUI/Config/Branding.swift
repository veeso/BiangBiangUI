public struct Branding: Sendable {
    public let appName: String
    public let accentColorHex: String
    public let logoAssetName: String
    public let buttonLogoAssetName: String
    public let githubRepo: String // e.g. "veeso/BiangBiang-Hanzi"
    public let supportEmail: String
    public let appStoreId: String
    public let playStoreId: String
    public init(appName: String, accentColorHex: String, logoAssetName: String,
                buttonLogoAssetName: String, githubRepo: String, supportEmail: String,
                appStoreId: String, playStoreId: String)
    {
        self.appName = appName; self.accentColorHex = accentColorHex
        self.logoAssetName = logoAssetName; self.buttonLogoAssetName = buttonLogoAssetName
        self.githubRepo = githubRepo; self.supportEmail = supportEmail
        self.appStoreId = appStoreId; self.playStoreId = playStoreId
    }
}
