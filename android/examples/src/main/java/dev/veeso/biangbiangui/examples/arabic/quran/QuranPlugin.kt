package dev.veeso.biangbiangui.examples.arabic.quran

import android.content.Context
import androidx.compose.runtime.Composable
import dev.veeso.biangbiangui.protocols.AudioProvider
import dev.veeso.biangbiangui.protocols.FeaturePlugin
import dev.veeso.biangbiangui.protocols.PluginTab
import dev.veeso.biangbiangui.protocols.ProcessedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * A [FeaturePlugin] proving the library's plugin slot end to end, mirroring
 * iOS `ArabicExample/Quran/QuranPlugin`:
 * - [tabs]: a Quran browser tab.
 * - [onProcessedText]: runs [QuranMatcher.match] asynchronously and caches
 *   the hit so the next [inlineResultView] call can render it.
 * - [inlineResultView]: returns the [QuranMatchView] content lambda on a hit,
 *   else `null`.
 * - [audioProvider]: an [EveryAyahAudioProvider].
 *
 * Concurrency design: iOS isolated the cache on `@MainActor` (the protocol is
 * `@MainActor`). The Android [FeaturePlugin] is a plain interface callable
 * from any thread, so the cache is held in a single immutable snapshot inside
 * an [AtomicReference] — `onProcessedText` launches a coroutine that does the
 * heavy work on the [QuranDataset] / [QuranMatcher] (off the caller's thread)
 * and atomically publishes the snapshot; [inlineResultView] is a lock-free
 * atomic read. No locks, no torn reads, no data races.
 */
class QuranPlugin(context: Context) : FeaturePlugin {

    private val appContext = context.applicationContext
    private val dataset = QuranDataset(appContext)
    private val matcher = QuranMatcher(dataset)
    private val everyAyah = EveryAyahAudioProvider(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class CacheSnapshot(
        val original: String,
        val match: QuranMatch,
        val surahName: SurahName?,
    )

    private val cache = AtomicReference<CacheSnapshot?>(null)

    override val tabs: List<PluginTab>
        get() = listOf(
            PluginTab(
                id = "quran",
                title = "Quran",
                systemImage = "book.closed.fill",
                content = { QuranBrowserView() },
            ),
        )

    override fun onProcessedText(result: ProcessedText) {
        val query = result.original
        if (query.trim().isEmpty()) {
            cache.set(null)
            return
        }
        scope.launch {
            dataset.loadIfNeeded()
            val match = matcher.match(query)
            if (match == null) {
                cache.set(null)
                return@launch
            }
            val surahName = dataset.surahNames[match.ayah.surah]
            cache.set(CacheSnapshot(original = query, match = match, surahName = surahName))
        }
    }

    override fun inlineResultView(result: ProcessedText): (@Composable () -> Unit)? {
        val snapshot = cache.get() ?: return null
        if (snapshot.original != result.original) return null
        return {
            QuranMatchView(
                match = snapshot.match,
                surahName = snapshot.surahName,
                audio = everyAyah,
            )
        }
    }

    override val audioProvider: AudioProvider
        get() = everyAyah
}
