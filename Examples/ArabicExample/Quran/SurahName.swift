//
//  SurahName.swift
//  ArabicExample
//
//  Ported verbatim from the Harakat-Lens iOS app.
//

import Foundation

nonisolated struct SurahName: Decodable, Identifiable, Hashable {
    let number: Int
    let english: String
    let transliteration: String
    let arabic: String

    var id: Int {
        number
    }
}
