//
//  HistoryScreen.swift
//  BiangBiangUI
//
//  Config-driven port of HistoryView from BiangBiang Hanzi.
//  Generalisation notes vs. the reference:
//    • All user-facing strings come from ctx.config.strings (original,
//      transliterated, clearAll, clearAllConfirm, historyEmpty, listen, stop).
//    • Per-row TTS language: resolved via ctx.variant(forId: entry.variantId)
//      and its ttsLanguageCode. When ttsLanguageCode == nil the TTS button is
//      hidden (data-driven; reference always had a language for Chinese).
//    • No HistoryVariant enum — variant lookup replaces entry.variant switch.
//    • audio.isPlaying replaces audio.isSpeaking (AudioProvider protocol API).
//

#if canImport(UIKit)
    import SwiftUI

    // MARK: - HistoryScreen

    /// Displays saved history entries newest-first with a segmented
    /// Original / Transliterated toggle, per-row tap-to-expand, TTS, and
    /// swipe-to-delete / Clear All with confirmation dialog.
    ///
    /// Reads all configuration from `BiangBiangContext` in the SwiftUI environment.
    /// Inject it from the consuming app's root view:
    ///
    ///     ContentView()
    ///         .environment(context)
    @MainActor
    public struct HistoryScreen: View {
        @Environment(BiangBiangContext.self) private var ctx

        // MARK: - Private state

        @State private var showTransliterated = false
        @State private var expandedIDs: Set<UUID> = []
        @State private var showClearConfirm = false
        @State private var speakingID: UUID?

        // MARK: - Init

        public init() {}

        // MARK: - Body

        public var body: some View {
            NavigationStack {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        Picker("Display", selection: $showTransliterated) {
                            Text(ctx.config.strings.original).tag(false)
                            Text(ctx.config.strings.transliterated).tag(true)
                        }
                        .pickerStyle(.segmented)

                        Button(ctx.config.strings.clearAll, role: .destructive) {
                            showClearConfirm = true
                        }
                        .buttonStyle(.bordered)
                        .disabled(ctx.settings.history.isEmpty)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)

                    if ctx.settings.history.isEmpty {
                        Spacer()
                        ContentUnavailableView(
                            ctx.config.strings.historyEmpty,
                            systemImage: "clock"
                        )
                        Spacer()
                    } else {
                        List {
                            ForEach(ctx.settings.history) { entry in
                                row(entry)
                            }
                            .onDelete { offsets in
                                let ids = offsets.map { ctx.settings.history[$0].id }
                                for id in ids {
                                    ctx.settings.deleteHistory(id: id)
                                }
                            }
                        }
                    }
                }
                .navigationTitle(ctx.config.strings.tabHistory)
                .onChange(of: ctx.audio.isPlaying) { _, playing in
                    if !playing { speakingID = nil }
                }
                .confirmationDialog(
                    ctx.config.strings.clearAllConfirm,
                    isPresented: $showClearConfirm,
                    titleVisibility: .visible
                ) {
                    Button(ctx.config.strings.clearAll, role: .destructive) {
                        ctx.settings.clearHistory()
                        expandedIDs.removeAll()
                    }
                    Button("Cancel", role: .cancel) {}
                }
            }
        }

        // MARK: - Row

        @ViewBuilder
        private func row(_ entry: HistoryEntry) -> some View {
            let text = showTransliterated
                ? entry.transliteration
                : entry.original
            let expanded = expandedIDs.contains(entry.id)
            let isThisPlaying = speakingID == entry.id && ctx.audio.isPlaying
            let variant = ctx.variant(forId: entry.variantId)

            HStack(alignment: .top, spacing: 12) {
                Text(text)
                    .lineLimit(expanded ? nil : 1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if expanded {
                            expandedIDs.remove(entry.id)
                        } else {
                            expandedIDs.insert(entry.id)
                        }
                    }

                if variant?.ttsLanguageCode != nil {
                    Button {
                        if isThisPlaying {
                            ctx.audio.stop()
                            speakingID = nil
                        } else {
                            speakingID = entry.id
                            ctx.audio.play(
                                text: entry.original,
                                languageCode: variant?.ttsLanguageCode
                            )
                        }
                    } label: {
                        Image(
                            systemName: isThisPlaying
                                ? "stop.circle.fill"
                                : "speaker.wave.2.fill"
                        )
                        .accessibilityLabel(
                            isThisPlaying
                                ? ctx.config.strings.stop
                                : ctx.config.strings.listen
                        )
                    }
                    .buttonStyle(.borderless)
                }
            }
            .padding(.vertical, 4)
        }
    }
#endif
