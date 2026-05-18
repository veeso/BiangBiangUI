//
//  QuranAyah.swift
//  ArabicExample
//
//  Ported verbatim from the Harakat-Lens iOS app.
//

import Foundation

nonisolated struct QuranAyah: Decodable, Identifiable, Hashable {
    let surah: Int
    let ayah: Int
    let text: String
    let normalized: String
    let transliteration: String
    let translationEn: String

    var id: String {
        "\(surah):\(ayah)"
    }

    enum CodingKeys: String, CodingKey {
        case surah
        case ayah
        case text
        case normalized
        case transliteration
        case translationEn = "translation_en"
    }
}
