//
//  QuranPlugin.swift
//  ArabicExample
//
//  A `FeaturePlugin` proving the library's plugin slot end to end:
//  - `tabs`: a Quran browser tab.
//  - `onProcessedText`: runs `QuranMatcher.match` asynchronously and caches
//    the hit so the next `inlineResultView` call can render it.
//  - `inlineResultView(for:)`: returns `AnyView(QuranMatchView(...))` on a hit.
//  - `audioProvider`: an `EveryAyahAudioProvider`.
//
//  Concurrency design: `FeaturePlugin` is `@MainActor`, so this final class
//  is `@MainActor`-isolated. The cache (`lastMatch` / `lastOriginal` /
//  `lastSurahName`) is therefore touched only on the main actor — no locks,
//  no data races. `onProcessedText` spawns a `Task` that does the heavy work
//  on the `QuranDataset` / `QuranMatcher` actors (off the main actor) and
//  hops back to the main actor only to write the cache. `inlineResultView`
//  is a pure synchronous read of that main-actor state.
//

import BiangBiangUI
import SwiftUI

@MainActor
public final class QuranPlugin: FeaturePlugin {
    private let dataset = QuranDataset.shared
    private let matcher: QuranMatcher
    private let everyAyah = EveryAyahAudioProvider()

    // Main-actor-isolated cache populated by `onProcessedText`.
    private var lastOriginal: String?
    private var lastMatch: QuranMatch?
    private var lastSurahName: SurahName?

    public init() {
        matcher = QuranMatcher(dataset: QuranDataset.shared)
    }

    // MARK: - tabs

    public var tabs: [PluginTab] {
        [
            PluginTab(
                id: "quran",
                title: "Quran",
                systemImage: "book.closed.fill",
                content: AnyView(QuranBrowserView())
            ),
        ]
    }

    // MARK: - onProcessedText

    public func onProcessedText(_ result: ProcessedText) {
        let query = result.original
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            lastOriginal = nil
            lastMatch = nil
            lastSurahName = nil
            return
        }
        Task { [dataset, matcher] in
            await dataset.loadIfNeeded()
            let match = await matcher.match(query)
            let surahName: SurahName? = if let match {
                await dataset.surahNames[match.ayah.surah]
            } else {
                nil
            }
            // Back on the main actor (this Task inherits @MainActor isolation
            // from the enclosing @MainActor class): write the cache.
            self.lastOriginal = query
            self.lastMatch = match
            self.lastSurahName = surahName
        }
    }

    // MARK: - inlineResultView

    public func inlineResultView(for result: ProcessedText) -> AnyView? {
        guard result.original == lastOriginal, let match = lastMatch else { return nil }
        return AnyView(
            QuranMatchView(match: match, surahName: lastSurahName, audio: everyAyah)
        )
    }

    // MARK: - audioProvider

    public var audioProvider: (any AudioProvider)? {
        everyAyah
    }
}
