# OCRService seam — design

## Problem

The OCR recognizer is hardcoded per platform: Apple Vision on iOS, Google
ML Kit on Android. ML Kit ships no on-device Arabic script model (still true
2026; upstream feature request `googlesamples/mlkit#674` open), so the
`arabic` recognizer falls back to the Latin model and produces garbage on
Android. Any Arabic-script app (e.g. Harakat Lens) is broken on Android by
construction. iOS Arabic works (Vision Revision 3, available since iOS 16;
library minimum is iOS 17).

There is no injection point for the recognition step today. `BiangBiangConfig`
exposes `Transliterator`, `FeaturePlugin`, `AudioProvider`,
`SettingDescriptor` — none touch OCR.

## Goal

Add a narrow, optional, symmetric `OCRService` seam so a consuming app can
supply its own recognizer (e.g. Tesseract4Android for Arabic) while the
built-in Vision / ML Kit path stays the default and unchanged when no
override is given.

Non-goals: changing throttle/debounce/spacing/transliteration/plugin
behaviour; abstracting frame capture or rotation; shipping a Tesseract
dependency in the core library.

## Decisions (locked during brainstorming)

- **Symmetric** — seam added on both iOS and Android (parity rule).
- **Seam + example adapter** — core library defines the seam only; the
  Arabic example app demonstrates a real Tesseract4Android adapter so the
  pattern is proven in CI.
- **Narrow, recognize-only** — the pipeline keeps frame capture, rotation
  normalization (`OcrRotation`), throttle, debounce, `TextProcessingEngine`,
  transliteration, plugin hooks. `OCRService` only replaces the raw
  recognize step.
- **Per-`LanguageProfile` optional override** — each profile may carry its
  own `ocrService`; unset profiles use the platform default.
- **Single async `recognize`** (approach A) — one method, sync/async
  agnostic, mirrors existing default code paths.

## Architecture

New protocol/interface alongside `Transliterator` / `AudioProvider`:

- iOS: `Sources/BiangBiangUI/Protocols/OCRService.swift`
- Android:
  `android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/protocols/OcrService.kt`

Optional override field on `LanguageProfile`:

- iOS: `let ocrService: (any OCRService)?` (default `nil`)
- Android: `val ocrService: OcrService? = null`

The existing built-in recognize logic is wrapped behind a private
`DefaultOCRService` / `DefaultOcrService` conforming to the same
protocol, so the pipeline always talks to one `OCRService`. Resolution per
active profile:

```text
resolvedService = profile.ocrService ?? DefaultOCRService()
```

When no override is present the behaviour is identical to today
(`DefaultOCRService` is a verbatim wrapper of the current code paths).

Note: `Protocols/OcrService.kt` collides in directory only — the existing
one-shot class is `services/camera/OcrService.kt` (`class OcrService`). The
new interface is `protocols/OcrService.kt` (`fun interface OcrService`).
The existing camera class is renamed to `StillImageOcr` to remove the
ambiguity.

## Interface and types

```text
OCRBox {
  text: String
  rect: image-space rectangle, top-left origin, pixel units
        (CGRect on iOS, android.graphics.Rect on Android)
}

iOS:
  protocol OCRService: Sendable {
    func recognize(_ image: CGImage,
                    recognizer: OCRRecognizer) async -> [OCRBox]
  }

Android:
  fun interface OcrService {
    suspend fun recognize(bitmap: Bitmap,
                           recognizer: OcrRecognizer): List<OcrBox>
  }
```

- `recognizer` is passed through so one impl can dispatch by script
  (`arabic` → Tesseract, otherwise delegate to the default).
- `image` is the **normalized still** the pipeline already produces. No
  `CMSampleBuffer` / `ImageProxy` is exposed — the rotation-drift fix stays
  library-owned by construction.
- Returned rects are in image-pixel space. The pipeline maps them to display
  space using the existing `OcrRotation` (Android) / Vision coordinate
  handling (iOS).

Coordinate-contract change (highest-risk touch point): the current iOS
`RecognizedTextBox` uses Vision's normalized `boundingBox` (0–1, bottom-left
origin). `OCRBox` standardizes on pixel / top-left. `DefaultOCRService` on
iOS converts Vision normalized → pixel rect so every service shares one
contract; the iOS overlay mapping is updated to consume pixel rects. A
golden regression test guards this conversion.

## Data flow

Only the raw recognize call changes; everything around it is untouched.

- **iOS live:** capture buffer → `CGImage` (orientation already handled) →
  `resolvedService.recognize(cgImage, recognizer:)` → `[OCRBox]` →
  `engine.process(box.text)` → `transliterationMap` → overlay (pixel rects).
- **iOS still:** `UIImage` → `CGImage` → same `recognize` call.
- **Android live:** `LiveOcrAnalyzer` → `Bitmap` + rotation →
  `OcrRotation.uprightSize()` (kept) →
  `resolvedService.recognize(bitmap, recognizer)` → boxes →
  `engine.process` → `OcrRotation.isSane` filter → overlay.
- **Android still:** one-shot path identical, via the renamed
  `StillImageOcr`.

Throttle (1 s live), debounce (0.8 s text), `TextProcessingEngine`,
transliteration, plugin hooks: unchanged, all remain in the pipeline.

Sync backends (Tesseract4Android blocks) wrap their work in
`withContext(Dispatchers.Default)` (Android) / a detached `Task`
(iOS) inside their `recognize` implementation.

## Default implementations and example adapter

- `DefaultOCRService` (iOS): wraps `VNRecognizeTextRequest` plus the current
  `recognitionLanguages(for:)` and `preferredArabicLanguages()`; converts
  Vision normalized box → pixel `OCRBox`.
- `DefaultOcrService` (Android): wraps `recognizerFor()` + ML Kit; converts
  the ML Kit upright box → `OcrBox`.
- Arabic example adapter (`android/examples/.../arabic`):
  `TesseractOcrService` using
  `cz.adaptech.tesseract4android:tesseract4android:4.9.0` (JitPack,
  Apache-2.0, minSdk 21 — compatible with library minSdk 26, fully
  offline). `ara.traineddata` shipped in the example's assets, copied to a
  private `tessdata` directory on first run. Dispatch:
  `recognizer == ARABIC` → Tesseract; otherwise delegate to
  `DefaultOcrService`. The iOS Arabic example keeps the Vision default
  (Arabic already works there); it only needs to compile against the new
  symmetric API.

This places a real Tesseract path in CI via the example build, matching the
existing example-config strategy (examples prove the seam and run in CI).

## Testing

- `FakeOCRService` returning fixed boxes → mirrored iOS/Android unit tests
  assert the pipeline transliterates, spaces, and filters identically.
- Default-resolution test: `nil` override resolves to `DefaultOCRService`;
  output matches a pre-change golden (regression guard for the iOS
  box-coordinate conversion).
- Android Arabic: covered by the example build; add a lightweight
  instrumented sanity test for traineddata load if feasible.

## Documentation

- `docs/parity.md`: add an `OCRService` row and a deliberate-difference
  entry (Arabic example: iOS Vision-default vs Android Tesseract).
- `docs/configuration.md`: document the `ocrService` field on
  `LanguageProfile`.
- `docs/ocr-languages.md`: re-add Arabic, noting Android support is
  available via an injectable `OCRService` (see the Arabic example).

## Branch

`feat/ocr-service-seam`. Per the user's decision, the in-flight
documentation edits (`docs/ocr-languages.md`, `docs/configuration.md`) and
this spec are committed on the same feature branch.
