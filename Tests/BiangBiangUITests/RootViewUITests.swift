//
//  RootViewUITests.swift
//  BiangBiangUITests
//
//  iOS root-view smoke test (Task 4.3). Proves the library's public entry
//  point `BiangBiangRootView` instantiates and renders for BOTH example
//  configs without crashing — exercising the whole wiring (context
//  injection, tab composition, plugin tabs).
//
//  Tab-contract note: SwiftUI exposes no public API to count `TabView`
//  tabs from a hosted view, and the plan forbids ViewInspector / SwiftUI
//  internal reflection. So the tab contract is asserted on the SAME public
//  config inputs the view's body uses to build its tabs:
//    base tabs (Text, Camera, Settings)
//      + History iff config.features.history
//      + config.plugins.flatMap(\.tabs).count
//  This tests the config-driven contract (Arabic = Chinese + the Quran
//  plugin tab) without private view introspection, alongside the genuine
//  host-and-layout crash-free assertion.
//

#if canImport(UIKit)
    import ArabicExample
    @testable import BiangBiangUI
    import ChineseExample
    import SwiftUI
    import Testing
    import UIKit

    // MARK: - Helpers

    /// Number of fixed, always-present tabs in `BiangBiangRootView`'s body:
    /// Text, Camera, Settings.
    private let baseTabCount = 3

    /// Expected tab count derived from the public config inputs the view's
    /// body uses (base + History iff `features.history` + plugin tabs).
    @MainActor
    private func expectedTabCount(for config: BiangBiangConfig) -> Int {
        baseTabCount
            + (config.features.history ? 1 : 0)
            + config.plugins.flatMap(\.tabs).count
    }

    /// Hosts `BiangBiangRootView(config:)` in a `UIHostingController`, forces
    /// a layout pass, and returns without crashing — exercising the real
    /// dependency graph + tab composition.
    @MainActor
    private func hostAndLayout(_ config: BiangBiangConfig) {
        let host = UIHostingController(rootView: BiangBiangRootView(config: config))
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
    }

    // MARK: - Crash-free hosting (both configs)

    @MainActor
    @Test func chineseRootViewHostsWithoutCrashing() {
        hostAndLayout(ChineseConfig.chineseConfig)
    }

    @MainActor
    @Test func arabicRootViewHostsWithoutCrashing() {
        hostAndLayout(ArabicConfig.arabicConfig)
    }

    // MARK: - Config-driven tab contract

    @MainActor
    @Test func chineseConfigHasHistoryAndNoPluginTabs() {
        let cfg = ChineseConfig.chineseConfig
        #expect(cfg.features.history)
        #expect(cfg.plugins.flatMap(\.tabs).isEmpty)
        // Text, Camera, History, Settings — no plugin tabs.
        #expect(expectedTabCount(for: cfg) == 4)
    }

    @MainActor
    @Test func arabicConfigHasHistoryAndExactlyOneQuranPluginTab() {
        let cfg = ArabicConfig.arabicConfig
        #expect(cfg.features.history)
        let pluginTabs = cfg.plugins.flatMap(\.tabs)
        #expect(pluginTabs.count == 1)
        #expect(pluginTabs.first?.id == "quran")
        // Text, Camera, History, Settings, Quran.
        #expect(expectedTabCount(for: cfg) == 5)
    }

    @MainActor
    @Test func arabicHasExactlyOneMoreTabThanChinese() {
        let chinese = ChineseConfig.chineseConfig
        let arabic = ArabicConfig.arabicConfig
        // Both enable History; the only difference is the Quran plugin tab.
        #expect(chinese.features.history == arabic.features.history)
        #expect(
            expectedTabCount(for: arabic) - expectedTabCount(for: chinese) == 1
        )
        #expect(
            arabic.plugins.flatMap(\.tabs).count
                - chinese.plugins.flatMap(\.tabs).count == 1
        )
    }
#endif
