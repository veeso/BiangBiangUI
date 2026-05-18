package dev.veeso.biangbiangui.examples.chinese

import android.content.Context
import org.json.JSONObject

/**
 * Ported faithfully from the BiangBiang Hanzi reference Android
 * `JyutpingDictionary`. Loads the Han -> Jyutping table from `cantonese.json`
 * shipped in this module's assets.
 *
 * Mirrors iOS `ChineseExample/JyutpingDictionary` (which read
 * `Bundle.module`); on Android the table is loaded from `Context.assets`.
 */
class JyutpingDictionary private constructor(private val table: Map<String, String>) {

    val size: Int get() = table.size

    /** Returns the primary Jyutping reading for a single Han character, or `null` if unknown. */
    fun reading(character: String): String? = table[character]

    companion object {
        @Volatile
        private var instance: JyutpingDictionary? = null

        fun get(context: Context): JyutpingDictionary =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        private fun load(context: Context): JyutpingDictionary {
            val json = context.assets.open("cantonese.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromJson(json)
        }

        /** Visible for tests. */
        fun fromJson(json: String): JyutpingDictionary {
            val obj = JSONObject(json)
            val map = HashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            return JyutpingDictionary(map)
        }
    }
}
