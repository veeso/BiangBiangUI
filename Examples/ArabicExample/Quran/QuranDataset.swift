//
//  QuranDataset.swift
//  ArabicExample
//
//  Ported faithfully from the Harakat-Lens iOS app. Bundle loading adapted
//  from `Bundle.main` to `Bundle.module` so `quran.json` / `surah-names.json`
//  ship in this SwiftPM target's resource bundle (mirrors `ChineseExample`).
//

import Foundation

actor QuranDataset {
    static let shared = QuranDataset()

    private(set) var all: [QuranAyah] = []
    private(set) var surahNames: [Int: SurahName] = [:]
    private(set) var tokenIndex: [String: [Int]] = [:]
    private var loaded = false

    init() {}

    func loadIfNeeded() async {
        if loaded { return }
        loaded = true

        let ayat = await Self.decodeArray(QuranAyah.self, named: "quran")
        let names = await Self.decodeArray(SurahName.self, named: "surah-names")
        all = ayat
        surahNames = Dictionary(uniqueKeysWithValues: names.map { ($0.number, $0) })

        var index: [String: [Int]] = [:]
        for (i, ayah) in ayat.enumerated() {
            for token in ayah.normalized.split(separator: " ") {
                let key = String(token)
                index[key, default: []].append(i)
            }
        }
        tokenIndex = index
    }

    private static func decodeArray<T: Decodable>(
        _: T.Type,
        named: String
    ) async -> [T] {
        guard let url = Bundle.module.url(forResource: named, withExtension: "json") else {
            print("⚠️ QuranDataset: \(named).json missing from bundle")
            return []
        }
        do {
            let data = try await Task.detached(priority: .utility) {
                try Data(contentsOf: url)
            }.value
            return try JSONDecoder().decode([T].self, from: data)
        } catch {
            print("⚠️ QuranDataset: failed to decode \(named).json: \(error)")
            return []
        }
    }
}
