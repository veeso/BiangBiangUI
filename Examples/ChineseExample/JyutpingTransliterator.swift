//
//  JyutpingTransliterator.swift
//  ChineseExample
//
//  Cantonese romanisation. Faithfully extracted from the BiangBiang Hanzi
//  reference `hanziToJyutping(hanzi:)`: per-character dictionary lookup,
//  unknown characters preserved verbatim, tokens joined by single spaces.
//

import BiangBiangUI
import Foundation

public struct JyutpingTransliterator: Transliterator {
    public init() {}

    public func transliterate(_ scriptSpan: String) -> String {
        let dict = JyutpingDictionary.shared
        var tokens: [String] = []
        for scalar in scriptSpan {
            let key = String(scalar)
            if let reading = dict.reading(for: key) {
                tokens.append(reading)
            } else {
                tokens.append(key)
            }
        }
        return tokens.joined(separator: " ")
    }
}
