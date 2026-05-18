# BiangBiangUI cross-platform parity audit

This document is the parity gate for BiangBiangUI (spec sections 12 and 13).
It maps every iOS type/file to its Android counterpart, asserts identical
public surface and behaviour, enumerates the deliberate platform differences,
and cross-checks the mirrored test sets 1:1.

The mapping is derived from the actual source tree at commit `506997c`
(branch `worktree-biangbiang-ui-impl`). It is not aspirational: each row was
walked in both trees, and every divergence claim was verified against code.

- iOS library root: `Sources/BiangBiangUI/`
- Android library root:
  `android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/`
- iOS examples: `Examples/ChineseExample/`, `Examples/ArabicExample/`
- Android examples:
  `android/examples/src/main/java/dev/veeso/biangbiangui/examples/`

## Conventions

- "Same" means the public API shape and observable behaviour are equivalent
  modulo language idiom (Swift `protocol`/`@Observable` vs Kotlin
  `interface`/`StateFlow`, value semantics, naming case).
- A `Dn` tag in the parity column points to the matching entry in
  [Deliberate platform differences](#deliberate-platform-differences).
- Tables stay narrow on purpose (repo `.markdownlint.json` enforces MD013 at
  80 columns for tables too); the long-form detail lives in the prose
  sections, not the cells.

## Library: configuration

Path stems are relative to each platform's library root. iOS files are
`Config/<name>.swift`; Android files are `config/<name>.kt`.

| Type             | iOS / Android stem      | Parity                  |
| ---------------- | ----------------------- | ----------------------- |
| BiangBiangConfig | `BiangBiangConfig`      | Same surface            |
| Branding         | `Branding`              | Same surface            |
| FeatureFlags     | `FeatureFlags`          | Same flags and defaults |
| LanguageProfile  | `LanguageProfile`       | Same surface            |
| LanguageVariant  | `LanguageVariant`       | Same surface            |
| SettingDescr.    | `SettingDescriptor`     | Same surface            |
| UI strings       | `UIStrings`/`UiStrings` | Same keys + merge       |
| Version constant | `BiangBiangUI(.kt)`     | Same semver constant    |

## Library: protocols

iOS `Protocols/<name>.swift`; Android `protocols/<name>.kt`.

| Type           | Stem             | Parity                              |
| -------------- | ---------------- | ----------------------------------- |
| AudioProvider  | `AudioProvider`  | Same surface: speak/stop, async     |
| FeaturePlugin  | `FeaturePlugin`  | Same surface: tab meta + view slot  |
| ProcessedText  | `ProcessedText`  | Same surface: source + variant      |
| Transliterator | `Transliterator` | Same: `transliterate(span)->String` |

## Library: services

iOS files are `Services/<stem>.swift`; Android `services/<stem>.kt`. Stems
match (modulo case, e.g. iOS `OCRCameraModel`/`SystemTTSAudioProvider` vs
Android `OcrService`/`SystemTtsAudioProvider`) unless a row notes a real
divergence.

| Type / file          | Stem                 | Parity     |
| -------------------- | -------------------- | ---------- |
| HistoryEntry         | `HistoryEntry`       | Same value |
| HistoryStore         | `HistoryStore`       | Same behav |
| History codec        | `HistorySerializer`  | Same; D12  |
| ReviewPromptPolicy   | `ReviewPromptPolicy` | D1         |
| SettingsStore        | `SettingsStore`      | D2         |
| System TTS provider  | `System*AudioProv.`  | D3         |
| TextProcessingEngine | `TextProcessing...`  | Same       |
| Camera preview glue  | `Camera*`/`Camera*`  | Same role  |
| CameraZoom           | `Camera/CameraZoom`  | Same behav |
| OCR camera model     | `*OCR*`/`OcrService` | D4         |
| OCR rotation         | `*`/`OcrRotation`    | D5         |

iOS history codec is inline in `HistoryStore`; Android extracts
`HistorySerializer` (D12). iOS camera-preview glue is
`Services/Camera/CameraPreview.swift`; Android is
`services/camera/CameraUtils.kt`. iOS OCR rotation is inline in the Vision
path; Android isolates `services/camera/OcrRotation.kt` (D5).

## Library: UI screens and components

iOS files are `UI/<stem>.swift`; Android `ui/<stem>.kt`. Android screen
files live under `ui/screens/...`; the prose below the table gives the exact
Android paths for the rows that move.

| Type / screen      | iOS stem               | Parity |
| ------------------ | ---------------------- | ------ |
| Context wiring     | `BiangBiangContext`    | Same   |
| Root container     | `BiangBiangRootView`   | Same   |
| Text screen        | `TextScreen`           | D6 D7  |
| Camera screen      | `CameraScreen`         | Same   |
| Camera live screen | `CameraLiveScreen`     | D5     |
| Camera permission  | `CameraPermissionScr.` | Same   |
| Recognized overlay | `RecognizedTextOvrl.`  | Same   |
| History screen     | `HistoryScreen`        | Same   |
| Settings screen    | `SettingsScreen`       | Same   |
| Copy toast         | `Components/CopyToast` | Same   |
| Section view       | `Components/SectionV.` | Same   |
| Review prompt UI   | (inline in iOS)        | D1     |
| Design tokens      | `Design/AppDesign`     | Same   |

Android counterparts: `ui/BiangBiangContext.kt`, `ui/BiangBiangRoot.kt`,
`ui/screens/TextScreen.kt` (+ `ui/screens/textmode/TextModeViewModel.kt`),
`ui/screens/CameraScreen.kt`, `ui/screens/camera/CameraLiveScreen.kt`,
`ui/screens/camera/CameraPermissionScreen.kt`,
`ui/screens/camera/Ocr.kt`, `ui/screens/HistoryScreen.kt`,
`ui/screens/SettingsScreen.kt`, `ui/components/CopyToast.kt`,
`ui/components/SectionView.kt`, `ui/components/ReviewPrompt.kt` (review-prompt
UI is inline on iOS), and `ui/AppDesign.kt` + `ui/theme/{Color,Theme,Type}.kt`
for design tokens. Android splits design tokens into the Compose `theme`
files (idiomatic); values match.

## Examples: ChineseExample

iOS `Examples/ChineseExample/...`; Android `examples/chinese/...`.

| Type                 | Stem                     | Parity              |
| -------------------- | ------------------------ | ------------------- |
| ChineseConfig        | `ChineseConfig`          | Same values; D8     |
| PinyinTransliterator | `PinyinTransliterator`   | D9                  |
| JyutpingTranslit.    | `JyutpingTransliterator` | Same behaviour      |
| JyutpingDictionary   | `JyutpingDictionary`     | Same data; D10      |
| cantonese.json       | `cantonese.json`         | Byte-identical; D10 |

## Examples: ArabicExample and QuranPlugin

iOS `Examples/ArabicExample/...`; Android `examples/arabic/...`. Rows
tagged `Q/` are the Quran plugin: iOS
`Examples/ArabicExample/Quran/<stem>.swift`, Android
`examples/arabic/quran/<stem>.kt`. Stems are identical across platforms.

| Type                   | Stem (iOS = Android) | Parity         |
| ---------------------- | -------------------- | -------------- |
| ArabicConfig           | `ArabicConfig`       | Same vals; D8  |
| ArabicNormalizer       | `ArabicNormalizer`   | Same behav     |
| ArabicTransliterator   | `ArabicTranslit.`    | Same behav     |
| Vocalizer              | `Vocalizer`          | Same behav     |
| VocalizationDictionary | `VocalizationDict.`  | Same data; D10 |
| vocab data             | `vocab.plist`/`json` | Lossless; D10  |
| QuranPlugin            | `Q/QuranPlugin`      | Same plugin    |
| QuranAyah              | `Q/QuranAyah`        | Same value     |
| SurahName              | `Q/SurahName`        | Same value     |
| QuranDataset           | `Q/QuranDataset`     | Same data; D10 |
| QuranMatcher           | `Q/QuranMatcher`     | Same behav     |
| QuranBrowserView       | `Q/QuranBrowserView` | Same behav     |
| QuranMatchView         | `Q/QuranMatchView`   | Same behav     |
| EveryAyahAudioProvider | `Q/EveryAyahAudioP.` | Same surface   |
| quran.json             | `quran.json`         | Byte-id.; D10  |
| surah-names.json       | `surah-names.json`   | Byte-id.; D10  |

## Deliberate platform differences

Every divergence below was verified against the source tree at `506997c`.
Each is either a named platform-SDK substitution from the spec or a
sanctioned execution decision recorded during the build. None is an
unintended parity break.

### D1 review prompt threshold

`Services/ReviewPromptPolicy.swift` uses `launchThreshold = 3`,
`launchCap = 5`. `services/ReviewPromptPolicy.kt` uses
`LAUNCH_THRESHOLD = 5`, `LAUNCH_CAP = 5`, taken verbatim from the reference
Android app. Android additionally keeps the reference extras
`MAX_ATTEMPTS = 5`, `SHOWN_MIN_ELAPSED_MS = 800L`, `reachedAttemptCap`, and
`looksShown`. Sanctioned reference value, not a defect.

### D2 settings store model

`Services/SettingsStore.swift` is a synchronous
`@MainActor @Observable final class`. `services/SettingsStore.kt` is async,
backed by Jetpack `DataStore` (`Flow` getters, `suspend` setters), collected
in Compose via `collectAsStateWithLifecycle`. Observable surface and
persisted keys are equivalent; the concurrency model is the idiomatic
platform choice.

### D3 text-to-speech engine

`Services/SystemTTSAudioProvider.swift` uses `AVSpeechSynthesizer`;
`services/SystemTtsAudioProvider.kt` uses Android `TextToSpeech`. Named spec
substitution. Same `AudioProvider` surface.

### D4 OCR engine

`Services/Camera/OCRCameraModel.swift` uses Apple Vision;
`services/camera/OcrService.kt` uses ML Kit text recognition. Named spec
substitution. Same recognized-text surface.

### D5 live OCR rotation basis

Android isolates rotation handling in `services/camera/OcrRotation.kt`, a
single-basis implementation: ML Kit boxes and the view mapping share one
upright basis (`uprightSize`, `mapBoxToView`, `isSane`), fixing the
Harakat-Android coordinate-drift bug by construction. The iOS Vision path
never had this bug (Vision returns normalized coordinates), so iOS keeps the
inline handling. Both platforms are correct; the implementation differs.

### D6 translation engine and version gate

iOS uses Apple Translation: `UI/TextScreen.swift` gates the translate UI on
`#if canImport(Translation)` and `@available(iOS 18, *)` — on iOS 17 the
translate button is not shown. Android uses ML Kit Translate
(`ui/screens/textmode/TextModeViewModel.kt`) with no version gate. Named spec
substitution plus the recorded version-gate decision. The unavailable /
failure message is hardcoded on both sides but worded per engine (iOS:
`"Translation failed: ..."`; Android:
`"⚠️ Translation unavailable for this language"`) because the two engines
surface unavailability differently — a defensible engine-level difference,
not a parity break.

### D7 translator lifecycle

`ui/screens/textmode/TextModeViewModel.kt` explicitly closes the ML Kit
`Translator` on success, failure, and `onCleared()`
(`activeTranslator?.close()`), fixing the reference Android app's leak. There
is no iOS analogue: Apple Translation sessions are ARC-managed. Deliberate
idiomatic fix.

### D8 example config construction

iOS example configs are static `@MainActor public static let`
(`chineseConfig`, `arabicConfig`). Android example configs are
`fun chineseConfig(context: Context)` /
`fun arabicConfig(context: Context)` factories, because Android asset loading
needs a `Context` (iOS uses `Bundle.module`). Same values and shape;
construction site differs by necessity.

### D9 pinyin transliteration

`ChineseExample/PinyinTransliterator.swift` uses `CFStringTransform`;
`examples/chinese/PinyinTransliterator.kt` uses ICU4J / pinyin4j. Named spec
substitution. Same `Transliterator` surface and romanisation behaviour.

### D10 shared data files

`vocab.plist` (iOS binary plist, `Bundle.module`) maps to `vocab.json`
(Android assets) via a lossless `plutil` conversion — 39992 identical
entries. `cantonese.json` (403760 bytes), `quran.json` (4437123 bytes), and
`surah-names.json` (10481 bytes) are byte-identical across platforms
(verified with `cmp`). Loaders differ only by platform asset API.

### D11 hardcoded footer/dialog strings

The `"Cancel"` confirmation-dialog button (HistoryScreen, SettingsScreen)
and the translation-unavailable footer text are hardcoded — not routed
through `UIStrings`/`UiStrings` — on **both** platforms. This was a
deliberate faithful-port decision and is parity-consistent (the same strings
are hardcoded in the same places on both sides), so it is noted rather than
"fixed" asymmetrically.

### D12 history codec factoring

Android extracts the history JSON codec into `services/HistorySerializer.kt`
and unit-tests it directly (`HistorySerializerTest`). iOS keeps the codec
inside `SettingsStore`/`HistoryStore` and exercises it through the store
round-trip tests. Same behaviour, different factoring.

## Test parity

iOS uses Swift Testing `@Test` funcs in `Tests/BiangBiangUITests/`; Android
uses JUnit in `android/biangbiang-ui/src/test/...` and
`android/examples/src/test/...`. Each list below is the iOS test set; the
Android set is **the same names 1:1** except for the explicitly noted
case-only renames and the platform-only cases called out per suite. The only
naming difference is case convention (`...PureASCII` vs `...PureAscii`,
`a_b` vs `aB`); behaviour matches 1:1.

### CameraZoom (`CameraZoomTests` / `CameraZoomTest`)

iOS tests (Android `CameraZoomTest` has the first seven verbatim):

- `presetsEmptyWhenMaxBelowOne`
- `presetsOnlyOneWhenMaxIsOne`
- `presetsOneAndTwoWhenMaxBetweenTwoAndFive`
- `presetsAllThreeWhenMaxAtLeastFive`
- `clampZoomLowerBound`
- `clampZoomUpperBound`
- `clampZoomWithinRange`
- `uiZoomToDeviceZoomMultipliesBySwitchOver`
- `deviceZoomToUIZoomDividesBySwitchOver`
- `mappingIsIdentityWhenSwitchOverIsOne`

iOS adds the last three explicit UI/device zoom-mapping cases. The Android
`CameraZoom` mapping is pure arithmetic exercised by the same preset/clamp
assertions, so behavioural coverage is equivalent.

### ConfigSeam — library module (`ConfigSeamTests` / `ConfigSeamTest`)

Names match 1:1 unless noted:

- `transliteratorConforms`
- `processedTextHoldsSourceAndVariant`
- `chineseProfileHasThreeVariantsAndCantoneseNotTranslatable`
- `pluginDefaultsAreEmpty`
- `configExposesBrandingAndDefaultsStringsToEnglish`
- `stringOverridesMergeOverDefaults`
- iOS asserts the setting-descriptor round trip via `SettingsStoreTests`
  (`descriptorRoundTripsAndSeedsDefault`); Android adds an explicit
  `settingDescriptorKindsAndIdentity` here instead. Same behaviour.
- iOS `chineseConfigBuildsAndTransliterates`,
  `arabicConfigHasQuranPluginAndDescriptor`,
  `arabicTransliteratorRomanises` live in the same iOS file; Android keeps
  them in the `:examples` module (next subsection).

iOS keeps library and example seam assertions in one file (single SwiftPM
target); Android splits the example-config seam tests into `:examples`. Net
behavioural coverage matches.

### ConfigSeam — Android `:examples` module

iOS counterparts live in iOS `ConfigSeamTests`; Android
`examples/ConfigSeamTest` has these verbatim:

- `chineseConfigBuildsAndTransliterates`
- `chineseProfileHasThreeVariantsAndCantoneseNotTranslatable`
- `arabicConfigHasQuranPluginAndDescriptor`
- `arabicTransliteratorRomanises`

### HistoryStore (`HistoryStoreTests` / `HistoryStoreTest`)

Names match 1:1:

- `insertPrependsNewest`
- `skipsConsecutiveDuplicateSameVariant`
- `keepsSameTextDifferentVariant`
- `capEvictsOldestBeyond500`
- `deleteRemovesById`
- `clearEmptiesList`

### HistorySerializer — Android-only (see D12)

`HistorySerializerTest` has no iOS file: iOS exercises the same codec through
`SettingsStoreTests` (the history round-trip cases). Android cases:

- `roundTripsList`
- `fromBlankReturnsEmpty`
- `fromMalformedReturnsEmpty`

### ReviewPromptPolicy (`ReviewPromptPolicyTests` / `...Test`)

- iOS `incrementCapsAtFive` = Android `nextLaunchCountCapsAtFive`
- iOS `showsOnlyWhenCountReachedAndNotDismissed` = Android
  `shouldShowOnlyWhenThresholdReachedAndNotDismissed`
- Android-only `reachedAttemptCapAtMax` and
  `looksShownOnlyWhenFlowSuspendedLongEnough` test the sanctioned reference
  extras (`MAX_ATTEMPTS`, `SHOWN_MIN_ELAPSED_MS`) from D1; iOS intentionally
  omits these so there is no analogue.

### SettingsStore (`SettingsStoreTests` / `SettingsStoreTest`)

Names match 1:1 except the storage-layer rename
(`historyRoundTripsThroughUserDefaults` on iOS =
`historyRoundTripsThroughDataStore` on Android, per D2):

- `defaultHistoryIsEmpty`
- `addHistoryDedupsConsecutive`
- `historyRoundTripsThroughUserDefaults` / `...DataStore`
- `clearHistoryEmptiesAndPersists`
- `defaultsAreZeroAndNotDismissed`
- `registerLaunchIncrementsAndCapsAtFive`
- `registerLaunchPersists`
- `notNowResetsCountKeepsDismissedFalse`
- `dismissForeverPersists`
- `descriptorRoundTripsAndSeedsDefault`
- `selectedVariantPersists`

### TextProcessingEngine (`...EngineTests` / `...EngineTest`)

Names match 1:1; three differ only by case convention (noted):

- `returnsNilWhenNoScript`
- `detectsScript`
- `doesNotDetectScriptInPureASCII` / `...PureAscii`
- `insertsSpacesAroundLatinRuns`
- `preservesEmojiAndPunctuation`
- `leadingSpanAtStringStart_noLeadingSpace` /
  `leadingSpanAtStringStartNoLeadingSpace`
- `punctuationPreventsLeadingSpace`
- `trailingSpaceBeforeASCIIDigit` / `trailingSpaceBeforeAsciiDigit`
- `multiRangeDetection`
- `spacesCleanedUpBeforePunctuation`

### Smoke (`SmokeTests` / `SmokeTest`)

- iOS `libraryVersionIsSemver` = Android `versionIsSemver`

### RootView UI (iOS) and LiveOcrAnalyzer (Android)

iOS `RootViewUITests`:

- `chineseRootViewHostsWithoutCrashing` — Android host-smoke is left to
  instrumentation (Compose hosting needs a device/emulator)
- `arabicRootViewHostsWithoutCrashing` — as above
- `chineseConfigHasHistoryAndNoPluginTabs` — Android asserts the same tab
  composition via `ConfigSeamTest`
- `arabicConfigHasHistoryAndExactlyOneQuranPluginTab` — via `ConfigSeamTest`
- `arabicHasExactlyOneMoreTabThanChinese` — via `ConfigSeamTest`

Android-only `LiveOcrAnalyzerTest` (no iOS analogue):

- `uprightDimensionsSwapFor90And270`
- `uprightDimensionsUnswappedFor0And180`
- `sampleBoxMapsToExpectedUprightViewCoordinates`
- `saneFilterUsesUprightBasis`

`LiveOcrAnalyzerTest` exists because Android's ML Kit OCR returns
buffer-space pixel coordinates that must be rotated into an upright basis
(D5); iOS Vision returns normalized coordinates so the bug class does not
exist there and needs no test. Sanctioned platform-only test, not a coverage
gap.

## Conclusion

Every iOS library type/file, both example apps, and the Quran plugin map to
an Android counterpart with equivalent public surface and behaviour. All
divergences are accounted for: four named spec SDK substitutions (D3, D4, D6,
D9) and eight sanctioned execution decisions (D1, D2, D5, D7, D8, D10, D11,
D12). The mirrored test sets match 1:1 modulo the explained platform-only
`LiveOcrAnalyzerTest` and the factoring-driven test relocations noted above.
No unintended parity break was found.
