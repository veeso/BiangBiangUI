package dev.veeso.biangbiangui.ui.screens.textmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.veeso.biangbiangui.services.TextProcessingEngine
import dev.veeso.biangbiangui.ui.AppDesign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the Text screen's input/output/translation state and the 800ms
 * debounce. Ported from the reference BiangBiang Hanzi Android
 * `ui/screens/textmode/TextModeViewModel.kt`.
 *
 * Generalisations vs. the reference (mirroring iOS Phase 3 `TextScreen`):
 * - The `TextProcessor` + `JyutpingDictionary`/`Mode` switch is replaced by a
 *   single injected [TextProcessingEngine] (the engine already encapsulates
 *   the active variant's transliterator). The screen pushes the current
 *   `BiangBiangContext.engine` via [setEngine]; changing variant pushes a new
 *   engine and re-processes (the Android equivalent of iOS `rebuildEngine()`).
 * - TTS moved out of the VM: the screen drives the resolved `AudioProvider`
 *   directly (plugin override or system TTS), matching iOS.
 * - `translate` source language is derived from the active variant's
 *   `ttsLanguageCode` (BCP-47), the same basis iOS uses for the Translation
 *   framework; target is the user's selected `userLanguage`.
 */
class TextModeViewModel : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _outputText = MutableStateFlow("")
    val outputText = _outputText.asStateFlow()

    private val _translatedText = MutableStateFlow("")
    val translatedText = _translatedText.asStateFlow()

    private var engine: TextProcessingEngine? = null
    private var debounceJob: Job? = null

    /**
     * The ML Kit [Translator] for the in-flight [translate] call. It is a
     * native [java.io.Closeable] holding a translation model, so it MUST be
     * closed once its terminal state (success or failure) is reached. Tracked
     * here so an in-flight translator that outlives the ViewModel is also
     * closed in [onCleared].
     */
    private var activeTranslator: Translator? = null

    /** Result of processing the current input; emitted to plugins by the screen. */
    var onProcessed: ((original: String, transliteration: String) -> Unit)? = null

    /**
     * Pushes the active [TextProcessingEngine] (from `BiangBiangContext`). When
     * it changes (variant switch), re-process — the Android `rebuildEngine()`.
     */
    fun setEngine(newEngine: TextProcessingEngine) {
        if (engine === newEngine) return
        engine = newEngine
        _translatedText.value = ""
        processInput()
    }

    fun onInputChanged(newText: String) {
        _inputText.value = newText
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AppDesign.INPUT_DEBOUNCE_MS)
            processInput()
        }
    }

    private fun processInput() {
        val text = _inputText.value
        if (text.trim().isEmpty()) {
            _outputText.value = ""
            _translatedText.value = ""
            return
        }
        val output = engine?.process(text) ?: ""
        _outputText.value = output
        if (output.isNotEmpty()) {
            onProcessed?.invoke(text, output)
        }
    }

    /**
     * Translates the current input via ML Kit. [sourceLanguageCode] is the
     * active variant's BCP-47 `ttsLanguageCode` (its natural written
     * language); [userLanguage] is the selected translation target.
     */
    fun translate(sourceLanguageCode: String?, userLanguage: String) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val source = sourceLanguageCode
            ?.let { TranslateLanguage.fromLanguageTag(it) }
            ?: TranslateLanguage.fromLanguageTag("")
        val target = TranslateLanguage.fromLanguageTag(userLanguage)
        if (source == null || target == null) {
            _translatedText.value = "⚠️ Translation unavailable for this language"
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        // Close any previous in-flight translator before starting a new one,
        // then track this one so its native model is released on the terminal
        // state (success/failure) and in onCleared() if it outlives the VM.
        activeTranslator?.close()
        val translator = Translation.getClient(options)
        activeTranslator = translator

        fun release() {
            translator.close()
            if (activeTranslator === translator) activeTranslator = null
        }

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        _translatedText.value = translated
                        release()
                    }
                    .addOnFailureListener { e ->
                        _translatedText.value = "⚠️ Translation failed: ${e.message}"
                        release()
                    }
            }
            .addOnFailureListener { e ->
                _translatedText.value = "⚠️ Model download failed: ${e.message}"
                release()
            }
    }

    override fun onCleared() {
        // Release the native translation model if a translate() call is still
        // in flight when the ViewModel is destroyed.
        activeTranslator?.close()
        activeTranslator = null
        super.onCleared()
    }
}
