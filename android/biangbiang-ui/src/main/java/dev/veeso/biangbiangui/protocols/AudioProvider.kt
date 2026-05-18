package dev.veeso.biangbiangui.protocols

/**
 * Plays/streams audio for transliteration results.
 * Mirrors iOS `AudioProvider`.
 */
interface AudioProvider {
    /** Speak/stream the given text/result. [languageCode] is the variant's BCP-47 code. */
    fun play(text: String, languageCode: String?)

    fun stop()

    val isPlaying: Boolean
}
