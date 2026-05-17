# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Project Overview

BiangBiangUI is a dual-platform (iOS + Android) shared UI library holding the
entire app logic and UI of the script-to-Latin OCR app family (BiangBiang
Hanzi, Harakat Lens, future Japanese/Korean). Each app reduces to one
`BiangBiangConfig` + a small `Transliterator`. The iOS and Android
implementations share architecture only — no shared source. They are two
parallel implementations kept structurally identical; parity is enforced by
review and mirrored tests, not by a compiler.

## Build & Development Commands

### iOS (Swift Package, SwiftUI)

- **Build:** `swift build`
- **Unit tests:** `swift test`
- **UI/Vision tests (need a simulator):**

  ```bash
  xcodebuild test -scheme BiangBiangUI -destination 'platform=iOS Simulator,name=iPhone 16'
  ```

- **Format code:** `swiftformat ./Sources ./Tests ./Examples`
  (requires `brew install swiftformat`).
  **Run this whenever iOS code is modified.**

### Android (Kotlin library module)

- **JDK (macOS):**
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
  and prepend `$JAVA_HOME/bin` to `PATH` before `./gradlew`.
- **Build:** `cd android && ./gradlew :biangbiang-ui:assembleRelease`
- **Unit tests:** `cd android && ./gradlew test`
- **Lint:** `cd android && ./gradlew lintDebug`
- **Single test class:**
  `cd android && ./gradlew test --tests "dev.veeso.biangbiangui.TextProcessingEngineTest"`

## Architecture

The library owns the root container (`TabView` / bottom navigation) and all
four tab screens (Text, Camera, History, Settings), the OCR/Vision pipeline,
History, the rate-app prompt, and TTS. Apps inject a `BiangBiangConfig`
(branding, `LanguageProfile`s, `SettingDescriptor`s, `FeaturePlugin`s, feature
flags, optional UI string overrides).

- `Config/` — the data model (no behaviour)
- `Protocols/` — `Transliterator`, `FeaturePlugin`, `AudioProvider`,
  `ProcessedText`
- `Services/` — `TextProcessingEngine` (span detect + spacing + debounce),
  `SettingsStore`, `HistoryStore`, `ReviewPromptPolicy`,
  `SystemTTS*AudioProvider`, OCR camera pipeline
- `UI/` — config-driven SwiftUI / Compose screens
- `Examples/` (iOS) / `android/examples/` — sample configs (Chinese,
  Arabic+Quran) that prove the seam and run in CI; these are NOT the shipping
  apps

### Core algorithm (both platforms)

`TextProcessingEngine` detects script spans via `LanguageProfile.scriptRanges`,
hands each isolated span to the app's `Transliterator`, preserves non-script
characters (Latin, digits, punctuation, emoji) in place, applies the spacing
rules, debounces text input 0.8s, and throttles live OCR to 1s. The camera OCR
pipeline has ONE correct rotation implementation per platform (this fixes
Harakat Android's rotation drift by construction).

## Distribution

- iOS: SwiftPM over git tag —
  `.package(url: "https://github.com/veeso/BiangBiangUI", from: "x.y.z")`
- Android: JitPack —
  `implementation("com.github.veeso.BiangBiangUI:biangbiang-ui:vX.Y.Z")`
- Both platforms share one tag `vX.Y.Z`.

## Platform Configuration

- **iOS:** min deployment iOS 17, Swift 6 strict concurrency, Swift Testing.
- **Android:** `minSdk 26`, `compileSdk 36`, Java 11, namespace
  `dev.veeso.biangbiangui`. The consuming app declares the `CAMERA` permission
  in its own manifest.
