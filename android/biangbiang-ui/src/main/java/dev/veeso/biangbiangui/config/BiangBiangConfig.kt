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
) {
    val strings: UiStrings = UiStrings.merged(strings)
}
