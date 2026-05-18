//
//  ArabicNormalizer.swift
//  ArabicExample
//
//  Ported faithfully from the Harakat-Lens iOS app (`ArabicNormalizer.swift`).
//  Dual-mode normalizer: aggressive for fuzzy matching, minimal for ICU
//  transliteration. Unchanged from the reference.
//

import Foundation

nonisolated enum NormalizationMode {
    /// Aggressive normalization for fuzzy matching: strips harakat, unifies
    /// alef variants and alef maqsura, drops tatweel.
    case matching
    /// Minimal cleanup for ICU transliteration: drops tatweel only, preserves
    /// harakat and alef variants so ICU can render them as vowels/glyphs.
    case transliteration
}

nonisolated struct ArabicNormalizer {
    let mode: NormalizationMode
    /// When `true` and `mode == .matching`, ta marbuta (ة) is unified to ha (ه).
    /// Ignored in `.transliteration` mode.
    let unifyTaMarbuta: Bool

    init(mode: NormalizationMode = .matching, unifyTaMarbuta: Bool = false) {
        self.mode = mode
        self.unifyTaMarbuta = unifyTaMarbuta
    }

    func normalize(_ input: String) -> String {
        var scalars = String.UnicodeScalarView()
        scalars.reserveCapacity(input.unicodeScalars.count)

        for scalar in input.unicodeScalars {
            let v = scalar.value

            // Tatweel — always dropped.
            if v == 0x0640 { continue }

            switch mode {
            case .matching:
                // Harakat (U+064B–U+0652) + superscript alef (U+0670)
                if (0x064B ... 0x0652).contains(v) || v == 0x0670 { continue }
                // Alef variants → ا
                if v == 0x0623 || v == 0x0625 || v == 0x0622 || v == 0x0671 {
                    scalars.append(Unicode.Scalar(0x0627)!)
                    continue
                }
                // Alef maqsura → ي
                if v == 0x0649 {
                    scalars.append(Unicode.Scalar(0x064A)!)
                    continue
                }
                // Optional ta marbuta → ه
                if unifyTaMarbuta, v == 0x0629 {
                    scalars.append(Unicode.Scalar(0x0647)!)
                    continue
                }
            case .transliteration:
                break
            }

            scalars.append(scalar)
        }

        return String(scalars)
            .replacingOccurrences(
                of: "\\s+",
                with: " ",
                options: .regularExpression
            )
            .trimmingCharacters(in: .whitespaces)
    }
}
