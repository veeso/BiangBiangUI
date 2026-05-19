package dev.veeso.biangbiangui.config

import dev.veeso.biangbiangui.protocols.FeaturePlugin

/**
 * Root configuration object that drives the shared UI.
 * Mirrors iOS `BiangBiangConfig`.
 *
 * `strings` is constructed by merging caller-supplied overrides over the
 * English defaults, exactly as iOS `UIStrings.merged(with:)`.
 */
class BiangBiangConfig(
    val branding: Branding,
    val languages: List<LanguageProfile>,
    val extraSettings: List<SettingDescriptor>,
    val plugins: List<FeaturePlugin>,
    val features: FeatureFlags,
    strings: Map<String, String>?,
    /**
     * Lower clamp for the OCR overlay transliteration font scale ratio
     * (`OcrOverlay`). Defaults to 0.6; apps may raise/lower it. Mirrors iOS
     * `BiangBiangConfig.minimumOcrScaleFactor`.
     */
    val minimumOcrScaleFactor: Float = 0.6f,
) {
    val strings: UiStrings = UiStrings.merged(strings)
}
