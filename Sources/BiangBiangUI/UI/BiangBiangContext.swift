//
//  BiangBiangContext.swift
//  BiangBiangUI
//
//  App-wide context injected into the SwiftUI environment. Mirrors the
//  pattern in the reference app's `@EnvironmentObject AppSettings` +
//  `@Environment(AudioPlayerService.self)`, unified into a single object.
//

import Observation
import SwiftUI

// MARK: - Identity transliterator (fallback when no variant is active)

/// Pass-through transliterator used when no active variant supplies one.
/// Returns its input unchanged so the engine still produces output.
private struct IdentityTransliterator: Transliterator {
    func transliterate(_ scriptSpan: String) -> String {
        scriptSpan
    }
}

// MARK: - BiangBiangContext

/// Shared context that every library screen reads from `@Environment`.
///
/// Ownership: the consuming app creates one `BiangBiangContext` in its root
/// view and injects it with `.environment(context)`.  Screens never own or
/// create it; they only read it.
@MainActor
@Observable
public final class BiangBiangContext {
    // MARK: - Public stored properties

    public let config: BiangBiangConfig
    public let settings: SettingsStore
    /// The resolved audio provider (plugin override or system TTS).
    /// Resolution happens in the root view that constructs this context.
    public let audio: any AudioProvider

    // MARK: - Observable engine

    /// Rebuilt whenever `selectedVariantId` changes via `rebuildEngine()`.
    public private(set) var engine: TextProcessingEngine

    // MARK: - Init

    /// Creates the context and builds an initial `TextProcessingEngine` from
    /// the active profile / variant at construction time.
    public init(config: BiangBiangConfig, settings: SettingsStore, audio: any AudioProvider) {
        self.config = config
        self.settings = settings
        self.audio = audio
        // Build engine inline; `activeProfile` / `activeVariant` are pure
        // computed properties so they are safe to call before `self` is fully
        // initialised (no stored-property cycles).
        let profile = config.languages.first
        let variantId = settings.selectedVariantId
        let variant = profile?.variants.first(where: { $0.id == variantId })
            ?? profile?.variants.first
        let transliterator: any Transliterator = variant?.transliterator ?? IdentityTransliterator()
        engine = TextProcessingEngine(
            scriptRanges: profile?.scriptRanges ?? [],
            transliterator: transliterator
        )
    }

    // MARK: - Derived helpers

    /// The first language profile in the config. Apps that support a single
    /// script always have exactly one profile; this is its canonical accessor.
    public var activeProfile: LanguageProfile {
        precondition(!config.languages.isEmpty,
                     "BiangBiangConfig must declare at least one LanguageProfile")
        return config.languages[0]
    }

    /// The variant whose `id` matches `settings.selectedVariantId`, falling
    /// back to the first variant of the active profile when there is no match
    /// (e.g. first launch before any selection has been persisted).
    public var activeVariant: LanguageVariant? {
        let id = settings.selectedVariantId
        return activeProfile.variants.first(where: { $0.id == id })
            ?? activeProfile.variants.first
    }

    /// Rebuilds `engine` from the active profile's `scriptRanges` and the
    /// active variant's `transliterator`. Call this after `selectedVariantId`
    /// changes (e.g. inside an `onChange(of:)` modifier on the root view).
    public func rebuildEngine() {
        let transliterator: any Transliterator =
            activeVariant?.transliterator ?? IdentityTransliterator()
        engine = TextProcessingEngine(
            scriptRanges: activeProfile.scriptRanges,
            transliterator: transliterator
        )
    }

    /// Searches every profile's variant list for `id`. Used by History and
    /// TTS screens that need to resolve a stored `variantId` string back to
    /// a `LanguageVariant`.
    public func variant(forId id: String) -> LanguageVariant? {
        config.languages.lazy
            .flatMap(\.variants)
            .first(where: { $0.id == id })
    }
}
