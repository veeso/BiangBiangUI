# BiangBiangUI — Shared UI Library Design

Date: 2026-05-16
Status: Approved (brainstorm)

## 1. Purpose

`BiangBiangUI` is a dual-platform (iOS + Android) library that holds the
entire app logic and UI shared by a family of script-to-Latin OCR apps:

- BiangBiang Hanzi (Chinese → Pinyin/Jyutping)
- Harakat Lens (Arabic → Latin, with Quran features)
- Future: Japanese (kana/kanji → romaji), Korean (Hangul → romaja)

Each app reduces to a single `BiangBiangConfig` plus a small transliterator
implementation. All screens, services and built-in features live in the
library. The library also carries features currently tracked per app:
the rate-app prompt and the History feature.

The iOS and Android libraries share architecture only — no shared source.
They are two parallel implementations kept structurally identical.

## 2. Problem

Today every feature exists four times: BiangBiang iOS, BiangBiang Android,
Harakat iOS, Harakat Android. The two apps are ~75–80% identical (Harakat
was forked from BiangBiang). Copy-paste drift has already caused divergent
bugs (Harakat Android OCR rotation handling differs from BiangBiang).
Adding Japanese and Korean would push the duplication to 6–8 copies.

A configurable shared library collapses this to one implementation per
platform plus per-app configuration data.

## 3. Decisions

| Topic         | Decision                                                          |
| ------------- | ----------------------------------------------------------------- |
| Approach      | Single config object + protocol-based service injection           |
| Repo          | One repo `veeso/BiangBiangUI`, two roots (SPM + Android module)   |
| iOS dist      | Swift Package Manager over git tag                                |
| Android dist  | JitPack, artifact `com.github.veeso.BiangBiangUI:biangbiang-ui`   |
| UI seam       | Config-driven full screens (library renders everything)           |
| Core features | History, rate-app prompt, TTS — all built into the library        |
| Quran         | Not in library; injected via a feature-plugin slot (Harakat only) |
| Versioning    | Shared tag `vX.Y.Z` for both platforms                            |
| Issue         | Tracking issue filed on `veeso/BiangBiang-Hanzi` until repo ready |

## 4. Repository Layout

```text
BiangBiangUI/
  Package.swift                    # SPM product "BiangBiangUI" (iOS)
  Sources/BiangBiangUI/            # Swift: protocols, services, SwiftUI screens
  Tests/BiangBiangUITests/
  android/
    biangbiang-ui/                 # com.android.library, ns dev.veeso.biangbiangui
      src/main/java/dev/veeso/biangbiangui/...
      src/test/...
  jitpack.yml
  docs/
  README.md
```

Consumption:

- iOS: `.package(url: "https://github.com/veeso/BiangBiangUI", from: "x.y.z")`
- Android: `implementation("com.github.veeso.BiangBiangUI:biangbiang-ui:vX.Y.Z")`
  with the JitPack maven repository added.

App name, launcher icon and drawables stay in the **app** module on both
platforms (platform best practice). The library receives logo, button
logo and colors through configuration.

## 5. Entry Point

The app builds one config and hands it to the library root:

```swift
// iOS
@main struct App { var body: some Scene {
  WindowGroup { BiangBiangRootView(config: BiangBiangHanzi.config) }
}}
```

```kotlin
// Android
setContent { BiangBiangRoot(BiangBiangHanziConfig) }
```

The library owns the root container (`TabView` / bottom navigation),
all tab screens (Text, Camera, Settings, History), the rate prompt,
the camera OCR pipeline, and all shared sub-views (overlays, toast,
section views).

## 6. Configuration Model

Configuration is data, not code.

```text
BiangBiangConfig
  branding      { appName, accentColor, logo, buttonLogo, githubRepo,
                  supportEmail, appStoreId, playStoreId }
  languages     [LanguageProfile]
  extraSettings [SettingDescriptor]
  plugins       [FeaturePlugin]
  features      { history = true, ratePrompt = true, tts = true }
  strings?      # optional UI string overrides; library ships English defaults

LanguageProfile
  id, displayName
  scriptRanges  [Unicode range]      # e.g. 0x4E00...0x9FFF
  ocrRecognizer                      # Chinese | Latin | Arabic | Japanese | Korean
  variants      [LanguageVariant]    # may be empty

LanguageVariant
  id, displayName
  transliterator : Transliterator
  ttsLanguageCode : String?          # nil => TTS hidden for this variant
  translatable : Bool                # false => translation section hidden
```

Concrete profiles:

| App        | Profile  | Variants                                                                                              |
| ---------- | -------- | ----------------------------------------------------------------------------------------------------- |
| BiangBiang | Chinese  | Simplified + Traditional (translatable, `zh-CN`); Cantonese (Jyutping, `translatable=false`, `zh-HK`) |
| Harakat    | Arabic   | One: ICU + normalizer + vocalizer, translatable, `ar`                                                 |
| Japanese   | Japanese | One: kana/kanji → romaji, translatable, `ja`                                                          |
| Korean     | Korean   | One: Hangul → romaja, translatable, `ko`                                                              |

`translatable=false` hides the translation UI as data — no conditional
code path. Variant pickers render only when a profile declares variants.

## 7. Transliteration Interface

```text
protocol Transliterator {
  func transliterate(_ scriptSpan: String) -> String
}
```

The library `TextProcessingEngine` owns the common behaviour:

- span detection via `scriptRanges`
- non-script passthrough (Latin, digits, punctuation, emoji preserved)
- spacing rules between transliterated and non-script runs
- 0.8s input debounce, 1s OCR throttle

The app implementation only converts an already-isolated script span to
Latin. This matches the existing internals of both apps and keeps the
per-app code minimal.

## 8. Settings Model

Three layers:

1. **Shared core** — translation target language, History "Clear all",
   bug-report links. Identical for every app, rendered by the library.
2. **Data-driven variants** — the variant segmented picker renders from
   `LanguageProfile.variants`. No hand-written per-app UI.
3. **Injectable descriptors** — `extraSettings: [SettingDescriptor]`.

```text
SettingDescriptor { key, kind: toggle | picker, label, default, footer? }
```

The library renders descriptors in a Settings section, persists them
generically (iOS `UserDefaults`, Android DataStore), and exposes their
values to injected services and plugins via a `SettingsStore` accessor.

Harakat declares no Chinese variants and one `quranMode` toggle
descriptor; the Quran plugin reads `quranMode` from the store. No
forked Settings screen.

## 9. Feature-Plugin Slot

Quran is Harakat-only and never enters the library. It is injected as a
plugin implementing four hooks:

```text
protocol FeaturePlugin {
  var tabs: [PluginTab]                              # extra tab/screen
  func onProcessedText(_ result: ProcessedText)       # observe Text + Camera results
  func inlineResultView(for: ProcessedText) -> View?  # replace translit output on hit
  var audioProvider: AudioProvider?                   # alternate audio source
}
```

Quran plugin behaviour (lives in the Harakat app):

- `tabs` — a Quran browser tab
- `onProcessedText` — runs `QuranMatcher` asynchronously
- `inlineResultView` — returns `QuranMatchView` when a verse matches,
  replacing the plain transliteration output
- `audioProvider` — streams recitation from EveryAyah, otherwise the
  library's system TTS is used

The library invokes the hooks and never references Quran types.

## 10. Built-in Features

| Feature | Behaviour                                                                                                                                                      |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| History | `HistoryStore` + tab, generic over transliteration, dedup, ~500 silent cap, save from Text + long-press OCR box. Flag `features.history`. Preserves issue #22. |
| Rate    | Launch-count + 3-button dialog; store URL from `branding`. Flag `features.ratePrompt`. Preserves issue #20.                                                    |
| TTS     | Always present. `SystemTTSAudioProvider` keyed on variant `ttsLanguageCode`; plugin `audioProvider` overrides. Hidden when code is nil.                        |

Issue #20 detail preserved: persisted `reviewLaunchCount` (capped 5)
and `reviewPromptDismissed`; show when count ≥ 3 and not dismissed;
buttons "Rate now" / "Not now" (reset count) / "Don't ask again".

Issue #22 detail preserved: newest-first list, per-row TTS, global
original/transliterated toggle, tap-to-expand, swipe-delete + Clear
All, consecutive-duplicate skip, persisted across launches, silent
~500 cap, empty state.

## 11. Migration Phasing

Each phase is independently shippable.

1. Scaffold the repo: both roots, green empty builds, JitPack wired.
2. Port BiangBiang iOS into the library as the reference implementation;
   BiangBiang iOS becomes config-only; ship; parity tests pass.
3. Same for BiangBiang Android.
4. Migrate Harakat iOS onto the library: Arabic `LanguageProfile` plus
   the Quran plugin. This proves the config seam and the plugin slot.
5. Migrate Harakat Android onto the library; also fixes its OCR
   rotation drift bug.
6. Japanese and Korean apps: configuration only.

## 12. Testing

- Library unit tests: `TextProcessingEngine` span/spacing, `SettingsStore`
  round-trip, `HistoryStore` dedup and cap, rate-prompt logic. Swift
  Testing on iOS, JUnit on Android.
- Parity gate: each migrated app keeps its existing tests; they must
  stay green after migration.
- Quran logic is tested in the Harakat app, not the library.

## 13. Risks

- Dual-language maintenance: two parallel library codebases, structural
  parity enforced by review, not by a compiler.
- Distributable UI plumbing: SPM resource bundling and JitPack subdir
  multi-module builds need validation in phase 1.
- Migration regression across four live apps; mitigated by the parity
  gate and incremental phasing.

## 14. Out of Scope (YAGNI)

- No layered/multi-module split (single product per platform until a
  concrete need appears).
- No speculative configuration beyond the four known apps.
- No general extension system beyond the four named plugin hooks.
</content>
</invoke>
