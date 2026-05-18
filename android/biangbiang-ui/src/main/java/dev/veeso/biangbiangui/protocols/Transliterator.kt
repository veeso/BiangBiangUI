package dev.veeso.biangbiangui.protocols

/**
 * Converts an already-isolated run of script characters to Latin.
 * The library's text-processing engine owns span detection, passthrough and
 * spacing; the app implementation only romanises one span. Must be pure.
 * Mirrors iOS `Transliterator`.
 */
fun interface Transliterator {
    fun transliterate(scriptSpan: String): String
}
