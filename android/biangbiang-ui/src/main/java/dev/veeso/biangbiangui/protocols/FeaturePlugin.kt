package dev.veeso.biangbiangui.protocols

import androidx.compose.runtime.Composable

/**
 * A custom tab contributed by a [FeaturePlugin].
 * The iOS `PluginTab.content` is a SwiftUI `AnyView`; the Android equivalent
 * is a Jetpack Compose content slot. Mirrors iOS `PluginTab`.
 */
data class PluginTab(
    val id: String,
    val title: String,
    /** Material icon name (mirrors iOS `systemImage`). */
    val systemImage: String,
    val content: @Composable () -> Unit,
)

/**
 * Injection slot for app-only features (e.g. Harakat's Quran). The library
 * invokes these hooks and never references plugin-specific types.
 * Mirrors iOS `FeaturePlugin` including its default-method behaviour.
 */
interface FeaturePlugin {
    val tabs: List<PluginTab> get() = emptyList()

    fun onProcessedText(result: ProcessedText) {}

    /** Replace the plain transliteration output when the plugin has a hit. */
    fun inlineResultView(result: ProcessedText): (@Composable () -> Unit)? = null

    val audioProvider: AudioProvider? get() = null
}
