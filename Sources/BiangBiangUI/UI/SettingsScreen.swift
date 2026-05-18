//
//  SettingsScreen.swift
//  BiangBiangUI
//
//  Config-driven port of SettingsView from BiangBiang Hanzi, restructured
//  into the three spec layers:
//    1. Shared core — translation-target language picker (disabled as data
//       when the active variant `translatable == false`), History "Clear all",
//       and bug-report (GitHub issue URL + support email).
//    2. Data-driven variant segmented picker — rendered only when the active
//       profile has more than one variant; binds `settings.selectedVariantId`
//       and rebuilds the engine on change (the documented `rebuildEngine()`
//       contract — see BiangBiangContext).
//    3. Injectable descriptors — one toggle/picker per `config.extraSettings`,
//       bound to `settings.value(for:)` / `setValue(_:for:)`; footer from the
//       descriptor itself.
//  Generalisation notes vs. the reference:
//    • No hard-coded Chinese/Cantonese checks: the disabled-translation state
//      is driven by `activeVariant.translatable`; the variant list is data.
//    • User-facing strings come from ctx.config.strings everywhere except two
//      literals inherited verbatim from the reference + sibling precedent: the
//      confirmation-dialog "Cancel" button (identical to HistoryScreen) and the
//      translation-disabled footer (the reference SettingsView hard-coded it).
//      The GitHub repo and support email come from ctx.config.branding. The
//      DEBUG-only review-reset block is kept verbatim from the reference
//      (debug-only, developer-facing text — never shipped to end users).
//

#if canImport(UIKit)
    import SwiftUI

    // MARK: - SettingsScreen

    /// The settings screen: shared core controls, an optional data-driven variant
    /// picker, and injectable app-specific descriptors.
    ///
    /// Reads all configuration from `BiangBiangContext` in the SwiftUI environment.
    /// Inject it from the consuming app's root view:
    ///
    ///     ContentView()
    ///         .environment(context)
    @MainActor
    public struct SettingsScreen: View {
        @Environment(BiangBiangContext.self) private var ctx
        @Environment(\.openURL) private var openURL

        // MARK: - Private state

        @State private var showClearConfirm = false

        #if DEBUG
            @State private var showResetToast = false
        #endif

        // MARK: - Derived data

        /// Locale identifiers offered as translation targets, name-sorted.
        /// Mirrors the reference's `availableLanguages`; the selection binds the
        /// same `userLanguage` source the ported TextScreen translation reads.
        private static let availableLanguages: [(id: String, name: String)] =
            Locale.availableIdentifiers
                .map { id in
                    (
                        id: id,
                        name: Locale.current.localizedString(forIdentifier: id) ?? id
                    )
                }
                .sorted { $0.name < $1.name }

        // MARK: - Init

        public init() {}

        // MARK: - Body

        public var body: some View {
            @Bindable var settings = ctx.settings

            NavigationStack {
                Form {
                    sharedCoreSections(userLanguage: $settings.userLanguage)
                    variantSection(selectedVariantId: $settings.selectedVariantId)
                    descriptorSections()

                    #if DEBUG
                        debugSection
                    #endif
                }
                .navigationTitle(ctx.config.strings.tabSettings)
                .navigationBarTitleDisplayMode(.inline)
                #if DEBUG
                    .overlay(alignment: .bottom) {
                        if showResetToast {
                            CopyToast(message: "Review prompt reset")
                                .padding(.bottom, 24)
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                                .task {
                                    try? await Task.sleep(for: .seconds(2))
                                    showResetToast = false
                                }
                        }
                    }
                    .animation(.easeInOut, value: showResetToast)
                #endif
            }
        }

        // MARK: - Layer 1: shared core

        @ViewBuilder
        private func sharedCoreSections(userLanguage: Binding<String>) -> some View {
            let translatable = ctx.activeVariant?.translatable ?? true

            Section {
                Picker(
                    ctx.config.strings.translationLanguage,
                    selection: userLanguage
                ) {
                    ForEach(Self.availableLanguages, id: \.id) { option in
                        Text(option.name).tag(option.id)
                    }
                }
                .pickerStyle(.menu)
                .disabled(!translatable)
            } header: {
                Label(ctx.config.strings.translationLanguage, systemImage: "globe")
            } footer: {
                if !translatable {
                    Text("Translation is unavailable for the selected variant.")
                }
            }

            Section {
                Button(ctx.config.strings.clearAll, role: .destructive) {
                    showClearConfirm = true
                }
                .disabled(ctx.settings.history.isEmpty)
            } header: {
                Label(ctx.config.strings.tabHistory, systemImage: "clock")
            }
            .confirmationDialog(
                ctx.config.strings.clearAllConfirm,
                isPresented: $showClearConfirm,
                titleVisibility: .visible
            ) {
                Button(ctx.config.strings.clearAll, role: .destructive) {
                    ctx.settings.clearHistory()
                }
                Button("Cancel", role: .cancel) {}
            }

            Section {
                Button(
                    ctx.config.strings.openGithubIssues,
                    systemImage: "link",
                    action: openGitHubIssues
                )
                Button(
                    ctx.config.strings.sendEmail,
                    systemImage: "envelope",
                    action: sendBugEmail
                )
            } header: {
                Label(ctx.config.strings.reportBug, systemImage: "ladybug")
            }
        }

        // MARK: - Layer 2: data-driven variant picker

        @ViewBuilder
        private func variantSection(selectedVariantId: Binding<String>) -> some View {
            let variants = ctx.activeProfile.variants
            if variants.count > 1 {
                Section {
                    Picker(
                        ctx.activeProfile.displayName,
                        selection: selectedVariantId
                    ) {
                        ForEach(variants, id: \.id) { variant in
                            Text(variant.displayName).tag(variant.id)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Label(ctx.activeProfile.displayName, systemImage: "textformat")
                }
                .onChange(of: ctx.settings.selectedVariantId) { _, _ in
                    ctx.rebuildEngine()
                }
            }
        }

        // MARK: - Layer 3: injectable descriptors

        private func descriptorSections() -> some View {
            ForEach(ctx.config.extraSettings) { descriptor in
                Section {
                    descriptorControl(descriptor)
                } footer: {
                    if let footer = descriptor.footer {
                        Text(footer)
                    }
                }
            }
        }

        @ViewBuilder
        private func descriptorControl(_ descriptor: SettingDescriptor) -> some View {
            switch descriptor.kind {
            case .toggle:
                Toggle(
                    descriptor.label,
                    isOn: toggleBinding(for: descriptor)
                )
            case let .picker(options):
                Picker(
                    descriptor.label,
                    selection: pickerBinding(for: descriptor)
                ) {
                    ForEach(options, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
            }
        }

        private func toggleBinding(for descriptor: SettingDescriptor) -> Binding<Bool> {
            Binding(
                get: {
                    (ctx.settings.value(for: descriptor.key) ?? descriptor.defaultValue) == "true"
                },
                set: { newValue in
                    ctx.settings.setValue(newValue ? "true" : "false", for: descriptor.key)
                }
            )
        }

        private func pickerBinding(for descriptor: SettingDescriptor) -> Binding<String> {
            Binding(
                get: {
                    ctx.settings.value(for: descriptor.key) ?? descriptor.defaultValue
                },
                set: { newValue in
                    ctx.settings.setValue(newValue, for: descriptor.key)
                }
            )
        }

        // MARK: - DEBUG section

        #if DEBUG
            private var debugSection: some View {
                Section {
                    Button("Reset review prompt", systemImage: "arrow.counterclockwise") {
                        ctx.settings.reviewPromptDismissed = false
                        ctx.settings.reviewLaunchCount = 0
                        showResetToast = true
                    }
                } header: {
                    Label("Debug", systemImage: "hammer")
                } footer: {
                    Text("Clears dismissed flag and launch count. Restart the app to re-trigger after 3 launches.")
                }
            }
        #endif

        // MARK: - Bug-report actions

        private func openGitHubIssues() {
            let repo = ctx.config.branding.githubRepo
            guard let url = URL(string: "https://github.com/\(repo)/issues/new") else {
                return
            }
            openURL(url)
        }

        private func sendBugEmail() {
            let subject = "[iOS] Bug report – \(ctx.config.branding.appName)"
            let body = """
            Description:

            Step to reproduce:

            Device:
            iOS version:
            """
            .replacingOccurrences(of: "\n", with: "\r\n")

            guard
                let encodedSubject = subject
                .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                let encodedBody = body
                .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                let url = URL(
                    string: "mailto:\(ctx.config.branding.supportEmail)"
                        + "?subject=\(encodedSubject)&body=\(encodedBody)"
                )
            else { return }
            openURL(url)
        }
    }
#endif
