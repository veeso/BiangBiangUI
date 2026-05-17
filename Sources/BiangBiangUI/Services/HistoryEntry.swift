import Foundation

public struct HistoryEntry: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public let original: String
    public let transliteration: String
    public let variantId: String
    public let timestamp: Date

    public init(
        id: UUID = UUID(),
        original: String,
        transliteration: String,
        variantId: String,
        timestamp: Date = Date()
    ) {
        self.id = id
        self.original = original
        self.transliteration = transliteration
        self.variantId = variantId
        self.timestamp = timestamp
    }
}
