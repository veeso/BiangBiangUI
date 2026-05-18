//
//  TextScreen.swift
//  BiangBiangUI
//
//  Config-driven port of TextModeView from BiangBiang Hanzi.
//  Generalisation notes vs. the reference:
//    • Logo: Image(ctx.config.branding.logoAssetName) — asset lives in the
//      consuming app's bundle; referenced by name, no bundle parameter needed.
//    • All user-facing strings come from ctx.config.strings.
//    • Processing: ctx.engine.process(inputText) — engine already encapsulates
//      the active variant's transliterator; no mode enum needed here.
//    • TTS button: shown only when ctx.activeVariant?.ttsLanguageCode != nil.
//    • Translation section: shown only when ctx.activeVariant?.translatable == true
//      (data-driven; no hard-coded Chinese / Cantonese check). The Translation
//      framework (iOS 18+) is used via a dedicated sub-view gated with
//      @available(iOS 18, *). On iOS 17 the translate button is not shown.
//      Source language is derived from ctx.activeVariant?.ttsLanguageCode because
//      that is the BCP-47 code for the variant's natural language. If a translatable
//      variant has a nil ttsLanguageCode, the Translation framework receives an empty
//      Locale.Language — the translate button simply won't produce output.
//    • Plugin hook: after producing outputText for non-empty input, a ProcessedText
//      is built and each plugin's onProcessedText(_:) is called. The first plugin
//      that returns a non-nil inlineResultView(for:) replaces the plain output box.
//    • Save: uses ctx.settings.addHistory with variantId.
//    • Debounce: 800 ms, identical to reference.
//

#if canImport(UIKit)
    import SwiftUI
    #if canImport(Translation)
        import Translation
    #endif

    // MARK: - TextScreen

    /// The primary text-input / transliteration screen.
    ///
    /// Reads all configuration from `BiangBiangContext` in the SwiftUI environment.
    /// Inject it from the consuming app's root view:
    ///
    ///     ContentView()
    ///         .environment(context)
    public struct TextScreen: View {
        @Environment(BiangBiangContext.self) private var ctx

        // MARK: - Private state

        @State private var inputText: String = ""
        @State private var outputText: String = ""
        @State private var debounceTask: Task<Void, Never>?
        @State private var showSavedToast = false
        @State private var savedToastTask: Task<Void, Never>?
        @FocusState private var inputFocused: Bool

        // MARK: - Init

        public init() {}

        // MARK: - Body

        public var body: some View {
            ScrollView {
                logoHeader

                VStack(alignment: .leading, spacing: AppDesign.sectionSpacing) {
                    inputSection
                    outputSection
                    if ctx.activeVariant?.translatable == true {
                        #if canImport(Translation)
                            if #available(iOS 18, *) {
                                TranslationSection(
                                    inputText: inputText,
                                    strings: ctx.config.strings,
                                    ttsLanguageCode: ctx.activeVariant?.ttsLanguageCode,
                                    userLanguage: ctx.settings.userLanguage
                                )
                            }
                        #endif
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: ctx.settings.selectedVariantId) { _, _ in
                processInput()
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { inputFocused = false }
                }
            }
            .overlay(alignment: .bottom) {
                toastOverlay
            }
            .sensoryFeedback(.success, trigger: showSavedToast)
        }

        // MARK: - Header

        private var logoHeader: some View {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Image(ctx.config.branding.logoAssetName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 48, height: 48)
                        .clipShape(.rect(cornerRadius: AppDesign.cornerRadius))
                        .accessibilityHidden(true)
                    Text(ctx.config.branding.appName)
                        .font(.title)
                        .bold()
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 16)

                // Optional tagline. Rendered only when the app supplies a
                // non-empty `appSubtitle`, so apps that don't set it get no
                // subtitle and no extra vertical space.
                if !ctx.config.strings.appSubtitle.isEmpty {
                    Text(ctx.config.strings.appSubtitle)
                        .font(.title2)
                        .padding(.vertical, 4)
                }
            }
        }

        // MARK: - Input section

        private var inputSection: some View {
            SectionView(
                title: ctx.config.strings.inputTitle,
                actionLabel: ctx.config.strings.paste,
                actionIcon: "doc.on.clipboard",
                action: pasteFromClipboard
            ) {
                TextField(
                    ctx.config.strings.inputTitle,
                    text: $inputText,
                    axis: .vertical
                )
                .focused($inputFocused)
                .font(.title2)
                .lineLimit(5 ... 10)
                .padding(8)
                .overlay {
                    RoundedRectangle(cornerRadius: AppDesign.cornerRadius)
                        .stroke(.secondary)
                }
                .onChange(of: inputText) { _, _ in
                    scheduleDebouncedProcessing()
                }

                HStack {
                    if ctx.activeVariant?.ttsLanguageCode != nil {
                        ttsButton
                    }
                    saveButton
                }
            }
            .padding(.horizontal, AppDesign.horizontalPadding)
        }

        // MARK: - Output section

        /// Renders either the first plugin inline view (if any) or the plain
        /// read-only transliteration box.
        private var outputSection: some View {
            SectionView(
                title: ctx.config.strings.outputTitle,
                actionLabel: ctx.config.strings.copy,
                actionIcon: "doc.on.doc",
                action: { copyToClipboard(outputText) }
            ) {
                pluginInlineView ?? AnyView(ReadOnlyTextBox(text: outputText, font: .title3))
            }
            .padding(.horizontal, AppDesign.horizontalPadding)
        }

        /// Returns the first plugin's inline result view for the current output,
        /// or `nil` when no plugin claims the result.
        private var pluginInlineView: AnyView? {
            guard !outputText.isEmpty else { return nil }
            let pt = ProcessedText(
                original: inputText,
                transliteration: outputText,
                variantId: ctx.activeVariant?.id ?? "",
                source: .text
            )
            return ctx.config.plugins.lazy
                .compactMap { $0.inlineResultView(for: pt) }
                .first
        }

        // MARK: - TTS button

        private var ttsButton: some View {
            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            let isPlaying = ctx.audio.isPlaying
            return Button {
                if isPlaying {
                    ctx.audio.stop()
                } else {
                    ctx.audio.play(
                        text: inputText,
                        languageCode: ctx.activeVariant?.ttsLanguageCode
                    )
                }
            } label: {
                Label(
                    isPlaying ? ctx.config.strings.stop : ctx.config.strings.listen,
                    systemImage: isPlaying ? "stop.circle.fill" : "speaker.wave.2.fill"
                )
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.capsule)
            .disabled(trimmed.isEmpty && !isPlaying)
            .accessibilityHint("Read the input text aloud")
        }

        // MARK: - Save button

        private var saveButton: some View {
            Button {
                ctx.settings.addHistory(
                    original: inputText,
                    transliteration: outputText,
                    variantId: ctx.activeVariant?.id ?? ""
                )
                savedToastTask?.cancel()
                withAnimation { showSavedToast = true }
                savedToastTask = Task {
                    try? await Task.sleep(for: .seconds(1.5))
                    guard !Task.isCancelled else { return }
                    withAnimation { showSavedToast = false }
                }
            } label: {
                Label(ctx.config.strings.save, systemImage: "bookmark.fill")
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.capsule)
            .disabled(outputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityHint("Save this entry to History")
        }

        // MARK: - Toast overlay

        @ViewBuilder
        private var toastOverlay: some View {
            if showSavedToast {
                Text(ctx.config.strings.savedToHistory)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(.ultraThinMaterial)
                    .clipShape(.rect(cornerRadius: AppDesign.cornerRadius))
                    .shadow(radius: 6)
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
        }

        // MARK: - Processing

        private func scheduleDebouncedProcessing() {
            debounceTask?.cancel()
            debounceTask = Task {
                try? await Task.sleep(for: .milliseconds(800))
                guard !Task.isCancelled else { return }
                processInput()
            }
        }

        private func processInput() {
            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else {
                outputText = ""
                return
            }
            outputText = ctx.engine.process(inputText) ?? ""

            // Notify plugins after output is produced.
            if !outputText.isEmpty {
                let pt = ProcessedText(
                    original: inputText,
                    transliteration: outputText,
                    variantId: ctx.activeVariant?.id ?? "",
                    source: .text
                )
                for plugin in ctx.config.plugins {
                    plugin.onProcessedText(pt)
                }
            }
        }

        // MARK: - Clipboard helpers

        private func pasteFromClipboard() {
            if let text = UIPasteboard.general.string {
                inputText = text
            }
        }

        private func copyToClipboard(_ text: String) {
            guard !text.isEmpty else { return }
            UIPasteboard.general.string = text
        }
    }

    // MARK: - TranslationSection (iOS 18+)

    #if canImport(Translation)
        /// Isolated sub-view that owns the iOS 18 `TranslationSession.Configuration` state.
        /// Keeping it in a separate struct avoids sprinkling `@available(iOS 18, *)` on every
        /// method of `TextScreen` that touches `TranslationSession`.
        @available(iOS 18, *)
        private struct TranslationSection: View {
            let inputText: String
            let strings: UIStrings
            let ttsLanguageCode: String?
            let userLanguage: String

            @State private var translatedText: String = ""
            @State private var translateConfig: TranslationSession.Configuration?

            var body: some View {
                VStack(alignment: .leading, spacing: AppDesign.stackSpacing) {
                    SectionView(
                        title: strings.translationTitle,
                        actionLabel: strings.copy,
                        actionIcon: "doc.on.doc",
                        action: copyTranslation
                    ) {
                        ReadOnlyTextBox(text: translatedText, font: .title3)
                    }

                    HStack {
                        Spacer()
                        Button(strings.translate, systemImage: "globe", action: triggerTranslation)
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                            .font(.headline)
                            // Source language is derived from the active variant's ttsLanguageCode,
                            // which is the BCP-47 tag for the variant's natural written language.
                            // If ttsLanguageCode is nil the Locale.Language is constructed from ""
                            // and the translation simply won't execute — acceptable per spec.
                            .translationTask(translateConfig) { session in
                                // Capture values before leaving the calling actor so the
                                // session — which translationTask delivers on its own
                                // (nonisolated) context — is not sent across boundaries.
                                nonisolated(unsafe) let captured = session
                                let text = inputText
                                let result: String
                                do {
                                    result = try await captured.translate(text).targetText
                                } catch {
                                    result = "Translation failed: \(error.localizedDescription)"
                                }
                                translatedText = result
                            }
                            .accessibilityHint("Translate the input text to your selected language")
                    }
                }
                .padding(.horizontal, AppDesign.horizontalPadding)
                .onChange(of: inputText) { _, _ in
                    // Clear stale translation whenever the source text changes.
                    translatedText = ""
                    translateConfig = nil
                }
            }

            private func triggerTranslation() {
                guard translateConfig == nil else {
                    translateConfig?.invalidate()
                    return
                }
                translateConfig = TranslationSession.Configuration(
                    source: Locale.Language(identifier: ttsLanguageCode ?? ""),
                    target: Locale.Language(identifier: userLanguage)
                )
            }

            private func copyTranslation() {
                guard !translatedText.isEmpty else { return }
                UIPasteboard.general.string = translatedText
            }
        }
    #endif // canImport(Translation)

    // MARK: - ReadOnlyTextBox (private)

    private struct ReadOnlyTextBox: View {
        let text: String
        let font: Font

        var body: some View {
            Text(text)
                .font(font)
                .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
                .padding(8)
                .textSelection(.enabled)
                .overlay {
                    RoundedRectangle(cornerRadius: AppDesign.cornerRadius)
                        .stroke(.secondary)
                }
        }
    }
#endif
