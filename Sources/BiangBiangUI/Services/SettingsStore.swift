import Foundation
import Observation

@MainActor
@Observable
public final class SettingsStore {
    public var userLanguage: String {
        didSet { userDefaults.set(userLanguage, forKey: "user_language") }
    }

    public var selectedVariantId: String {
        didSet { userDefaults.set(selectedVariantId, forKey: "selected_variant") }
    }

    public var history: [HistoryEntry] {
        didSet {
            if let data = try? JSONEncoder().encode(history) {
                userDefaults.set(data, forKey: "history")
            }
        }
    }

    public var reviewLaunchCount: Int {
        didSet { userDefaults.set(reviewLaunchCount, forKey: "review_launch_count") }
    }

    public var reviewPromptDismissed: Bool {
        didSet { userDefaults.set(reviewPromptDismissed, forKey: "review_prompt_dismissed") }
    }

    @ObservationIgnored
    private let userDefaults: UserDefaults

    @ObservationIgnored
    private let descriptors: [SettingDescriptor]

    public init(
        userDefaults: UserDefaults = .standard,
        variants: [LanguageVariant] = [],
        descriptors: [SettingDescriptor] = [],
        defaultLanguage: String = Locale.current.language.languageCode?.identifier ?? "en"
    ) {
        self.userDefaults = userDefaults
        self.descriptors = descriptors

        userLanguage = userDefaults.string(forKey: "user_language") ?? defaultLanguage
        selectedVariantId = userDefaults.string(forKey: "selected_variant") ?? variants.first?.id ?? ""

        if let data = userDefaults.data(forKey: "history"),
           let decoded = try? JSONDecoder().decode([HistoryEntry].self, from: data)
        {
            history = decoded
        } else {
            history = []
        }

        reviewLaunchCount = userDefaults.integer(forKey: "review_launch_count")
        reviewPromptDismissed = userDefaults.bool(forKey: "review_prompt_dismissed")

        // Seed descriptor defaults only if absent, so existing user values survive.
        for descriptor in descriptors {
            let udKey = "descriptor.\(descriptor.key)"
            if userDefaults.object(forKey: udKey) == nil {
                userDefaults.set(descriptor.defaultValue, forKey: udKey)
            }
        }
    }

    // MARK: - History

    public func addHistory(original: String, transliteration: String, variantId: String) {
        let entry = HistoryEntry(
            original: original,
            transliteration: transliteration,
            variantId: variantId
        )
        history = HistoryStore.insert(entry, into: history)
    }

    public func deleteHistory(id: UUID) {
        history = HistoryStore.delete(id: id, from: history)
    }

    public func clearHistory() {
        history = HistoryStore.clear()
    }

    // MARK: - Review prompt

    /// Increment the cold-launch counter, capped per policy. Call once per process launch.
    public func registerLaunch() {
        reviewLaunchCount = ReviewPromptPolicy.nextLaunchCount(reviewLaunchCount)
    }

    /// "Not now": reset the counter, keep prompting after more launches.
    public func notNow() {
        reviewLaunchCount = 0
    }

    /// "Rate now" / "Don't ask again": never show the prompt again.
    public func dismissForever() {
        reviewPromptDismissed = true
    }

    // MARK: - Generic descriptor persistence

    public func value(for key: String) -> String? {
        userDefaults.string(forKey: "descriptor.\(key)")
    }

    public func setValue(_ value: String, for key: String) {
        userDefaults.set(value, forKey: "descriptor.\(key)")
    }
}
