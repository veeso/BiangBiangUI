//
//  PinyinTransliterator.swift
//  ChineseExample
//
//  Mandarin romanisation. Faithfully extracted from the BiangBiang Hanzi
//  reference `hanziToPinyin(hanzi:)`: a single `CFStringTransform` to Latin
//  over the isolated script span. The library's `TextProcessingEngine` owns
//  span detection, passthrough and spacing.
//

import BiangBiangUI
import Foundation

public struct PinyinTransliterator: Transliterator {
    public init() {}

    public func transliterate(_ scriptSpan: String) -> String {
        let mutString = NSMutableString(string: scriptSpan) as CFMutableString
        CFStringTransform(mutString, nil, kCFStringTransformToLatin, false)
        return mutString as String
    }
}
