import Foundation

struct CameraSessionLog: Equatable {
    static let capacity = 50

    /// Explicit Unicode White_Space set, mirrored in Android; platform defaults differ.
    private static let boundaryWhitespace = CharacterSet(charactersIn:
        "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{0020}\u{0085}\u{00A0}\u{1680}"
            + "\u{2000}\u{2001}\u{2002}\u{2003}\u{2004}\u{2005}\u{2006}\u{2007}\u{2008}\u{2009}\u{200A}"
            + "\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}")

    struct Entry: Identifiable, Equatable, Sendable {
        let id: UUID
        let original: String
        let transliteration: String
    }

    private(set) var entries: [Entry] = []

    mutating func ingest(_ pairs: [(original: String, transliteration: String)]) -> [UUID] {
        var insertedIDs: [UUID] = []

        for pair in pairs {
            let original = pair.original.trimmingCharacters(in: Self.boundaryWhitespace)
            guard !original.isEmpty, !entries.contains(where: { $0.original == original }) else {
                continue
            }

            let entry = Entry(
                id: UUID(),
                original: original,
                transliteration: pair.transliteration
            )
            entries.insert(entry, at: 0)
            insertedIDs.append(entry.id)

            if entries.count > Self.capacity {
                entries.removeLast(entries.count - Self.capacity)
            }
        }

        return insertedIDs
    }

    mutating func clear() {
        entries.removeAll()
    }
}
