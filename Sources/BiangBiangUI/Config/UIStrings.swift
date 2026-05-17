public struct UIStrings: Sendable {
    public var inputTitle = "Text"
    public var outputTitle = "Transliteration"
    public var translationTitle = "Translation"
    public var paste = "Paste"
    public var copy = "Copy"
    public var listen = "Listen"
    public var stop = "Stop"
    public var save = "Save"
    public var savedToHistory = "Saved to History"
    public var textCopied = "Text copied"
    public var clearAll = "Clear All"
    public var clearAllConfirm = "Delete all history?"
    public var original = "Original"
    public var transliterated = "Transliterated"
    public var historyEmpty = "No history yet"
    public var cameraDisabledTitle = "Camera access disabled"
    public var cameraDisabledMessage = "Enable camera access in Settings to scan text."
    public var openSettings = "Open Settings"
    public var tapToCopyLongPressToSave = "Tap to copy · long-press to save"
    public var translate = "Translate"
    public var reportBug = "Report a bug"
    public var openGithubIssues = "Open GitHub Issues"
    public var sendEmail = "Send Email"
    public var rateTitle = "Enjoying the app?"
    public var rateMessage = "A quick rating really helps."
    public var rateNow = "Rate now"
    public var notNow = "Not now"
    public var dontAskAgain = "Don't ask again"
    public var translationLanguage = "Translation language"
    public var tabText = "Text"
    public var tabCamera = "Camera"
    public var tabHistory = "History"
    public var tabSettings = "Settings"
    public init() {}

    /// Merge override values keyed by property name over the English defaults.
    static func merged(with overrides: [String: String]?) -> UIStrings {
        var s = UIStrings()
        guard let o = overrides else { return s }
        if let v = o["inputTitle"] { s.inputTitle = v }
        if let v = o["outputTitle"] { s.outputTitle = v }
        if let v = o["translationTitle"] { s.translationTitle = v }
        if let v = o["paste"] { s.paste = v }
        if let v = o["copy"] { s.copy = v }
        if let v = o["listen"] { s.listen = v }
        if let v = o["stop"] { s.stop = v }
        if let v = o["save"] { s.save = v }
        if let v = o["savedToHistory"] { s.savedToHistory = v }
        if let v = o["textCopied"] { s.textCopied = v }
        if let v = o["clearAll"] { s.clearAll = v }
        if let v = o["clearAllConfirm"] { s.clearAllConfirm = v }
        if let v = o["original"] { s.original = v }
        if let v = o["transliterated"] { s.transliterated = v }
        if let v = o["historyEmpty"] { s.historyEmpty = v }
        if let v = o["cameraDisabledTitle"] { s.cameraDisabledTitle = v }
        if let v = o["cameraDisabledMessage"] { s.cameraDisabledMessage = v }
        if let v = o["openSettings"] { s.openSettings = v }
        if let v = o["tapToCopyLongPressToSave"] { s.tapToCopyLongPressToSave = v }
        if let v = o["translate"] { s.translate = v }
        if let v = o["reportBug"] { s.reportBug = v }
        if let v = o["openGithubIssues"] { s.openGithubIssues = v }
        if let v = o["sendEmail"] { s.sendEmail = v }
        if let v = o["rateTitle"] { s.rateTitle = v }
        if let v = o["rateMessage"] { s.rateMessage = v }
        if let v = o["rateNow"] { s.rateNow = v }
        if let v = o["notNow"] { s.notNow = v }
        if let v = o["dontAskAgain"] { s.dontAskAgain = v }
        if let v = o["translationLanguage"] { s.translationLanguage = v }
        if let v = o["tabText"] { s.tabText = v }
        if let v = o["tabCamera"] { s.tabCamera = v }
        if let v = o["tabHistory"] { s.tabHistory = v }
        if let v = o["tabSettings"] { s.tabSettings = v }
        return s
    }
}
