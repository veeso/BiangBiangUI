package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.services.HistoryEntry
import dev.veeso.biangbiangui.services.HistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors iOS Phase 2 `HistoryStoreTests`. */
class HistoryStoreTest {
    private fun entry(
        original: String,
        variantId: String = "mandarin",
        id: String = original + variantId,
    ) = HistoryEntry(id, original, "$original-t", variantId, 0L)

    @Test
    fun insertPrependsNewest() {
        var list = HistoryStore.insert(entry("A"), emptyList())
        list = HistoryStore.insert(entry("B"), list)
        assertEquals(listOf("B", "A"), list.map { it.original })
    }

    @Test
    fun skipsConsecutiveDuplicateSameVariant() {
        var list = HistoryStore.insert(entry("A"), emptyList())
        list = HistoryStore.insert(entry("A"), list)
        assertEquals(1, list.size)
    }

    @Test
    fun keepsSameTextDifferentVariant() {
        var list = HistoryStore.insert(entry("A", "mandarin"), emptyList())
        list = HistoryStore.insert(entry("A", "cantonese"), list)
        assertEquals(2, list.size)
    }

    @Test
    fun capEvictsOldestBeyond500() {
        var list = emptyList<HistoryEntry>()
        for (i in 0 until 520) {
            list = HistoryStore.insert(entry("E$i", id = "E$i"), list)
        }
        assertEquals(500, list.size)
        assertEquals("E519", list.first().original)
        assertEquals("E20", list.last().original)
    }

    @Test
    fun deleteRemovesById() {
        val a = entry("A")
        var list = HistoryStore.insert(a, emptyList())
        list = HistoryStore.delete(a.id, list)
        assertTrue(list.isEmpty())
    }

    @Test
    fun clearEmptiesList() {
        assertTrue(HistoryStore.clear().isEmpty())
    }
}
