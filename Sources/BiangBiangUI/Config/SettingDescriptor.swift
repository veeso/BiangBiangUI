public struct SettingDescriptor: Sendable, Identifiable {
    public enum Kind: Sendable { case toggle, picker(options: [String]) }
    public let key: String
    public let kind: Kind
    public let label: String
    public let defaultValue: String        // "true"/"false" for toggles; option for pickers
    public let footer: String?
    public var id: String { key }
    public init(key: String, kind: Kind, label: String,
                defaultValue: String, footer: String? = nil) {
        self.key = key; self.kind = kind; self.label = label
        self.defaultValue = defaultValue; self.footer = footer
    }
}
