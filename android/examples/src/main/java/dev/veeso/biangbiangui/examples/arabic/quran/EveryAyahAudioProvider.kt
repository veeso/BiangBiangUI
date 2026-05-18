package dev.veeso.biangbiangui.examples.arabic.quran

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.veeso.biangbiangui.protocols.AudioProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The EveryAyah-streaming part of Harakat-Lens's `AudioPlayerService`, ported
 * into a library [AudioProvider], mirroring iOS
 * `ArabicExample/Quran/EveryAyahAudioProvider`. Streams
 * `https://everyayah.com/data/Alafasy_128kbps/SSSAAA.mp3` for an ayah and
 * falls back to system TTS for arbitrary text.
 *
 * The iOS port used `AVPlayer` + `AVSpeechSynthesizer`; on Android the
 * equivalents are [MediaPlayer] (async-prepared stream) and [TextToSpeech].
 * Reciter, URL format, `surah:ayah` parsing and the TTS-fallback rule are
 * unchanged from the reference.
 */
class EveryAyahAudioProvider(context: Context) : AudioProvider {

    sealed interface State {
        data object Idle : State
        data class LoadingAyah(val surah: Int, val ayah: Int) : State
        data class PlayingAyah(val surah: Int, val ayah: Int) : State
        data object SpeakingTts : State
    }

    private val appContext = context.applicationContext
    private val reciter = "Alafasy_128kbps"

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    override val isPlaying: Boolean
        get() = _state.value != State.Idle

    private var player: MediaPlayer? = null

    private var ttsInitialized = false
    private var pendingTts: Pair<String, String>? = null
    private val tts = TextToSpeech(appContext) { status ->
        ttsInitialized = status == TextToSpeech.SUCCESS
        if (ttsInitialized) {
            pendingTts?.let { (text, code) -> enqueueTts(text, code) }
        }
        pendingTts = null
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = State.SpeakingTts
            }

            override fun onDone(utteranceId: String?) {
                if (_state.value == State.SpeakingTts) _state.value = State.Idle
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (_state.value == State.SpeakingTts) _state.value = State.Idle
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (_state.value == State.SpeakingTts) _state.value = State.Idle
            }
        })
    }

    /**
     * Library entry point. When [text] parses as `"surah:ayah"` (the form
     * [QuranAyah.id] produces) the recitation is streamed from EveryAyah;
     * otherwise the text is spoken with the system synthesizer in the given
     * language (falling back to Arabic).
     */
    override fun play(text: String, languageCode: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ref = parseAyahReference(trimmed)
        if (ref != null) {
            playAyah(ref.first, ref.second)
        } else {
            speak(trimmed, languageCode ?: "ar")
        }
    }

    override fun stop() {
        if (tts.isSpeaking) tts.stop()
        pendingTts = null
        player?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.reset()
            it.release()
        }
        player = null
        _state.value = State.Idle
    }

    fun playAyah(surah: Int, ayah: Int) {
        stop()
        val url = ayahUrl(surah, ayah)
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setDataSource(url)
            setOnPreparedListener { prepared ->
                if (_state.value == State.LoadingAyah(surah, ayah)) {
                    _state.value = State.PlayingAyah(surah, ayah)
                    prepared.start()
                }
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
        }
        player = mp
        _state.value = State.LoadingAyah(surah, ayah)
        mp.prepareAsync()
    }

    fun speak(text: String, languageCode: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        stop()
        if (!ttsInitialized) {
            pendingTts = trimmed to languageCode
            return
        }
        enqueueTts(trimmed, languageCode)
    }

    fun isPlayingAyah(surah: Int, ayah: Int): Boolean = when (val s = _state.value) {
        is State.PlayingAyah -> s.surah == surah && s.ayah == ayah
        is State.LoadingAyah -> s.surah == surah && s.ayah == ayah
        else -> false
    }

    fun isLoadingAyah(surah: Int, ayah: Int): Boolean = when (val s = _state.value) {
        is State.LoadingAyah -> s.surah == surah && s.ayah == ayah
        else -> false
    }

    /** Releases the underlying TTS engine. Call from the owner's lifecycle end. */
    fun shutdown() {
        stop()
        tts.shutdown()
        ttsInitialized = false
    }

    private fun enqueueTts(text: String, languageCode: String) {
        tts.language = Locale.forLanguageTag(languageCode)
        _state.value = State.SpeakingTts
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun ayahUrl(surah: Int, ayah: Int): String {
        val s = "%03d".format(surah)
        val a = "%03d".format(ayah)
        return "https://everyayah.com/data/$reciter/$s$a.mp3"
    }

    private fun parseAyahReference(text: String): Pair<Int, Int>? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val surah = parts[0].trim().toIntOrNull() ?: return null
        val ayah = parts[1].trim().toIntOrNull() ?: return null
        return surah to ayah
    }

    private companion object {
        const val UTTERANCE_ID = "biangbiang-ui-everyayah-tts"
    }
}
