import Foundation

/// Pure history-list mutation rules. No persistence, no UI.
public enum HistoryStore {
    /// Silent safety cap. Never surfaced in UI.
    public static let safetyCap = 500

    /// Prepend `entry` (newest first), skipping if it duplicates the
    /// most-recent entry by original text + variantId. Evicts oldest entries
    /// beyond `safetyCap`.
    public static func insert(
        _ entry: HistoryEntry,
        into list: [HistoryEntry]
    ) -> [HistoryEntry] {
        if let newest = list.first,
           newest.original == entry.original,
           newest.variantId == entry.variantId
        {
            return list
        }
        var result = list
        result.insert(entry, at: 0)
        if result.count > safetyCap {
            result.removeLast(result.count - safetyCap)
        }
        return result
    }

    public static func delete(
        id: UUID,
        from list: [HistoryEntry]
    ) -> [HistoryEntry] {
        list.filter { $0.id != id }
    }

    public static func clear() -> [HistoryEntry] {
        []
    }
}
