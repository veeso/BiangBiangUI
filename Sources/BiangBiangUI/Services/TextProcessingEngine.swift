import Foundation

/// Detects script spans via a regex built from `scriptRanges`, transliterates each span
/// using an injected `Transliterator`, and applies the reference spacing and cleanup rules.
///
/// The regex is immutable after initialisation; `@unchecked Sendable` is used only because
/// `NSRegularExpression` does not conform to `Sendable`, but is thread-safe when read-only.
public final class TextProcessingEngine: @unchecked Sendable {
    // MARK: - Stored properties

    private let regex: NSRegularExpression
    private let transliterator: any Transliterator

    // MARK: - Init

    /// Builds a `TextProcessingEngine` whose span detector matches any run of characters
    /// whose Unicode scalar values fall within any of `scriptRanges`.
    ///
    /// - Parameters:
    ///   - scriptRanges: One or more closed Unicode scalar ranges (e.g. `[0x4E00...0x9FFF]`).
    ///   - transliterator: Strategy that converts an isolated script span to a Latin string.
    public init(scriptRanges: [ClosedRange<UInt32>], transliterator: any Transliterator) {
        // Build a character-class pattern: [\uXXXX-\uYYYYꪪ-뮻...]+
        // NSRegularExpression uses ICU regex; \uXXXX (4-hex) escapes work for BMP code points,
        // and \U{XXXXXX} (with braces) for supplementary planes — but all CJK ranges used here
        // are BMP so 4-hex is sufficient and unambiguous.
        let classBody = scriptRanges.map { range in
            String(format: "\\u%04X-\\u%04X", range.lowerBound, range.upperBound)
        }.joined()

        let pattern = "[\(classBody)]+"
        // swiftlint:disable:next force_try — pattern is machine-generated from UInt32 values
        regex = try! NSRegularExpression(pattern: pattern, options: [])
        self.transliterator = transliterator
    }

    // MARK: - Public API

    /// Transliterates all script spans in `text`, preserving non-script characters and
    /// applying the reference leading/trailing spacing rules and post-pass cleanup.
    ///
    /// - Returns: The processed string, or `nil` if `text` contained no script spans.
    public func process(_ text: String) -> String? {
        guard containsScript(text) else { return nil }

        let nsRange = NSRange(text.startIndex..., in: text)
        var result = text

        // Iterate matches in reverse so that replacing a span does not invalidate
        // the character offsets of earlier (lower-index) spans.
        let matches = regex.matches(in: text, range: nsRange).reversed()

        for match in matches {
            guard let range = Range(match.range, in: result) else { continue }

            let span = String(result[range])
            let transliterated = transliterator.transliterate(span)

            // Leading space: insert unless the span is at the very start of the string
            // or the immediately preceding character is a space or sentence punctuation.
            let needsLeadingSpace: Bool = {
                guard range.lowerBound != result.startIndex else { return false }
                let prevChar = result[result.index(before: range.lowerBound)]
                return prevChar != " " && !".,!?;:".contains(prevChar)
            }()

            // Trailing space: insert only when the immediately following character is
            // an ASCII letter or digit (prevents gluing the romanisation to the next word).
            let needsTrailingSpace: Bool = {
                guard range.upperBound < result.endIndex else { return false }
                let nextChar = result[range.upperBound]
                return nextChar.isASCII && (nextChar.isLetter || nextChar.isNumber)
            }()

            let replacement =
                (needsLeadingSpace ? " " : "") +
                transliterated +
                (needsTrailingSpace ? " " : "")

            result.replaceSubrange(range, with: replacement)
        }

        // Post-pass 1: remove any whitespace that ended up immediately before punctuation.
        result = result.replacingOccurrences(
            of: "\\s+([.,!?;:])",
            with: "$1",
            options: .regularExpression
        )

        // Post-pass 2: collapse runs of two or more spaces into a single space.
        result = result.replacingOccurrences(
            of: "\\s{2,}",
            with: " ",
            options: .regularExpression
        )

        return result.trimmingCharacters(in: .whitespaces)
    }

    /// Returns `true` iff `text` contains at least one script span that the regex can match.
    public func containsScript(_ text: String) -> Bool {
        let nsRange = NSRange(text.startIndex..., in: text)
        return regex.firstMatch(in: text, range: nsRange) != nil
    }
}
