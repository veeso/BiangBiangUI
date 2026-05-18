package dev.veeso.biangbiangui.config

/** Toggles for optional library features. Mirrors iOS `FeatureFlags`. */
data class FeatureFlags(
    val history: Boolean = true,
    val ratePrompt: Boolean = true,
    val tts: Boolean = true,
)
