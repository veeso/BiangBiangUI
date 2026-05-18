//
//  VocalizationDictionary.swift
//  ArabicExample
//
//  Ported faithfully from the Harakat-Lens iOS app
//  (`Vocalizer/VocalizationDictionary.swift`). Bundle loading adapted from
//  `Bundle.main` to `Bundle.module` so the plist ships in this SwiftPM
//  target's resource bundle (mirrors `ChineseExample`).
//

import Foundation

final class VocalizationDictionary: @unchecked Sendable {
    static let shared = VocalizationDictionary()

    private let map: [String: String]

    /// Test-friendly initializer. Production code uses `shared`.
    init(map: [String: String]) {
        self.map = map
    }

    private convenience init() {
        self.init(map: Self.loadFromBundle())
    }

    func lookup(_ bare: String) -> String? {
        map[bare]
    }

    private static func loadFromBundle() -> [String: String] {
        guard let url = Bundle.module.url(forResource: "vocab", withExtension: "plist") else {
            print("⚠️ VocalizationDictionary: vocab.plist missing from bundle")
            return [:]
        }
        do {
            let data = try Data(contentsOf: url)
            let decoded = try PropertyListSerialization.propertyList(
                from: data,
                options: [],
                format: nil
            )
            guard let dict = decoded as? [String: String] else {
                print("⚠️ VocalizationDictionary: vocab.plist root is not [String: String]")
                return [:]
            }
            return dict
        } catch {
            print("⚠️ VocalizationDictionary: failed to load vocab.plist: \(error)")
            return [:]
        }
    }
}
