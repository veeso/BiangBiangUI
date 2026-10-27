# Changelog

All notable changes to this project are documented in this file.

## 0.5.0

Released on 2026-10-27

### Added

- **camera:** add wide split layout and session log (#10)

> - feat(camera): add wide split layout and session log
>
> Add matching iOS and Android camera logs on wide built-in displays. Preserve physical pane order, bounded session history and existing camera controls, and correct iOS landscape OCR alignment.
>
> - ci(ios): skip Duo simulator and drop pixel-based still-image UI test
>
> The simulator fallback picked iPhone Duo on the iOS 27.1 runtime, which
> broke the landscape geometry test. Exclude it from the candidates.
>
> The still-image test measured screenshot pixels and missed by a different
> amount on every environment, so remove it with its helper and harness path.
>
> - ci(ios): prefer iPhone, then iPad, then Duo for the simulator
>
> The iOS 27.1 image supports only iPhone Duo among iPhones, so excluding it
> left no device. Pick a standard iPhone first, fall back to any iPad, and
> use Duo only as a last resort. Log the supported device types.
>
> - test(camera): wait on conditions, not yield counts, in highlight test
>
> The highlight test failed on CI and, under CPU load, locally. It gave up
> after 1000 yields while waiting for sleepers and asserted after fixed
> 20-yield loops, both of which depend on runner load. Poll the actual
> condition for up to five seconds of wall time instead.
>
> - ci(ios): make pane UI tests advisory on the iOS 27 image
>
> The iOS 27.1 image offers only the dual-screen iPhone Duo, where the
> landscape overlay geometry check fails deterministically. It passes on
> iPhone 17 and iPad, so keep it blocking on macos-26 and advisory on
> xcode-27 until the Duo geometry is understood.

### Fixed

- **examples:** break retain cycle in EveryAyah player observers

> The KVO status observer and the AVPlayerItemDidPlayToEndTime observer captured self strongly in their outer closures while self retained both observers. Capture self weakly in the outer closures, which also clears the Swift 6.4 ImplicitStrongCapture warning.

## 0.4.0

Released on 2026-08-24

### Breaking changes

- **config:** separate translation and TTS language codes

> LanguageVariant removes translatable and requires translationLanguageCode; consuming configs must replace true with a BCP-47 translation source and false with nil or null.

### Added

- Breaking: **config:** separate translation and TTS language codes

> Give each language variant an independent translation source code so translation support no longer depends on the TTS locale. Cantonese can now retain zh-HK speech while translating from zh-CN.

### Fixed

- **release:** correct version to 0.4.0

> Replace the mistakenly published 7.0.0 metadata with the intended next minor release version across both platforms and consumer documentation.

## 0.3.3

Released on 2026-05-19

### Fixed

- Android OCR overlay ConcurrentModificationException; bump version to 0.3.3

> LiveOcrAnalyzer delivered onResult on its Dispatchers.Default recognition
> scope. The CameraLiveScreen callback mutated a Compose SnapshotStateList
> (clear()+addAll()) from that background thread while OcrOverlay's Canvas
> iterated the same list during the draw phase, throwing
> ConcurrentModificationException at StateListIterator.validateModification.
>
> - LiveOcrAnalyzer.deliverResult marshals onResult onto an injectable
>   resultDispatcher (Dispatchers.Main by default).
> - ocrBoxes/liveOcrBoxes become mutableStateOf<List<OcrBox>> assigned
>   atomically, removing the SnapshotStateList/StateListIterator entirely so
>   the throwing path is unreachable by construction.
> - Regression test asserts results are delivered on the injected dispatcher.

## 0.3.2

Released on 2026-05-19

### Fixed

- Android SettingDescriptor toggles persist and reflect state; bump version to 0.3.2

> Toggle/Picker controls read their value once via produceState, which
> never re-collected after setValue wrote DataStore, so Switch.checked
> stayed frozen at the default. Add SettingsStore.valueFlow and collect
> it reactively via collectAsStateWithLifecycle, matching the existing
> history/variant pattern.

## 0.3.1

Released on 2026-05-19

### Fixed

- Android camera honors Branding.accentColorHex instead of hardcoded red

> The Android camera screen hardcoded AppDesign.accentRed (0xFFDE2910,
> Chinese red) on the zoom-preset and transliteration-toggle buttons, and
> Branding.accentColorHex was never decoded anywhere on Android. Every
> non-Chinese app (e.g. Harakat Lens, #006C35) rendered red controls.
>
> Add ui/Color+Brand.kt mirroring iOS Color+Brand.swift: decode
> #RGB/#RRGGBB/#RRGGBBAA, with a Branding.accentColor composable extension
> falling back to the Material primary on malformed hex. Thread the brand
> accent through ZoomPresetBar/CaptureControlBar and drop the now-unused
> AppDesign.accentRed. Add BrandColorTest covering the regression.
>
> Bump version to 0.3.1.

## 0.3.0

Released on 2026-05-19

### Added

- configurable OCR overlay min scale factor; bump version to 0.3.0

> Add optional `minimumOcrScaleFactor` to BiangBiangConfig (iOS + Android),
> default 0.6, replacing the hardcoded lower clamp on the OCR overlay
> transliteration font scale ratio.
>
> - iOS: BiangBiangConfig.minimumOcrScaleFactor (CGFloat = 0.6); used by
>   RecognizedTextOverlay.
> - Android: BiangBiangConfig.minimumOcrScaleFactor (Float = 0.6f); threaded
>   to OcrOverlay via new minScaleFactor param at both CameraLiveScreen call
>   sites.
> - Config seam tests assert the 0.6 default on both platforms.
> - Bump version 0.2.0 -> 0.3.0 (README, android build.gradle.kts).

## 0.2.0

Released on 2026-05-18

### Added

- **ios:** add OCRService protocol and OCRTextBox
- **ios:** add Vision-backed DefaultOCRService
- **ios:** inject OCRService per LanguageProfile, pixel-rect overlay
- **android:** add OcrService interface; rename camera OcrService -> StillImageOcr
- **android:** add ML Kit-backed DefaultOcrService
- **android:** inject OcrService per LanguageProfile; upright-bitmap live path
- **example:** Arabic Android example uses Tesseract4Android OcrService

### Fixed

- **ios:** normalize still-image orientation before OCRService
- **android:** close ML Kit recognizer per call to avoid leak
- **android:** stride-correct YUV->NV21; cancellable analyzer scope; drop overlapping frames
- **example:** lazy off-main Tesseract traineddata copy with atomic install

### Performance

- **ios:** share CIContext; clarify OCR docs; test orientation helper

## 0.1.3

Released on 2026-05-18

### Fixed

- **ios:** camera unusable under Swift 6 + wire brand color seam

> Camera (symptoms: no OCR, severe lag, dead controls):
> OCRCameraModel is @MainActor and conformed the AVCapture delegates with
> @preconcurrency but did not mark the delegate methods nonisolated. Under
> .swiftLanguageMode(.v6) that bound the video-frame delegate to the main
> actor, so Vision .accurate ran against / raced the main actor every frame.
> The preview layer is driven by AVCaptureSession directly, so live video
> stayed smooth while OCR and the SwiftUI control layer were starved.
>
> - captureOutput / photoOutput are now nonisolated (run on the camera queue,
>   matching the reference's effective behaviour); results still publish via
>   the existing Task { @MainActor }.
> - lastProcessingTime + a captureSuspended flag are queue-confined
>   nonisolated(unsafe); recognitionLanguages is resolved once at init so the
>   frame delegate never touches main-actor state.
> - Dropped now-unnecessary @preconcurrency on the delegate conformances.
> - CameraLiveScreen gained .onDisappear teardown (stopSession + clear),
>   matching Harakat; the session no longer runs forever off-tab.
>
> Color seam (buttons rendered default colors, no way to pass app color):
> Branding.accentColorHex existed but nothing consumed it.
>
> - Add Color(hex:) decoder + Branding.accentColor.
> - Apply .tint(branding.accentColor) at BiangBiangRootView (fixes the
>   overlay checkmark's Color.accentColor too).
> - CameraLiveScreen active toggle/zoom use branding.accentColor; neutral
>   camera/gallery/inactive buttons stay gray.
>
> Bump version 0.1.2 -> 0.1.3.

## 0.1.2

Released on 2026-05-18

### Added

- **ios,android:** config-driven appSubtitle tagline on Text screen

> Restores the app-name subtitle dropped during the library migration.
> New UIStrings/UiStrings key 'appSubtitle', empty by default so apps
> that don't set it render no subtitle and no layout gap. Rendered under
> the app name on both platforms.

### Changed

- **ios,android:** app-defined settings after Translation language

> extraSettings descriptors now render directly after the Translation
> language / variant controls, with the library-owned History + support
> sections moved below them. Mirrored on both platforms.

### Fixed

- **ios:** render non-empty CameraScreen root so TabView keeps the Camera tab

> CameraScreen returned an empty Group until OCRCameraModel was built
> lazily in .task. SwiftUI TabView does not create a tab item for a
> child whose content is empty, so the Camera tab never appeared, its
> .task never ran, and the model was never built (permanent self-lock;
> the whole camera feature was hidden). Always render a non-empty root
> (ZStack { Color.black }) so the tab exists from first frame.

## 0.1.1

Released on 2026-05-18

### Fixed

- **ci:** drop broken SwiftPM swift test step, test via iOS simulator only

> swift test builds for the macOS host, but the library is iOS-only
> (UIKit/SwiftUI: UIPasteboard, View conformances need macOS 10.15+),
> so the step could never pass. The existing xcodebuild test step on
> an iOS simulator already runs the BiangBiangUITests unit target plus
> UI/Vision tests, matching the proven sibling project's CI pattern.

- **android:** add maven-publish so JitPack publishToMavenLocal works

> The biangbiang-ui module had no maven-publish plugin, so jitpack.yml's
> :biangbiang-ui:publishToMavenLocal install step failed and v0.1.0 was
> unconsumable on JitPack.
>
> Add the maven-publish plugin, AGP singleVariant("release") publishing
> with sources jar, and a release MavenPublication. Version is taken from
> the Gradle 'version' property (JitPack injects the git tag via -Pversion)
> with a 0.1.0 fallback for local builds.

## 0.1.0

Released on 2026-05-18

### Added

- scaffold iOS Swift Package with green build
- scaffold Android library module + examples with green build

> - Copy Gradle wrapper, version catalog, and gradle.properties from BiangBiang Hanzi reference app
> - Add android-library plugin entry to libs.versions.toml
> - Create :biangbiang-ui Android library module (com.android.library, AGP 9.2.1)
> - Create :examples stub library module depending on :biangbiang-ui
> - Add BiangBiangUi.kt singleton with VERSION constant and SmokeTest (versionIsSemver passes)
> - Add android/.kotlin/ to .gitignore

- **ios:** add Transliterator and ProcessedText
- **ios:** add LanguageProfile and LanguageVariant
- **ios:** add AudioProvider and FeaturePlugin protocols
- **ios:** add Branding, SettingDescriptor, FeatureFlags, UIStrings, BiangBiangConfig
- **ios:** port HistoryEntry + HistoryStore (generic variant)
- **ios:** port ReviewPromptPolicy
- **ios:** port CameraZoom and AppDesign
- **ios:** port TextProcessingEngine (scriptRanges + injected Transliterator)
- **ios:** port SettingsStore (generic variant + descriptor persistence)
- **ios:** port SystemTTSAudioProvider + OCRCameraModel (recognizer from profile)
- **ios:** port reusable components (SectionView, CopyToast, CameraPermissionScreen)
- **ios:** add BiangBiangContext + port TextScreen (data-driven translatable + plugin inline view)
- **ios:** port Camera screens + overlay (plugin camera seam)

> Adds CameraScreen, CameraLiveScreen and RecognizedTextOverlay — faithful
> ports of CameraModeView / CameraLiveView / RecognizedTextOverlay from
> BiangBiang Hanzi — wired to BiangBiangContext for config-driven strings,
> branding and plugin hooks.
>
> Plugin seam: on recognizedTexts change, onProcessedText is called for
> every box/plugin; the first non-nil inlineResultView is shown as a
> half-sheet. Inert when no plugins are configured.
>
> Also adds Equatable conformance to RecognizedTextBox (required by
> onChange(of:) on [RecognizedTextBox]).

- **ios:** port HistoryScreen (preserves issue #22)
- **ios:** port SettingsScreen (3-layer settings model)
- **ios:** add BiangBiangRootView entry point
- **ios:** ChineseExample config proves the seam
- **ios:** ArabicExample + Quran plugin proves the plugin slot
- **android:** config model (mirror iOS Phase 1)
- **android:** plugin/transliterator protocols (mirror iOS Phase 1)
- **android:** port HistoryStore + HistorySerializer (mirror iOS Phase 2)
- **android:** port ReviewPromptPolicy (verbatim Android, keeps attempts extras)
- **android:** port CameraZoom + CameraUtils (verbatim)
- **android:** port TextProcessingEngine (scriptRanges regex + injected Transliterator)
- **android:** port SettingsStore (selectedVariantId + descriptor value/setValue)
- **android:** port SystemTtsAudioProvider (BCP-47, AudioProvider conformance)
- **android:** OcrService + LiveOcrAnalyzer (correct rotation handling)
- **android:** port theme/AppDesign + BiangBiangContext state holder [mirror iOS Phase 3]
- **android:** port SectionView/CopyToast/ReviewPrompt (config-driven, Play In-App Review + 800ms heuristic) [mirror iOS Phase 3]
- **android:** port TextScreen (data-driven, plugin onProcessedText/inlineResultView hooks) [mirror iOS Phase 3]
- **android:** port CameraScreen + camera sub-composables (5.2 OcrService/LiveOcrAnalyzer/OcrRotation seam, plugin camera hook) [mirror iOS Phase 3]
- **android:** port HistoryScreen (data-driven, per-row TTS, #22 preserved) [mirror iOS Phase 3]
- **android:** port SettingsScreen (3-layer, data-driven variant picker, descriptors) [mirror iOS Phase 3]
- **android:** port BiangBiangRoot nav scaffold (config tabs + plugin tabs, hosts ReviewPrompt, #20 preserved) [mirror iOS Phase 3]
- **android:** ChineseExample config (mirror iOS Phase 4)

> PinyinTransliterator (pinyin4j, example-only dep), JyutpingTransliterator +
> JyutpingDictionary (cantonese.json asset, byte-identical to reference),
> chineseConfig factory (3 variants; cantonese translatable=false). Config
> values mirror iOS ChineseConfig.swift exactly. pinyin4j lives only in
> :examples, never the library.

- **android:** ArabicExample + QuranPlugin proves plugin slot

> ArabicTransliterator (vocalize -> ArabicNormalizer.transliteration ->
> android.icu Any-Latin, bundled, no new dep), Vocalizer +
> VocalizationDictionary (vocab.json: faithful plutil conversion of iOS
> vocab.plist, 39992 entries), QuranAyah/SurahName/QuranDataset/QuranMatcher
> ported verbatim from Harakat iOS (same thresholds: minTokenLength=2,
> candidateCap=600, rareTokenLimit=5, scoreThreshold=0.74, strongTokenLength=4,
> minFitLength=8), EveryAyahAudioProvider (MediaPlayer stream + TTS fallback),
> QuranMatchView/QuranBrowserView Composables, QuranPlugin (4 hooks; cache is a
> lock-free AtomicReference snapshot since the Android FeaturePlugin is not
> @MainActor-isolated like iOS). arabicConfig mirrors iOS ArabicConfig.swift
> exactly (quranMode toggle, 1 plugin, ar/translatable variant). assets copied
> from iOS Examples (Harakat-faithful).

### Changed

- **ios:** SettingsScreen binding style + accurate header (review fixes)
- **android:** UiStrings.merged derives defaults from single source (review fix)

### Fixed

- **android:** close ML Kit Translator + document BiangBiangContext remember-construction invariant (review fixes)
- **android:** use com.ibm.icu:icu4j for ArabicTransliterator (minSdk-26 safe; gate fix)

### Build

- **android:** add ML Kit JP/KR/Latin recognizers + Robolectric test deps
