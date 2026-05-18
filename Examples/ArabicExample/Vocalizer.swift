//
//  Vocalizer.swift
//  ArabicExample
//
//  Ported verbatim from the Harakat-Lens iOS app (`Vocalizer/Vocalizer.swift`).
//

import Foundation

struct Vocalizer {
    private let dictionary: VocalizationDictionary

    init(dictionary: VocalizationDictionary = .shared) {
        self.dictionary = dictionary
    }

    /// If `word` is bare (no harakat), return its dictionary vocalization or
    /// the original word on miss. If `word` already contains any harakat
    /// character, return it unchanged.
    func vocalize(_ word: String) -> String {
        if Self.containsHarakat(word) { return word }
        return dictionary.lookup(word) ?? word
    }

    private static func containsHarakat(_ word: String) -> Bool {
        for scalar in word.unicodeScalars {
            let v = scalar.value
            if (0x064B ... 0x0652).contains(v) || v == 0x0670 { return true }
        }
        return false
    }
}
