import CoreGraphics

/// A recognised text run in image-pixel coordinates (top-left origin).
public struct OCRTextBox: Sendable, Equatable {
    public let text: String
    public let rect: CGRect

    public init(text: String, rect: CGRect) {
        self.text = text
        self.rect = rect
    }
}

/// Pluggable OCR backend. The library normalises the frame to a still
/// `CGImage`; the service only performs raw recognition and returns boxes in
/// image-pixel space. Rotation, throttle, debounce, spacing and
/// transliteration stay library-owned. Mirrors Android `OcrService`.
public protocol OCRService: Sendable {
    /// Recognise text in `image`. `recognizer` is the active profile's
    /// script hint so one implementation can dispatch by script.
    func recognize(_ image: CGImage,
                   recognizer: OCRRecognizer) async -> [OCRTextBox]
}
