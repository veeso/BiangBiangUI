//
//  BiangBiangRootView.swift
//  BiangBiangUI
//
//  The library's public entry point. Config-driven port of
//  BiangBiang_HanziApp + ContentView from BiangBiang Hanzi.
//
//  Responsibilities:
//    • Owns the dependency graph: resolves the audio provider (plugin
//      override else SystemTTSAudioProvider), builds the SettingsStore
//      (seeded from the active profile's variants + extra descriptors),
//      constructs the BiangBiangContext and injects it via .environment so
//      every ported screen resolves it from @Environment.
//    • Calls settings.registerLaunch() once at init (preserves issue #20).
//    • Hosts the TabView: Text, Camera, History (only when
//      config.features.history), Settings, plus one tab per plugin
//      (config.plugins.tabs — the Quran-browser seam). Labels from
//      config.strings.
//    • Hosts the rate-prompt alert (only when config.features.ratePrompt),
//      driven by ReviewPromptPolicy.shouldShow, with the three reference
//      buttons wired to the SettingsStore review API (preserves issue #20
//      exactly): "Rate now" dismisses forever + opens the App Store
//      write-review URL, "Not now" resets the counter, "Don't ask again"
//      dismisses forever.
//  Generalisation notes vs. the reference:
//    • Tab set is config-driven (History + plugin tabs are conditional);
//      the reference hard-coded the four Hanzi tabs.
//    • All user-facing strings come from config.strings; the write-review
//      URL is built from config.branding.appStoreId.
//

#if canImport(UIKit)
    import SwiftUI

    // MARK: - BiangBiangRootView

    /// The library's root container. The consuming app makes this its
    /// `WindowGroup` content, passing its `BiangBiangConfig`:
    ///
    ///     @main
    ///     struct MyApp: App {
    ///         var body: some Scene {
    ///             WindowGroup {
    ///                 BiangBiangRootView(config: .myConfig)
    ///             }
    ///         }
    ///     }
    ///
    /// It owns the dependency graph (settings, engine, audio) via
    /// `BiangBiangContext` and injects it into the SwiftUI environment so
    /// every screen resolves it from `@Environment(BiangBiangContext.self)`.
    @MainActor
    public struct BiangBiangRootView: View {
        @Environment(\.openURL) private var openURL

        // MARK: - Owned state

        @State private var context: BiangBiangContext
        @State private var showReviewPrompt = false

        // MARK: - Init

        /// Builds the dependency graph from `config` and registers this
        /// process launch for the rate-prompt policy (preserves issue #20).
        public init(config: BiangBiangConfig) {
            let settings = SettingsStore(
                variants: config.languages.first?.variants ?? [],
                descriptors: config.extraSettings
            )
            settings.registerLaunch()

            let audio: any AudioProvider =
                config.plugins.lazy
                    .compactMap(\.audioProvider)
                    .first
                    ?? SystemTTSAudioProvider()

            _context = State(
                wrappedValue: BiangBiangContext(
                    config: config,
                    settings: settings,
                    audio: audio
                )
            )
        }

        // MARK: - Body

        public var body: some View {
            TabView {
                TextScreen()
                    .tabItem {
                        Label(
                            context.config.strings.tabText,
                            systemImage: "textformat"
                        )
                    }

                CameraScreen()
                    .tabItem {
                        Label(
                            context.config.strings.tabCamera,
                            systemImage: "camera"
                        )
                    }

                if context.config.features.history {
                    HistoryScreen()
                        .tabItem {
                            Label(
                                context.config.strings.tabHistory,
                                systemImage: "clock.fill"
                            )
                        }
                }

                SettingsScreen()
                    .tabItem {
                        Label(
                            context.config.strings.tabSettings,
                            systemImage: "gear"
                        )
                    }

                ForEach(pluginTabs) { pluginTab in
                    pluginTab.content
                        .tabItem {
                            Label(
                                pluginTab.title,
                                systemImage: pluginTab.systemImage
                            )
                        }
                }
            }
            .tint(context.config.branding.accentColor)
            .environment(context)
            .task {
                guard context.config.features.ratePrompt else { return }
                showReviewPrompt = ReviewPromptPolicy.shouldShow(
                    launchCount: context.settings.reviewLaunchCount,
                    dismissed: context.settings.reviewPromptDismissed
                )
            }
            .alert(
                context.config.strings.rateTitle,
                isPresented: $showReviewPrompt
            ) {
                Button(context.config.strings.rateNow) {
                    context.settings.dismissForever()
                    if let url = writeReviewURL {
                        openURL(url)
                    }
                }
                Button(context.config.strings.notNow, role: .cancel) {
                    context.settings.notNow()
                }
                Button(
                    context.config.strings.dontAskAgain,
                    role: .destructive
                ) {
                    context.settings.dismissForever()
                }
            } message: {
                Text(context.config.strings.rateMessage)
            }
        }

        // MARK: - Derived data

        /// Every plugin's tabs, flattened in declaration order. This is the
        /// Quran-browser seam: the library never references plugin types.
        private var pluginTabs: [PluginTab] {
            context.config.plugins.flatMap(\.tabs)
        }

        /// App Store write-review deep link, built from
        /// `config.branding.appStoreId`.
        private var writeReviewURL: URL? {
            URL(
                string: "https://apps.apple.com/app/id"
                    + "\(context.config.branding.appStoreId)?action=write-review"
            )
        }
    }
#endif
