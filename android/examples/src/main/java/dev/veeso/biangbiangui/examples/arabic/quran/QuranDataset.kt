package dev.veeso.biangbiangui.examples.arabic.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Ported faithfully from the Harakat-Lens iOS app and the already-complete
 * iOS `ArabicExample/Quran/QuranDataset` (a Swift `actor`). On Android the
 * actor's serialized-access guarantee is provided by a [Mutex]; decoding runs
 * on [Dispatchers.IO]. Bundle loading (`Bundle.module`) is adapted to
 * `Context.assets`.
 */
class QuranDataset(private val context: Context) {

    @Volatile
    var all: List<QuranAyah> = emptyList()
        private set

    @Volatile
    var surahNames: Map<Int, SurahName> = emptyMap()
        private set

    @Volatile
    var tokenIndex: Map<String, List<Int>> = emptyMap()
        private set

    private val loadMutex = Mutex()
    private var loaded = false

    suspend fun loadIfNeeded() {
        loadMutex.withLock {
            if (loaded) return
            loaded = true

            val ayat = decodeAyat()
            val names = decodeSurahNames()
            all = ayat
            surahNames = names.associateBy { it.number }

            val index = HashMap<String, MutableList<Int>>()
            ayat.forEachIndexed { i, ayah ->
                for (token in ayah.normalized.split(" ")) {
                    if (token.isEmpty()) continue
                    index.getOrPut(token) { mutableListOf() }.add(i)
                }
            }
            tokenIndex = index
        }
    }

    private suspend fun decodeAyat(): List<QuranAyah> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("quran.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val arr = JSONArray(json)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QuranAyah(
                            surah = o.getInt("surah"),
                            ayah = o.getInt("ayah"),
                            text = o.getString("text"),
                            normalized = o.getString("normalized"),
                            transliteration = o.getString("transliteration"),
                            translationEn = o.getString("translation_en"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            println("⚠️ QuranDataset: failed to decode quran.json: $e")
            emptyList()
        }
    }

    private suspend fun decodeSurahNames(): List<SurahName> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("surah-names.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val arr = JSONArray(json)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SurahName(
                            number = o.getInt("number"),
                            english = o.getString("english"),
                            transliteration = o.getString("transliteration"),
                            arabic = o.getString("arabic"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            println("⚠️ QuranDataset: failed to decode surah-names.json: $e")
            emptyList()
        }
    }
}
