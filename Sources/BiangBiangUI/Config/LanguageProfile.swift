/// Which OCR engine recognises this script.
public enum OCRRecognizer: String, Sendable, CaseIterable {
    case chinese, latin, arabic, japanese, korean
}

public struct LanguageProfile: Sendable {
    public let id: String
    public let displayName: String
    /// Unicode scalar ranges that delimit a script span (e.g. 0x4E00...0x9FFF).
    public let scriptRanges: [ClosedRange<UInt32>]
    public let ocrRecognizer: OCRRecognizer
    /// May be empty; the variant picker renders only when non-empty.
    public let variants: [LanguageVariant]
    /// Optional app-supplied OCR backend. When `nil`, the library uses
    /// `DefaultOCRService` (Vision on iOS).
    public let ocrService: (any OCRService)?

    public init(id: String, displayName: String, scriptRanges: [ClosedRange<UInt32>],
                ocrRecognizer: OCRRecognizer, variants: [LanguageVariant],
                ocrService: (any OCRService)? = nil)
    {
        self.id = id; self.displayName = displayName
        self.scriptRanges = scriptRanges
        self.ocrRecognizer = ocrRecognizer
        self.variants = variants
        self.ocrService = ocrService
    }
}
