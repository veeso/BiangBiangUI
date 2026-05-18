@testable import BiangBiangUI
import Foundation
import Testing

@MainActor
struct SettingsStoreHistoryTests {
    private func makeDefaults() -> UserDefaults {
        let suite = UUID().uuidString
        let d = UserDefaults(suiteName: suite)!
        d.removePersistentDomain(forName: suite)
        return d
    }

    @Test func defaultHistoryIsEmpty() {
        let s = SettingsStore(userDefaults: makeDefaults())
        #expect(s.history.isEmpty)
    }

    @Test func addHistoryDedupsConsecutive() {
        let s = SettingsStore(userDefaults: makeDefaults())
        s.addHistory(original: "你好", transliteration: "nǐ hǎo", variantId: "mandarin")
        s.addHistory(original: "你好", transliteration: "nǐ hǎo", variantId: "mandarin")
        #expect(s.history.count == 1)
    }

    @Test func historyRoundTripsThroughUserDefaults() {
        let defaults = makeDefaults()
        let a = SettingsStore(userDefaults: defaults)
        a.addHistory(original: "我", transliteration: "wǒ", variantId: "cantonese")
        let b = SettingsStore(userDefaults: defaults)
        #expect(b.history.count == 1)
        #expect(b.history.first?.original == "我")
        #expect(b.history.first?.variantId == "cantonese")
    }

    @Test func clearHistoryEmptiesAndPersists() {
        let defaults = makeDefaults()
        let a = SettingsStore(userDefaults: defaults)
        a.addHistory(original: "我", transliteration: "wǒ", variantId: "mandarin")
        a.clearHistory()
        let b = SettingsStore(userDefaults: defaults)
        #expect(b.history.isEmpty)
    }
}

@MainActor
struct SettingsStoreReviewTests {
    private func makeDefaults() -> UserDefaults {
        let suite = UUID().uuidString
        let d = UserDefaults(suiteName: suite)!
        d.removePersistentDomain(forName: suite)
        return d
    }

    @Test func defaultsAreZeroAndNotDismissed() {
        let s = SettingsStore(userDefaults: makeDefaults())
        #expect(s.reviewLaunchCount == 0)
        #expect(s.reviewPromptDismissed == false)
    }

    @Test func registerLaunchIncrementsAndCapsAtFive() {
        let s = SettingsStore(userDefaults: makeDefaults())
        for _ in 0 ..< 7 {
            s.registerLaunch()
        }
        #expect(s.reviewLaunchCount == 5)
    }

    @Test func registerLaunchPersists() {
        let d = makeDefaults()
        let a = SettingsStore(userDefaults: d)
        a.registerLaunch()
        a.registerLaunch()
        let b = SettingsStore(userDefaults: d)
        #expect(b.reviewLaunchCount == 2)
    }

    @Test func notNowResetsCountKeepsDismissedFalse() {
        let s = SettingsStore(userDefaults: makeDefaults())
        s.registerLaunch(); s.registerLaunch(); s.registerLaunch()
        s.notNow()
        #expect(s.reviewLaunchCount == 0)
        #expect(s.reviewPromptDismissed == false)
    }

    @Test func dismissForeverPersists() {
        let d = makeDefaults()
        let a = SettingsStore(userDefaults: d)
        a.dismissForever()
        #expect(a.reviewPromptDismissed == true)
        let b = SettingsStore(userDefaults: d)
        #expect(b.reviewPromptDismissed == true)
    }
}

@MainActor
struct SettingsStoreDescriptorTests {
    private func makeDefaults() -> UserDefaults {
        let suite = UUID().uuidString
        let d = UserDefaults(suiteName: suite)!
        d.removePersistentDomain(forName: suite)
        return d
    }

    @Test func descriptorRoundTripsAndSeedsDefault() {
        let d = SettingDescriptor(key: "quranMode", kind: .toggle, label: "Quran",
                                  defaultValue: "false")
        let s = SettingsStore(userDefaults: makeDefaults(), variants: [],
                              descriptors: [d], defaultLanguage: "en")
        #expect(s.value(for: "quranMode") == "false")
        s.setValue("true", for: "quranMode")
        #expect(s.value(for: "quranMode") == "true")
    }

    @Test func selectedVariantPersists() {
        let ud = makeDefaults()
        let s1 = SettingsStore(userDefaults: ud, variants: [], descriptors: [],
                               defaultLanguage: "en")
        s1.selectedVariantId = "cantonese"
        let s2 = SettingsStore(userDefaults: ud, variants: [], descriptors: [],
                               defaultLanguage: "en")
        #expect(s2.selectedVariantId == "cantonese")
    }
}
