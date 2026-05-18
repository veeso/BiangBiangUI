package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.services.HistoryEntry
import dev.veeso.biangbiangui.services.HistorySerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors the reference BiangBiang Hanzi Android `HistorySerializerTest`,
 * generalised to the opaque `variantId` (iOS Phase 2 `Codable` round-trip).
 */
class HistorySerializerTest {
    @Test
    fun roundTripsList() {
        val list = listOf(
            HistoryEntry("id1", "你好", "nei5 hou2", "cantonese", 1234L),
            HistoryEntry("id2", "我", "wǒ", "mandarin", 5678L),
        )
        val json = HistorySerializer.toJson(list)
        val back = HistorySerializer.fromJson(json)
        assertEquals(list, back)
    }

    @Test
    fun fromBlankReturnsEmpty() {
        assertEquals(emptyList<HistoryEntry>(), HistorySerializer.fromJson(""))
    }

    @Test
    fun fromMalformedReturnsEmpty() {
        assertEquals(emptyList<HistoryEntry>(), HistorySerializer.fromJson("not-json"))
    }
}
