# Configuration

`BiangBiangConfig` is the single injection point for a consuming app. The
whole app — branding, supported scripts, transliteration, extra settings,
plugins, feature toggles, and UI strings — is described by one value passed
to the library. This page documents every field.

The iOS and Android models are structurally identical; differences are
called out where they exist. The string-override key set is identical on
both platforms.

## Constructing the config

iOS builds the config as a `@MainActor static let` (no runtime context
needed). Android uses a factory function taking a `Context`, because some
transliterators and plugins load assets.

### iOS

```swift
import BiangBiangUI

public enum ChineseConfig {
    @MainActor
    public static let chineseConfig: BiangBiangConfig = .init(
        branding: Branding(
            appName: "BiangBiang Hanzi",
            accentColorHex: "#DE2910",
            logoAssetName: "Logo",
            buttonLogoAssetName: "ButtonLogo",
            githubRepo: "veeso/BiangBiang-Hanzi",
            supportEmail: "info@veeso.dev",
            appStoreId: "6754869174",
            playStoreId: "dev.veeso.biangbianghanzi"
        ),
        languages: [
            LanguageProfile(
                id: "chinese",
                displayName: "Chinese",
                scriptRanges: [0x4E00 ... 0x9FFF],
                ocrRecognizer: .chinese,
                variants: [
                    LanguageVariant(
                        id: "simplified",
                        displayName: "Simplified",
                        transliterator: PinyinTransliterator(),
                        ttsLanguageCode: "zh-CN",
                        translatable: true
                    ),
                ]
            ),
        ],
        extraSettings: [],
        plugins: [],
        features: FeatureFlags(),
        strings: nil
    )
}
```

### Android

```kotlin
fun chineseConfig(context: Context): BiangBiangConfig {
    val appContext = context.applicationContext
    return BiangBiangConfig(
        branding = Branding(
            appName = "BiangBiang Hanzi",
            accentColorHex = "#DE2910",
            logoAssetName = "Logo",
            buttonLogoAssetName = "ButtonLogo",
            githubRepo = "veeso/BiangBiang-Hanzi",
            supportEmail = "info@veeso.dev",
            appStoreId = "6754869174",
            playStoreId = "dev.veeso.biangbianghanzi",
        ),
        languages = listOf(
            LanguageProfile(
                id = "chinese",
                displayName = "Chinese",
                scriptRanges = listOf(0x4E00u..0x9FFFu),
                ocrRecognizer = OcrRecognizer.CHINESE,
                variants = listOf(
                    LanguageVariant(
                        id = "simplified",
                        displayName = "Simplified",
                        transliterator = PinyinTransliterator(),
                        ttsLanguageCode = "zh-CN",
                        translatable = true,
                    ),
                ),
            ),
        ),
        extraSettings = emptyList(),
        plugins = emptyList(),
        features = FeatureFlags(),
        strings = null,
    )
}
```

### Top-level fields

| Field           | Type                        | Required | Purpose                                                |
| --------------- | --------------------------- | -------- | ------------------------------------------------------ |
| `branding`      | `Branding`                  | yes      | App name, accent color, logos, store / support links   |
| `languages`     | `[LanguageProfile]`         | yes      | Supported scripts and their transliteration variants   |
| `extraSettings` | `[SettingDescriptor]`       | yes      | App-defined settings rows (may be empty)               |
| `plugins`       | `[FeaturePlugin]`           | yes      | Extra tabs / hooks / custom audio (may be empty)       |
| `features`      | `FeatureFlags`              | yes      | Toggles for History, rate prompt, TTS                  |
| `strings`       | `[String: String]?` / `Map` | yes      | UI string overrides; `nil` / `null` = English defaults |

The iOS `BiangBiangConfig` is `@MainActor`-isolated (not `Sendable`)
because `FeaturePlugin` vends SwiftUI views; every leaf config type is
`Sendable`.

## Branding

All fields required.

| Field                 | Type     | Purpose                                             |
| --------------------- | -------- | --------------------------------------------------- |
| `appName`             | `String` | Display name (e.g. `BiangBiang Hanzi`)              |
| `accentColorHex`      | `String` | Hex accent color (e.g. `#DE2910`)                   |
| `logoAssetName`       | `String` | Asset name for the main logo                        |
| `buttonLogoAssetName` | `String` | Asset name for the logo on buttons / settings       |
| `githubRepo`          | `String` | `owner/repo` for the bug-report link                |
| `supportEmail`        | `String` | Support contact email                               |
| `appStoreId`          | `String` | iOS App Store numeric ID (rate prompt / store link) |
| `playStoreId`         | `String` | Google Play package ID (rate prompt / store link)   |

## FeatureFlags

All fields default to `true`.

| Field        | Type   | Default | Effect when `false`          |
| ------------ | ------ | ------- | ---------------------------- |
| `history`    | `Bool` | `true`  | Hides the History tab        |
| `ratePrompt` | `Bool` | `true`  | Disables the rate-app prompt |
| `tts`        | `Bool` | `true`  | Disables text-to-speech      |

## LanguageProfile

One entry per supported script. All fields required; `variants` may be
empty but the variant picker only renders when it has entries.

| Field           | Type                    | Purpose                                      |
| --------------- | ----------------------- | -------------------------------------------- |
| `id`            | `String`                | Stable identifier (e.g. `chinese`, `arabic`) |
| `displayName`   | `String`                | User-facing name                             |
| `scriptRanges`  | `[ClosedRange<UInt32>]` | Unicode scalar ranges defining the script    |
| `ocrRecognizer` | `OCRRecognizer`         | OCR engine used for this script              |
| `variants`      | `[LanguageVariant]`     | Transliteration variants                     |

`TextProcessingEngine` uses `scriptRanges` to isolate script spans;
non-script characters (Latin, digits, punctuation, emoji) are preserved in
place.

`OCRRecognizer` (iOS) / `OcrRecognizer` (Android) cases: `chinese`,
`latin`, `arabic`, `japanese`, `korean`.

## LanguageVariant

A transliteration variant within a profile (e.g. Simplified vs Cantonese).

| Field             | Type             | Required | Purpose                                    |
| ----------------- | ---------------- | -------- | ------------------------------------------ |
| `id`              | `String`         | yes      | Stable variant identifier                  |
| `displayName`     | `String`         | yes      | User-facing variant name                   |
| `transliterator`  | `Transliterator` | yes      | Converts an isolated script span to Latin  |
| `ttsLanguageCode` | `String?`        | no       | BCP-47 code; `nil` hides the Listen button |
| `translatable`    | `Bool`           | yes      | Whether the Translate UI is shown for this |

## SettingDescriptor

App-defined settings rows, rendered after the built-in settings and
persisted in `UserDefaults` / `SharedPreferences` under `key`. Read the
stored value from your transliterator / plugin to branch behaviour.

| Field          | Type      | Required | Purpose                                      |
| -------------- | --------- | -------- | -------------------------------------------- |
| `key`          | `String`  | yes      | Storage key; also the descriptor identity    |
| `kind`         | `Kind`    | yes      | Control type                                 |
| `label`        | `String`  | yes      | User-facing row label                        |
| `defaultValue` | `String`  | yes      | `"true"` / `"false"` for toggle; option text |
| `footer`       | `String?` | no       | Optional explanatory footer                  |

`Kind` cases:

- `toggle` — boolean switch
- `picker(options:)` — dropdown over a `[String]` of options

iOS models `Kind` as an `enum`; Android as a `sealed class`
(`Kind.Toggle`, `Kind.Picker(options)`).

```swift
SettingDescriptor(
    key: "quranMode",
    kind: .toggle,
    label: "Quran mode",
    defaultValue: "false"
)
```

## Plugins

A `FeaturePlugin` injects extra behaviour. Every member has a default
no-op / `nil` implementation, so a plugin only overrides what it needs.

| Member             | Type                       | Default | Purpose                               |
| ------------------ | -------------------------- | ------- | ------------------------------------- |
| `tabs`             | `[PluginTab]`              | `[]`    | Extra tabs added to the root nav      |
| `onProcessedText`  | `(ProcessedText) -> Void`  | no-op   | Hook fired after each transliteration |
| `inlineResultView` | `(ProcessedText) -> View?` | `nil`   | Custom view under the result          |
| `audioProvider`    | `AudioProvider?`           | `nil`   | Replaces the system TTS provider      |

`PluginTab` fields: `id`, `title`, `systemImage` (SF Symbol on iOS /
Material icon name on Android), `content` (`AnyView` on iOS /
`@Composable () -> Unit` on Android).

```swift
extraSettings: [
    SettingDescriptor(
        key: "quranMode", kind: .toggle,
        label: "Quran mode", defaultValue: "false"
    ),
],
plugins: [QuranPlugin()],
```

### AudioProvider

Supply a custom TTS / audio backend via a plugin's `audioProvider`.

| Member      | Type                                    | Purpose                      |
| ----------- | --------------------------------------- | ---------------------------- |
| `play`      | `(text: String, languageCode: String?)` | Start playback (BCP-47 code) |
| `stop`      | `() -> Void`                            | Stop playback                |
| `isPlaying` | `Bool`                                  | Current playback state       |

## Transliterator

The app's only required logic. The library owns span detection, spacing,
and debounce; the transliterator receives one isolated script span and
returns its Latin form. It must be pure and `Sendable`.

```swift
func transliterate(_ scriptSpan: String) -> String
```

On Android it is a `fun interface`, so it is lambda-constructible:
`Transliterator { it.uppercase() }`.

## ProcessedText

The result value passed to `FeaturePlugin.onProcessedText` /
`inlineResultView`.

| Field             | Type     | Purpose                               |
| ----------------- | -------- | ------------------------------------- |
| `original`        | `String` | Input text                            |
| `transliteration` | `String` | Transliterated output                 |
| `variantId`       | `String` | `LanguageVariant.id` that produced it |
| `source`          | `Source` | `.text` (typed) or `.camera` (OCR)    |

## String overrides

The library ships English defaults in `UIStrings` (iOS,
`Sources/BiangBiangUI/Config/UIStrings.swift`) and `UiStrings` (Android,
`config/UiStrings.kt`). A consuming app overrides any subset by passing a
`[String: String]` / `Map<String, String>` keyed by the property name;
unset keys fall back to the English default. The key set is identical on
both platforms.

```swift
BiangBiangConfig(
    // ...
    strings: [
        "inputTitle": "Hanzi",
        "outputTitle": "Pinyin",
        "appSubtitle": "Convert Hanzi to Pinyin",
    ]
)
```

### Keys

| Key                        | Default                                          | Used in                                  |
| -------------------------- | ------------------------------------------------ | ---------------------------------------- |
| `appSubtitle`              | `""` (empty — no subtitle rendered)              | Text screen tagline under the app name   |
| `inputTitle`               | `Text`                                           | Text screen input section title          |
| `outputTitle`              | `Transliteration`                                | Text screen output section title         |
| `translationTitle`         | `Translation`                                    | Text screen translation section title    |
| `paste`                    | `Paste`                                          | Text screen input paste action           |
| `copy`                     | `Copy`                                           | Output / translation copy action         |
| `listen`                   | `Listen`                                         | TTS play button                          |
| `stop`                     | `Stop`                                           | TTS stop button                          |
| `save`                     | `Save`                                           | Text screen save-to-history button       |
| `savedToHistory`           | `Saved to History`                               | Save confirmation toast                  |
| `textCopied`               | `Text copied`                                    | Copy confirmation toast                  |
| `clearAll`                 | `Clear All`                                      | History clear-all button                 |
| `clearAllConfirm`          | `Delete all history?`                            | History clear-all confirmation           |
| `original`                 | `Original`                                       | History row original label               |
| `transliterated`           | `Transliterated`                                 | History row transliteration label        |
| `historyEmpty`             | `No history yet`                                 | History empty state                      |
| `cameraDisabledTitle`      | `Camera access disabled`                         | Camera permission screen title           |
| `cameraDisabledMessage`    | `Enable camera access in Settings to scan text.` | Camera permission screen message         |
| `openSettings`             | `Open Settings`                                  | Camera permission screen settings button |
| `tapToCopyLongPressToSave` | `Tap to copy · long-press to save`               | Camera live overlay hint                 |
| `translate`                | `Translate`                                      | Translation section button               |
| `reportBug`                | `Report a bug`                                   | Settings screen support section          |
| `openGithubIssues`         | `Open GitHub Issues`                             | Settings screen support section          |
| `sendEmail`                | `Send Email`                                     | Settings screen support section          |
| `rateTitle`                | `Enjoying the app?`                              | Rate-app prompt title                    |
| `rateMessage`              | `A quick rating really helps.`                   | Rate-app prompt message                  |
| `rateNow`                  | `Rate now`                                       | Rate-app prompt confirm button           |
| `notNow`                   | `Not now`                                        | Rate-app prompt defer button             |
| `dontAskAgain`             | `Don't ask again`                                | Rate-app prompt dismiss button           |
| `translationLanguage`      | `Translation language`                           | Settings screen translation language     |
| `tabText`                  | `Text`                                           | Text tab label                           |
| `tabCamera`                | `Camera`                                         | Camera tab label                         |
| `tabHistory`               | `History`                                        | History tab label                        |
| `tabSettings`              | `Settings`                                       | Settings tab label                       |

> An unknown key in the override map is ignored. Keys are case-sensitive and
> must match the property name exactly.

## Platform divergences

| Aspect                   | iOS                                  | Android                                |
| ------------------------ | ------------------------------------ | -------------------------------------- |
| `BiangBiangConfig`       | `@MainActor struct` (not `Sendable`) | `class`                                |
| Construction             | static `let`, no context             | factory function taking `Context`      |
| `Transliterator`         | `protocol`                           | `fun interface` (lambda-constructible) |
| `SettingDescriptor.Kind` | `enum`                               | `sealed class`                         |
| `PluginTab.content`      | `AnyView` (SwiftUI)                  | `@Composable () -> Unit` (Compose)     |
| String override keying   | property name                        | property name (identical)              |
