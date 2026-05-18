package dev.veeso.biangbiangui.examples.arabic

import android.content.Context
import org.json.JSONObject

/**
 * Ported faithfully from the Harakat-Lens iOS app
 * (`Vocalizer/VocalizationDictionary.swift`) and the already-complete iOS
 * `ArabicExample/VocalizationDictionary`.
 *
 * The iOS port loaded a `vocab.plist` binary plist from `Bundle.module`. On
 * Android the same dictionary is shipped as `vocab.json` (a flat
 * `{ bareWord: vocalizedWord }` object, byte-faithfully converted from the
 * iOS plist) and read from `Context.assets`.
 */
class VocalizationDictionary(private val map: Map<String, String>) {

    fun lookup(bare: String): String? = map[bare]

    companion object {
        @Volatile
        private var instance: VocalizationDictionary? = null

        fun get(context: Context): VocalizationDictionary =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        private fun load(context: Context): VocalizationDictionary =
            try {
                val json = context.assets.open("vocab.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                fromJson(json)
            } catch (e: java.io.IOException) {
                println("⚠️ VocalizationDictionary: failed to load vocab.json: $e")
                VocalizationDictionary(emptyMap())
            }

        /** Visible for tests. */
        fun fromJson(json: String): VocalizationDictionary {
            val obj = JSONObject(json)
            val map = HashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            return VocalizationDictionary(map)
        }
    }
}
