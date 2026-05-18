package dev.veeso.biangbiangui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.veeso.biangbiangui.config.BiangBiangConfig
import dev.veeso.biangbiangui.config.LanguageProfile
import dev.veeso.biangbiangui.config.LanguageVariant
import dev.veeso.biangbiangui.protocols.AudioProvider
import dev.veeso.biangbiangui.protocols.Transliterator
import dev.veeso.biangbiangui.services.SettingsStore
import dev.veeso.biangbiangui.services.TextProcessingEngine

/**
 * App-wide context exposed to every library screen.
 *
 * Mirrors iOS `BiangBiangContext` (`@Observable` env object: `config`,
 * `settings`, `engine` rebuildable via `rebuildEngine()`, `audio`,
 * `activeProfile`, `activeVariant`, `variant(forId:)`).
 *
 * ## Android divergence vs. iOS
 *
 * iOS holds `engine` as an `@Observable` mutable property and calls
 * `rebuildEngine()` imperatively after `selectedVariantId` changes. On Android
 * `SettingsStore` is Flow-based, so the equivalent surface is reactive: the
 * holder collects `settings.selectedVariantId` and derives `activeVariant` and
 * `engine` from it. Reading `engine`/`activeVariant` inside Compose
 * recomposes when the selected variant changes — there is no imperative
 * `rebuildEngine()` to call, the rebuild is driven by the Flow (the documented
 * iOS `rebuildEngine()` contract, expressed idiomatically).
 *
 * Ownership: [BiangBiangRoot] builds one holder and provides it via
 * [LocalBiangBiangContext]. Screens only read it.
 */
class BiangBiangContext(
    val config: BiangBiangConfig,
    val settings: SettingsStore,
    /** Resolved audio provider (plugin override or system TTS). */
    val audio: AudioProvider,
    /** Reactive selected-variant id, collected from [SettingsStore]. */
    private val selectedVariantId: State<String>,
) {
    /** Pass-through transliterator used when no active variant supplies one. */
    private val identityTransliterator = Transliterator { it }

    /**
     * The first language profile in the config. Apps that support a single
     * script always have exactly one profile; this is its canonical accessor.
     * Mirrors iOS `activeProfile`.
     */
    val activeProfile: LanguageProfile
        get() {
            check(config.languages.isNotEmpty()) {
                "BiangBiangConfig must declare at least one LanguageProfile"
            }
            return config.languages[0]
        }

    /**
     * The variant whose `id` matches `settings.selectedVariantId`, falling back
     * to the first variant of the active profile. Mirrors iOS `activeVariant`.
     * Backed by [derivedStateOf] so reads recompose on variant change.
     */
    val activeVariant: LanguageVariant?
        get() = activeVariantState.value

    private val activeVariantState: State<LanguageVariant?> = derivedStateOf {
        val id = selectedVariantId.value
        activeProfile.variants.firstOrNull { it.id == id }
            ?: activeProfile.variants.firstOrNull()
    }

    /**
     * The active [TextProcessingEngine], derived from the active profile's
     * `scriptRanges` and the active variant's `transliterator`. Recomputed
     * whenever the selected variant changes — this is the Android equivalent of
     * iOS `rebuildEngine()`.
     */
    val engine: TextProcessingEngine
        get() = engineState.value

    private val engineState: State<TextProcessingEngine> = derivedStateOf {
        TextProcessingEngine(
            scriptRanges = activeProfile.scriptRanges,
            transliterator = activeVariantState.value?.transliterator
                ?: identityTransliterator,
        )
    }

    /**
     * Searches every profile's variant list for [id]. Used by History and TTS
     * screens that need to resolve a stored `variantId` back to a variant.
     * Mirrors iOS `variant(forId:)`.
     */
    fun variant(forId: String): LanguageVariant? =
        config.languages.asSequence()
            .flatMap { it.variants.asSequence() }
            .firstOrNull { it.id == forId }
}

/**
 * The injected [BiangBiangContext]. [BiangBiangRoot] provides it; every screen
 * resolves it with `LocalBiangBiangContext.current`. Mirrors iOS
 * `@Environment(BiangBiangContext.self)`.
 */
val LocalBiangBiangContext = staticCompositionLocalOf<BiangBiangContext> {
    error("BiangBiangContext not provided. Wrap content in BiangBiangRoot.")
}

/**
 * Builds and remembers a [BiangBiangContext], collecting the reactive
 * `selectedVariantId` from [settings]. Called once from [BiangBiangRoot].
 */
@Composable
internal fun rememberBiangBiangContext(
    config: BiangBiangConfig,
    settings: SettingsStore,
    audio: AudioProvider,
): BiangBiangContext {
    val variantId = settings.selectedVariantId
        .collectAsStateWithLifecycle(
            initialValue = config.languages.firstOrNull()
                ?.variants?.firstOrNull()?.id ?: "",
        )
    return remember(config, settings, audio) {
        BiangBiangContext(config, settings, audio, variantId)
    }
}
