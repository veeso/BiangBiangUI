/// Converts an already-isolated run of script characters to Latin.
/// The library's `TextProcessingEngine` owns span detection, passthrough and spacing;
/// the app implementation only romanises one span. Must be pure and `Sendable`.
public protocol Transliterator: Sendable {
    func transliterate(_ scriptSpan: String) -> String
}
