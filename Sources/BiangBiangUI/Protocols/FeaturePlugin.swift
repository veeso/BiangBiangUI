import SwiftUI

public struct PluginTab: Identifiable {
    public let id: String
    public let title: String
    public let systemImage: String
    public let content: AnyView
    public init(id: String, title: String, systemImage: String, content: AnyView) {
        self.id = id; self.title = title
        self.systemImage = systemImage; self.content = content
    }
}

/// Injection slot for app-only features (e.g. Harakat's Quran). The library invokes
/// these hooks and never references plugin-specific types.
@MainActor
public protocol FeaturePlugin {
    var tabs: [PluginTab] { get }
    func onProcessedText(_ result: ProcessedText)
    /// Replace the plain transliteration output when the plugin has a hit.
    func inlineResultView(for result: ProcessedText) -> AnyView?
    var audioProvider: (any AudioProvider)? { get }
}

public extension FeaturePlugin {
    var tabs: [PluginTab] {
        []
    }

    func onProcessedText(_: ProcessedText) {}
    func inlineResultView(for _: ProcessedText) -> AnyView? {
        nil
    }

    var audioProvider: (any AudioProvider)? {
        nil
    }
}
