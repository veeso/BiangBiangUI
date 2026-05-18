package dev.veeso.biangbiangui.config

import dev.veeso.biangbiangui.protocols.Transliterator

/**
 * A single transliteration variant of a [LanguageProfile].
 * Mirrors iOS `LanguageVariant`.
 */
data class LanguageVariant(
    val id: String,
    val displayName: String,
    val transliterator: Transliterator,
    /** BCP-47 code for TTS; `null` hides the Listen control for this variant. */
    val ttsLanguageCode: String?,
    /** `false` hides the translation UI as data — no conditional code path. */
    val translatable: Boolean,
)
