package dev.veeso.biangbiangui.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.veeso.biangbiangui.protocols.AudioProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speaks text aloud using the platform [TextToSpeech] engine.
 *
 * Ported from the reference BiangBiang Hanzi Android `AudioPlayerService`.
 * The generalisations mirror iOS Phase 2 `SystemTTSAudioProvider`:
 * - `AudioPlayerService` -> `SystemTtsAudioProvider`.
 * - The app-specific `Language` enum is replaced by an open
 *   `languageCode: String?` BCP-47 parameter resolved via
 *   [Locale.forLanguageTag]; a `null` code is a no-op (covers the
 *   "Listen hidden when no variant selected" case).
 * - Conforms to [AudioProvider] (`play` / `stop` / `isPlaying`).
 */
class SystemTtsAudioProvider(context: Context) : AudioProvider {

    enum class State {
        IDLE,
        SPEAKING,
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    override val isPlaying: Boolean
        get() = _state.value == State.SPEAKING

    private var initialized = false
    private var pending: Pair<String, String>? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        initialized = status == TextToSpeech.SUCCESS
        if (initialized) {
            pending?.let { (text, code) -> enqueue(text, code) }
        }
        pending = null
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = State.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                _state.value = State.IDLE
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.value = State.IDLE
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _state.value = State.IDLE
            }
        })
    }

    /**
     * Speaks [text] aloud using the system speech synthesizer.
     *
     * Any in-progress playback is stopped first. Empty/whitespace-only input
     * or a `null` [languageCode] is ignored. If the engine has not finished
     * initializing the request is queued and spoken once ready.
     *
     * @param languageCode BCP-47 language tag (e.g. `"zh-CN"`, `"ar-SA"`).
     *   Pass `null` to suppress speech.
     */
    override fun play(text: String, languageCode: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || languageCode == null) return
        stop()
        if (!initialized) {
            pending = trimmed to languageCode
            return
        }
        enqueue(trimmed, languageCode)
    }

    /** Stops any active speech and resets [state] to [State.IDLE]. */
    override fun stop() {
        pending = null
        if (initialized) tts.stop()
        _state.value = State.IDLE
    }

    /** Releases the underlying engine. Call from the owner's lifecycle end. */
    fun shutdown() {
        tts.shutdown()
        initialized = false
        _state.value = State.IDLE
    }

    private fun enqueue(text: String, languageCode: String) {
        tts.language = Locale.forLanguageTag(languageCode)
        _state.value = State.SPEAKING
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private companion object {
        const val UTTERANCE_ID = "biangbiang-ui-tts"
    }
}
