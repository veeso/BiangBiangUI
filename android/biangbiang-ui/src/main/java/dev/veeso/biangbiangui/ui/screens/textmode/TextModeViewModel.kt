package dev.veeso.biangbiangui.ui.screens.textmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
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

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated -> _translatedText.value = translated }
                    .addOnFailureListener { e ->
                        _translatedText.value = "⚠️ Translation failed: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                _translatedText.value = "⚠️ Model download failed: ${e.message}"
            }
    }
}
