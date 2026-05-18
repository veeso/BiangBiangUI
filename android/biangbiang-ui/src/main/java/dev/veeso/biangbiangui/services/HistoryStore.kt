package dev.veeso.biangbiangui.services

/**
 * Pure history-list mutation rules. No persistence, no UI.
 *
 * Ported verbatim from the reference BiangBiang Hanzi Android `HistoryStore`.
 * The only generalisation: consecutive-duplicate detection keys on
 * `original` + `variantId` (was `original` + `variant` enum), matching the
 * iOS Phase 2 `HistoryStore` contract.
 */
object HistoryStore {
    /** Silent safety cap. Never surfaced in UI. */
    const val SAFETY_CAP = 500

    /**
     * Prepend [entry] (newest first), skipping if it duplicates the
     * most-recent entry by original text + variantId. Evicts oldest entries
     * beyond [SAFETY_CAP].
     */
    fun insert(
        entry: HistoryEntry,
        list: List<HistoryEntry>,
    ): List<HistoryEntry> {
        val newest = list.firstOrNull()
        if (newest != null &&
            newest.original == entry.original &&
            newest.variantId == entry.variantId
        ) {
            return list
        }
        val result = ArrayList<HistoryEntry>(list.size + 1)
        result.add(entry)
        result.addAll(list)
        return if (result.size > SAFETY_CAP) {
            result.subList(0, SAFETY_CAP).toList()
        } else {
            result
        }
    }

    fun delete(
        id: String,
        list: List<HistoryEntry>,
    ): List<HistoryEntry> = list.filterNot { it.id == id }

    fun clear(): List<HistoryEntry> = emptyList()
}
