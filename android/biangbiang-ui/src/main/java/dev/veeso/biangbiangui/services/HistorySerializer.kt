package dev.veeso.biangbiangui.services

import org.json.JSONArray
import org.json.JSONObject

/**
 * org.json (de)serialization for the persisted history list.
 *
 * Ported verbatim from the reference BiangBiang Hanzi Android
 * `HistorySerializer`. The only generalisation: the `variant` enum is
 * persisted/restored as the opaque `variantId` string (mirrors iOS Phase 2
 * `Codable` round-trip of `HistoryEntry.variantId`).
 */
object HistorySerializer {
    fun toJson(list: List<HistoryEntry>): String {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("original", e.original)
            o.put("transliteration", e.transliteration)
            o.put("variantId", e.variantId)
            o.put("timestamp", e.timestamp)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<HistoryEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.getString("id"),
                            original = o.getString("original"),
                            transliteration = o.getString("transliteration"),
                            variantId = o.getString("variantId"),
                            timestamp = o.getLong("timestamp"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
