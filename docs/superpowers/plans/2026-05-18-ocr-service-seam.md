# OCRService Seam Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, symmetric, recognize-only `OCRService` seam so a
consuming app can supply its own OCR backend (e.g. Tesseract4Android for
Arabic) while the built-in Vision / ML Kit path stays the unchanged default.

**Architecture:** A new `OCRService` protocol/interface returns raw
`[OCRTextBox]` (text + image-pixel rect). The camera pipeline keeps frame
capture, rotation, throttle, debounce, `TextProcessingEngine`, and overlay
mapping; it only delegates the raw recognize step. Built-in behaviour is
wrapped in a private `DefaultOCRService`. An override is injected per
`LanguageProfile`.

**Tech Stack:** Swift 6 / SwiftUI / Vision / Swift Testing (iOS); Kotlin /
ML Kit / Tesseract4Android / JUnit (Android).

---

## File structure

### iOS

- Create `Sources/BiangBiangUI/Protocols/OCRService.swift` — protocol +
  `OCRTextBox`.
- Create `Sources/BiangBiangUI/Services/Camera/DefaultOCRService.swift` —
  Vision-backed default, normalized→pixel conversion.
- Modify `Sources/BiangBiangUI/Config/LanguageProfile.swift` — add optional
  `ocrService`.
- Modify `Sources/BiangBiangUI/Services/Camera/OCRCameraModel.swift` —
  resolve service, call it, drop the in-class Vision request.
- Test `Tests/BiangBiangUITests/OCRServiceSeamTests.swift`.

### Android

- Create
  `android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/protocols/OcrService.kt`
  — interface + `OcrTextBox`.
- Create
  `android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/services/camera/DefaultOcrService.kt`
  — ML Kit-backed default.
- Rename existing `class OcrService` →
  `class StillImageOcr` in
  `android/.../services/camera/OcrService.kt` (file renamed to
  `StillImageOcr.kt`).
- Modify
  `android/.../config/LanguageProfile.kt` — add optional `ocrService`.
- Modify `StillImageOcr.kt` and the `LiveOcrAnalyzer` (same file) — accept a
  resolved `OcrService`, normalize the live frame to an upright `Bitmap`.
- Test
  `android/biangbiang-ui/src/test/java/dev/veeso/biangbiangui/OcrServiceSeamTest.kt`.

### Arabic example

- Create
  `android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic/TesseractOcrService.kt`.
- Modify the Arabic example config to inject it; add `ara.traineddata` asset
  + Gradle dependency.

### Docs

- Modify `docs/parity.md`, `docs/configuration.md`,
  `docs/ocr-languages.md`.

---

## Task 1: iOS — `OCRService` protocol and `OCRTextBox`

**Files:**

- Create: `Sources/BiangBiangUI/Protocols/OCRService.swift`
- Test: `Tests/BiangBiangUITests/OCRServiceSeamTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import BiangBiangUI

struct OCRServiceSeamTests {
    struct StubService: OCRService {
        func recognize(_ image: CGImage,
                        recognizer: OCRRecognizer) async -> [OCRTextBox] {
            [OCRTextBox(text: "你好",
                        rect: CGRect(x: 1, y: 2, width: 3, height: 4))]
        }
    }

    @Test func stubServiceReturnsBox() async {
        let img = CGContext(
            data: nil, width: 1, height: 1, bitsPerComponent: 8,
            bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )!.makeImage()!
        let boxes = await StubService().recognize(img, recognizer: .chinese)
        #expect(boxes == [OCRTextBox(
            text: "你好", rect: CGRect(x: 1, y: 2, width: 3, height: 4))])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter OCRServiceSeamTests`
Expected: FAIL — `cannot find 'OCRService'` / `'OCRTextBox' in scope`.

- [ ] **Step 3: Write the protocol and type**

Create `Sources/BiangBiangUI/Protocols/OCRService.swift`:

```swift
import CoreGraphics

/// A recognised text run in image-pixel coordinates (top-left origin).
public struct OCRTextBox: Sendable, Equatable {
    public let text: String
    public let rect: CGRect

    public init(text: String, rect: CGRect) {
        self.text = text
        self.rect = rect
    }
}

/// Pluggable OCR backend. The library normalises the frame to a still
/// `CGImage`; the service only performs raw recognition and returns boxes in
/// image-pixel space. Rotation, throttle, debounce, spacing and
/// transliteration stay library-owned. Mirrors Android `OcrService`.
public protocol OCRService: Sendable {
    /// Recognise text in `image`. `recognizer` is the active profile's
    /// script hint so one implementation can dispatch by script.
    func recognize(_ image: CGImage,
                    recognizer: OCRRecognizer) async -> [OCRTextBox]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter OCRServiceSeamTests`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
swiftformat ./Sources ./Tests
git add Sources/BiangBiangUI/Protocols/OCRService.swift \
        Tests/BiangBiangUITests/OCRServiceSeamTests.swift
git commit -m "feat(ios): add OCRService protocol and OCRTextBox"
```

---

## Task 2: iOS — `DefaultOCRService` (Vision, normalized→pixel)

**Files:**

- Create: `Sources/BiangBiangUI/Services/Camera/DefaultOCRService.swift`
- Test: `Tests/BiangBiangUITests/OCRServiceSeamTests.swift` (append)

The current Vision logic lives in `OCRCameraModel.swift`:
`makeTextRecognitionRequest()` (lines ~379-406),
`recognitionLanguages(for:)` (lines ~411-424), `preferredArabicLanguages()`
(lines ~428-435). Move that logic verbatim into `DefaultOCRService`,
converting Vision's normalized bottom-left `boundingBox` to a top-left pixel
`CGRect`.

- [ ] **Step 1: Write the failing test (append to the struct)**

```swift
@Test func defaultServiceConvertsNormalizedToPixelTopLeft() {
    // Vision box: normalized, bottom-left origin.
    let normalized = CGRect(x: 0.25, y: 0.10, width: 0.5, height: 0.20)
    let pixel = DefaultOCRService.pixelRect(
        fromVisionNormalized: normalized,
        imageWidth: 200, imageHeight: 100)
    // x: 0.25*200=50  w: 0.5*200=100  h: 0.20*100=20
    // top-left y = (1 - 0.10 - 0.20) * 100 = 70
    #expect(pixel == CGRect(x: 50, y: 70, width: 100, height: 20))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter OCRServiceSeamTests`
Expected: FAIL — `cannot find 'DefaultOCRService'`.

- [ ] **Step 3: Implement `DefaultOCRService`**

Create `Sources/BiangBiangUI/Services/Camera/DefaultOCRService.swift`:

```swift
import CoreGraphics
import Vision

/// Built-in Vision-backed OCR. Used whenever a `LanguageProfile` does not
/// provide an `ocrService`. Behaviour is identical to the pre-seam
/// `OCRCameraModel` Vision path.
public struct DefaultOCRService: OCRService {
    public init() {}

    public func recognize(_ image: CGImage,
                           recognizer: OCRRecognizer) async -> [OCRTextBox] {
        await withCheckedContinuation { continuation in
            let request = VNRecognizeTextRequest { req, _ in
                let results =
                    (req.results as? [VNRecognizedTextObservation]) ?? []
                let boxes: [OCRTextBox] = results.compactMap { obs in
                    guard let top = obs.topCandidates(1).first else {
                        return nil
                    }
                    return OCRTextBox(
                        text: top.string,
                        rect: Self.pixelRect(
                            fromVisionNormalized: obs.boundingBox,
                            imageWidth: image.width,
                            imageHeight: image.height))
                }
                continuation.resume(returning: boxes)
            }
            request.recognitionLanguages =
                Self.recognitionLanguages(for: recognizer)
            request.recognitionLevel = .accurate
            let handler = VNImageRequestHandler(cgImage: image, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(returning: [])
            }
        }
    }

    /// Vision normalized (0–1, bottom-left) → image-pixel (top-left).
    static func pixelRect(fromVisionNormalized n: CGRect,
                          imageWidth: Int,
                          imageHeight: Int) -> CGRect {
        let w = CGFloat(imageWidth), h = CGFloat(imageHeight)
        return CGRect(
            x: n.minX * w,
            y: (1 - n.minY - n.height) * h,
            width: n.width * w,
            height: n.height * h)
    }

    static func recognitionLanguages(
        for recognizer: OCRRecognizer) -> [String] {
        switch recognizer {
        case .chinese: return ["zh-Hans", "zh-Hant"]
        case .latin: return ["en-US"]
        case .arabic: return preferredArabicLanguages()
        case .japanese: return ["ja"]
        case .korean: return ["ko"]
        }
    }

    static func preferredArabicLanguages() -> [String] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.revision = VNRecognizeTextRequestRevision3
        let supported =
            (try? request.supportedRecognitionLanguages()) ?? []
        let arabic = supported.filter { $0.hasPrefix("ar") }
        return arabic.isEmpty ? ["ar-SA", "ar"] : arabic
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter OCRServiceSeamTests`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
swiftformat ./Sources ./Tests
git add Sources/BiangBiangUI/Services/Camera/DefaultOCRService.swift \
        Tests/BiangBiangUITests/OCRServiceSeamTests.swift
git commit -m "feat(ios): add Vision-backed DefaultOCRService"
```

---

## Task 3: iOS — inject `ocrService` and wire the pipeline

**Files:**

- Modify: `Sources/BiangBiangUI/Config/LanguageProfile.swift`
- Modify: `Sources/BiangBiangUI/Services/Camera/OCRCameraModel.swift`
- Test: `Tests/BiangBiangUITests/OCRServiceSeamTests.swift` (append)

- [ ] **Step 1: Write the failing test (append)**

```swift
@Test func profileDefaultsToNilService() {
    let p = LanguageProfile(
        id: "zh", displayName: "Chinese",
        scriptRanges: [0x4E00...0x9FFF],
        ocrRecognizer: .chinese, variants: [])
    #expect(p.ocrService == nil)
}

@Test func resolvedServiceFallsBackToDefault() {
    let p = LanguageProfile(
        id: "zh", displayName: "Chinese",
        scriptRanges: [0x4E00...0x9FFF],
        ocrRecognizer: .chinese, variants: [])
    #expect(OCRCameraModel.resolveService(p) is DefaultOCRService)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter OCRServiceSeamTests`
Expected: FAIL — extra `ocrService:` argument / `resolveService` missing.

- [ ] **Step 3: Add the optional field**

In `Sources/BiangBiangUI/Config/LanguageProfile.swift`, add the stored
property and initializer parameter (defaulted so existing call sites keep
compiling):

```swift
public struct LanguageProfile: Sendable {
    public let id: String
    public let displayName: String
    public let scriptRanges: [ClosedRange<UInt32>]
    public let ocrRecognizer: OCRRecognizer
    public let variants: [LanguageVariant]
    /// Optional OCR backend override. `nil` uses the built-in Vision path.
    public let ocrService: (any OCRService)?

    public init(id: String, displayName: String,
                scriptRanges: [ClosedRange<UInt32>],
                ocrRecognizer: OCRRecognizer,
                variants: [LanguageVariant],
                ocrService: (any OCRService)? = nil) {
        self.id = id; self.displayName = displayName
        self.scriptRanges = scriptRanges
        self.ocrRecognizer = ocrRecognizer
        self.variants = variants
        self.ocrService = ocrService
    }
}
```

- [ ] **Step 4: Wire the pipeline in `OCRCameraModel`**

In `Sources/BiangBiangUI/Services/Camera/OCRCameraModel.swift`:

1. Add a resolver and stored service. After the `init` (line ~106) add:

```swift
static func resolveService(_ profile: LanguageProfile) -> any OCRService {
    profile.ocrService ?? DefaultOCRService()
}
```

2. In `init` (lines ~102-106) replace the `recognitionLanguages`
   computation with the resolved service and keep the recognizer:

```swift
public init(profile: LanguageProfile, engine: TextProcessingEngine) {
    self.profile = profile
    self.engine = engine
    self.service = Self.resolveService(profile)
    self.recognizer = profile.ocrRecognizer
}
```

   Add the stored properties next to `engine` (line ~75):

```swift
@ObservationIgnored private nonisolated let service: any OCRService
@ObservationIgnored private nonisolated let recognizer: OCRRecognizer
```

   Remove the now-unused `recognitionLanguages` stored property
   (line ~78) and `textRequest` (line ~89).

3. Replace the live path body of `captureOutput` (lines ~146-159) with a
   `CGImage` build + service call:

```swift
guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
      let cgImage = Self.cgImage(from: pixelBuffer) else { return }
let service = self.service
let recognizer = self.recognizer
let engine = self.engine
Task { @MainActor in
    let boxes = await service.recognize(cgImage, recognizer: recognizer)
    self.applyRecognized(boxes, engine: engine)
}
```

4. Add the pixel-buffer → `CGImage` helper and the apply helper. Place
   near `recognizeText(from:)` (line ~437):

```swift
nonisolated static func cgImage(
    from pixelBuffer: CVPixelBuffer) -> CGImage? {
    let ci = CIImage(cvPixelBuffer: pixelBuffer)
    return CIContext().createCGImage(ci, from: ci.extent)
}

@MainActor
func applyRecognized(_ boxes: [OCRTextBox],
                     engine: TextProcessingEngine) {
    recognizedTexts = boxes.map {
        RecognizedTextBox(text: $0.text, pixelRect: $0.rect)
    }
    transliterationMap.removeAll(keepingCapacity: true)
    for box in recognizedTexts {
        if let processed = engine.process(box.text) {
            transliterationMap[box.id] = processed
        }
    }
}
```

5. Update `recognizeText(from:)` (lines ~437-446) to use the service:

```swift
private func recognizeText(from image: UIImage) {
    guard let cgImage = image.cgImage else { return }
    let service = self.service
    let recognizer = self.recognizer
    let engine = self.engine
    Task { @MainActor in
        let boxes = await service.recognize(cgImage, recognizer: recognizer)
        self.applyRecognized(boxes, engine: engine)
    }
}
```

6. Delete `makeTextRecognitionRequest()`, `recognitionLanguages(for:)` and
   `preferredArabicLanguages()` from `OCRCameraModel` (now in
   `DefaultOCRService`). Change `RecognizedTextBox` to store a pixel rect:
   find its definition (search `struct RecognizedTextBox`) and replace its
   `boundingBox: CGRect` (Vision-normalized) with `pixelRect: CGRect`;
   update the overlay view that consumes it (search
   `RecognizedTextOverlay`) to map `pixelRect` from image space to the
   view using the existing aspect-fit transform instead of the
   Vision-normalized flip. Keep the `id` property unchanged.

- [ ] **Step 5: Run the full iOS suite (regression guard)**

Run: `swift test`
Expected: PASS, including existing `RootViewUITests`, `SmokeTests`,
`ConfigSeamTests` and the new `OCRServiceSeamTests`.

- [ ] **Step 6: Format and commit**

```bash
swiftformat ./Sources ./Tests
git add Sources/BiangBiangUI/Config/LanguageProfile.swift \
        Sources/BiangBiangUI/Services/Camera/OCRCameraModel.swift \
        Tests/BiangBiangUITests/OCRServiceSeamTests.swift
git commit -m "feat(ios): inject OCRService per LanguageProfile, pixel-rect overlay"
```

---

## Task 4: Android — `OcrService` interface, `OcrTextBox`, rename collision

**Files:**

- Create:
  `android/.../protocols/OcrService.kt`
- Rename: `android/.../services/camera/OcrService.kt` →
  `StillImageOcr.kt` (`class OcrService` → `class StillImageOcr`)
- Test:
  `android/.../src/test/java/dev/veeso/biangbiangui/OcrServiceSeamTest.kt`

- [ ] **Step 1: Write the failing test**

Create
`android/biangbiang-ui/src/test/java/dev/veeso/biangbiangui/OcrServiceSeamTest.kt`:

```kotlin
package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrServiceSeamTest {
    @Test
    fun stubServiceReturnsBox() = runBlocking {
        val svc = OcrService { _, _ ->
            listOf(OcrTextBox("你好", 1, 2, 3, 4))
        }
        val boxes = svc.recognize(null, OcrRecognizer.CHINESE)
        assertEquals(listOf(OcrTextBox("你好", 1, 2, 3, 4)), boxes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
`cd android && ./gradlew test --tests "dev.veeso.biangbiangui.OcrServiceSeamTest"`
Expected: FAIL — unresolved `OcrService` / `OcrTextBox` in `protocols`.

- [ ] **Step 3: Create the interface and type**

Create
`android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/protocols/OcrService.kt`:

```kotlin
package dev.veeso.biangbiangui.protocols

import android.graphics.Bitmap
import dev.veeso.biangbiangui.config.OcrRecognizer

/** A recognised text run in image-pixel coordinates (top-left origin). */
data class OcrTextBox(
    val text: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Pluggable OCR backend. The library normalises the frame to an upright
 * still [Bitmap]; the service only performs raw recognition and returns
 * boxes in image-pixel space. Rotation, throttle, debounce, spacing and
 * transliteration stay library-owned. Mirrors iOS `OCRService`.
 */
fun interface OcrService {
    suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox>
}
```

(`bitmap` is nullable only so unit tests can pass `null`; production code
always passes a real bitmap.)

- [ ] **Step 4: Rename the existing camera class to free the name**

`git mv` the file and rename the class:

```bash
git mv \
 android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/services/camera/OcrService.kt \
 android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/services/camera/StillImageOcr.kt
```

In `StillImageOcr.kt` rename `class OcrService(` → `class StillImageOcr(`.
Then update every reference: search the tree for `OcrService(` and the
`import ...services.camera.OcrService` (the camera screen / model
constructs it) and rename to `StillImageOcr`. Do **not** touch
`recognizerFor`, `OcrBox`, or `LiveOcrAnalyzer` yet.

- [ ] **Step 5: Run test to verify it passes and project compiles**

Run:
`cd android && ./gradlew test --tests "dev.veeso.biangbiangui.OcrServiceSeamTest"`
Expected: PASS. Then `./gradlew :biangbiang-ui:assembleRelease` — BUILD
SUCCESSFUL (rename fully propagated).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(android): add OcrService interface; rename camera OcrService -> StillImageOcr"
```

---

## Task 5: Android — `DefaultOcrService` (ML Kit)

**Files:**

- Create: `android/.../services/camera/DefaultOcrService.kt`
- Test: `OcrServiceSeamTest.kt` (append a pure mapping test)

`DefaultOcrService` wraps `recognizerFor(...)` (still in `StillImageOcr.kt`)
and ML Kit. ML Kit boxes from `InputImage.fromBitmap(bitmap, 0)` are already
in bitmap-pixel space, so no coordinate conversion is needed.

- [ ] **Step 1: Write the failing test (append)**

```kotlin
@Test
fun mapsMlKitElementToTextBox() {
    val box = DefaultOcrService.toTextBox(
        text = "和",
        left = 5, top = 6, right = 25, bottom = 26)
    assertEquals(OcrTextBox("和", 5, 6, 20, 20), box)
}
```

Add imports: `import dev.veeso.biangbiangui.services.camera.DefaultOcrService`.

- [ ] **Step 2: Run test to verify it fails**

Run:
`cd android && ./gradlew test --tests "dev.veeso.biangbiangui.OcrServiceSeamTest"`
Expected: FAIL — unresolved `DefaultOcrService`.

- [ ] **Step 3: Implement `DefaultOcrService`**

Create
`android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/services/camera/DefaultOcrService.kt`:

```kotlin
package dev.veeso.biangbiangui.services.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import kotlinx.coroutines.tasks.await

/**
 * Built-in ML Kit-backed OCR. Used whenever a `LanguageProfile` does not
 * provide an `ocrService`. Behaviour matches the pre-seam recognize path.
 */
class DefaultOcrService : OcrService {
    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        val bmp = bitmap ?: return emptyList()
        val image = InputImage.fromBitmap(bmp, 0)
        val result = recognizerFor(recognizer).process(image).await()
        return result.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { el ->
                el.boundingBox?.let { b ->
                    toTextBox(el.text, b.left, b.top, b.right, b.bottom)
                }
            }
    }

    companion object {
        fun toTextBox(
            text: String,
            left: Int, top: Int, right: Int, bottom: Int,
        ): OcrTextBox =
            OcrTextBox(text, left, top, right - left, bottom - top)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
`cd android && ./gradlew test --tests "dev.veeso.biangbiangui.OcrServiceSeamTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/biangbiang-ui/src/main/java/dev/veeso/biangbiangui/services/camera/DefaultOcrService.kt \
        android/biangbiang-ui/src/test/java/dev/veeso/biangbiangui/OcrServiceSeamTest.kt
git commit -m "feat(android): add ML Kit-backed DefaultOcrService"
```

---

## Task 6: Android — inject `ocrService` and wire the pipeline

**Files:**

- Modify: `android/.../config/LanguageProfile.kt`
- Modify: `android/.../services/camera/StillImageOcr.kt` (the file holding
  `StillImageOcr` and `LiveOcrAnalyzer`)
- Test: `OcrServiceSeamTest.kt` (append)

The live analyzer must hand the service an **upright Bitmap** so the seam
contract (image-pixel space) holds and `OcrRotation` stays library-owned.

- [ ] **Step 1: Write the failing test (append)**

```kotlin
@Test
fun profileDefaultsToNullServiceAndResolvesToDefault() {
    val p = dev.veeso.biangbiangui.config.LanguageProfile(
        id = "zh", displayName = "Chinese",
        scriptRanges = listOf(0x4E00u..0x9FFFu),
        ocrRecognizer = OcrRecognizer.CHINESE,
        variants = emptyList())
    assertEquals(null, p.ocrService)
    val resolved = dev.veeso.biangbiangui.services.camera
        .resolveOcrService(p)
    assert(resolved is DefaultOcrService)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
`cd android && ./gradlew test --tests "dev.veeso.biangbiangui.OcrServiceSeamTest"`
Expected: FAIL — no `ocrService` param / `resolveOcrService` unresolved.

- [ ] **Step 3: Add the optional field**

In `android/.../config/LanguageProfile.kt`:

```kotlin
import dev.veeso.biangbiangui.protocols.OcrService

data class LanguageProfile(
    val id: String,
    val displayName: String,
    val scriptRanges: List<UIntRange>,
    val ocrRecognizer: OcrRecognizer,
    val variants: List<LanguageVariant>,
    /** Optional OCR backend override. `null` uses the built-in ML Kit path. */
    val ocrService: OcrService? = null,
)
```

- [ ] **Step 4: Add the resolver and rewire**

In `StillImageOcr.kt`, add a top-level resolver:

```kotlin
fun resolveOcrService(profile: LanguageProfile): OcrService =
    profile.ocrService ?: DefaultOcrService()
```

Change `StillImageOcr` and `LiveOcrAnalyzer` to take a resolved
`OcrService` and the recognizer, instead of building one via
`recognizerFor`:

```kotlin
class StillImageOcr(
    private val service: OcrService,
    private val recognizer: OcrRecognizer,
    private val engine: TextProcessingEngine,
) {
    suspend fun recognize(bitmap: Bitmap): List<OcrBox> {
        val upright = OcrRotation.UprightSize(bitmap.width, bitmap.height)
        return service.recognize(bitmap, recognizer)
            .mapNotNull { tb ->
                val tl = engine.process(tb.text) ?: return@mapNotNull null
                OcrBox(tb.text, tl, tb.left, tb.top, tb.width, tb.height)
            }
            .filter {
                OcrRotation.isSane(
                    OcrRotation.Box(it.left, it.top, it.width, it.height),
                    upright)
            }
    }
}
```

For `LiveOcrAnalyzer`, normalise the frame to an upright bitmap before
calling the service (this keeps the single correct rotation in the library):

```kotlin
class LiveOcrAnalyzer(
    private val service: OcrService,
    private val recognizer: OcrRecognizer,
    private val engine: TextProcessingEngine,
    private val throttleMs: Long = 1000L,
    private val onResult: (List<OcrBox>, Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastProcessedTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttleMs) {
            imageProxy.close(); return
        }
        lastProcessedTime = now
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close(); return
        }
        val deg = imageProxy.imageInfo.rotationDegrees
        val upright = OcrRotation.uprightSize(
            bufferWidth = mediaImage.width,
            bufferHeight = mediaImage.height,
            rotationDegrees = deg)
        val bitmap = OcrRotation.toUprightBitmap(imageProxy, deg)
        imageProxy.close()
        scope.launch {
            val boxes = service.recognize(bitmap, recognizer)
                .mapNotNull { tb ->
                    val tl = engine.process(tb.text)
                        ?: return@mapNotNull null
                    OcrBox(tb.text, tl, tb.left, tb.top,
                           tb.width, tb.height)
                }
                .filter {
                    OcrRotation.isSane(
                        OcrRotation.Box(
                            it.left, it.top, it.width, it.height),
                        upright)
                }
            onResult(boxes, upright.width, upright.height)
        }
    }
}
```

Add to `OcrRotation.kt` a single correct `ImageProxy` → upright `Bitmap`
helper (YUV→`Bitmap` then rotate by `rotationDegrees`), keeping all rotation
math in the one file:

```kotlin
@androidx.camera.core.ExperimentalGetImage
fun toUprightBitmap(
    proxy: androidx.camera.core.ImageProxy,
    rotationDegrees: Int,
): android.graphics.Bitmap {
    val nv21 = yuv420ToNv21(proxy)
    val yuv = android.graphics.YuvImage(
        nv21, android.graphics.ImageFormat.NV21,
        proxy.width, proxy.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuv.compressToJpeg(
        android.graphics.Rect(0, 0, proxy.width, proxy.height), 100, out)
    val bytes = out.toByteArray()
    val raw = android.graphics.BitmapFactory
        .decodeByteArray(bytes, 0, bytes.size)
    if (rotationDegrees == 0) return raw
    val m = android.graphics.Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return android.graphics.Bitmap.createBitmap(
        raw, 0, 0, raw.width, raw.height, m, true)
}

private fun yuv420ToNv21(
    proxy: androidx.camera.core.ImageProxy): ByteArray {
    val y = proxy.planes[0].buffer
    val u = proxy.planes[1].buffer
    val v = proxy.planes[2].buffer
    val ySize = y.remaining()
    val uSize = u.remaining()
    val vSize = v.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    y.get(nv21, 0, ySize)
    v.get(nv21, ySize, vSize)
    u.get(nv21, ySize + vSize, uSize)
    return nv21
}
```

Update the camera screen/model that constructs `StillImageOcr` /
`LiveOcrAnalyzer` (search `LiveOcrAnalyzer(` and `StillImageOcr(`): build
`val service = resolveOcrService(profile)` and pass
`service, profile.ocrRecognizer, engine, ...`.

- [ ] **Step 5: Run unit suite + build (regression guard)**

Run: `cd android && ./gradlew test && ./gradlew :biangbiang-ui:assembleRelease`
Expected: PASS / BUILD SUCCESSFUL, including existing `LiveOcrAnalyzerTest`,
`ConfigSeamTest`, `SmokeTest`. If `LiveOcrAnalyzerTest` constructs the old
signature, update its construction to the new
`(service, recognizer, engine, throttleMs, onResult)` form using
`DefaultOcrService()`.

- [ ] **Step 6: Lint and commit**

```bash
cd android && ./gradlew lintDebug && cd ..
git add -A
git commit -m "feat(android): inject OcrService per LanguageProfile; upright-bitmap live path"
```

---

## Task 7: Arabic example — Tesseract4Android adapter

**Files:**

- Modify: `android/examples/build.gradle.kts` (add JitPack dep + ensure
  `jitpack` repo in settings)
- Create:
  `android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic/TesseractOcrService.kt`
- Add asset:
  `android/examples/src/main/assets/tessdata/ara.traineddata`
- Modify: the Arabic example config under
  `android/examples/.../examples/arabic/` to pass
  `ocrService = TesseractOcrService(context)` on the Arabic
  `LanguageProfile`.

- [ ] **Step 1: Add the dependency**

In `android/examples/build.gradle.kts` `dependencies { }`:

```kotlin
implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
```

Ensure `dependencyResolutionManagement.repositories` (root
`settings.gradle.kts`) includes `maven { url = uri("https://jitpack.io") }`.

- [ ] **Step 2: Add the traineddata asset**

Download `ara.traineddata` (Tesseract `tessdata_best`, Apache-2.0) into
`android/examples/src/main/assets/tessdata/ara.traineddata`:

```bash
mkdir -p android/examples/src/main/assets/tessdata
curl -L -o android/examples/src/main/assets/tessdata/ara.traineddata \
  https://github.com/tesseract-ocr/tessdata_best/raw/main/ara.traineddata
```

- [ ] **Step 3: Implement the adapter**

Create
`android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic/TesseractOcrService.kt`:

```kotlin
package dev.veeso.biangbiangui.examples.arabic

import android.content.Context
import android.graphics.Bitmap
import cz.adaptech.tesseract4android.TessBaseAPI
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import dev.veeso.biangbiangui.services.camera.DefaultOcrService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Arabic OCR via Tesseract4Android. ML Kit ships no Arabic script model, so
 * the Arabic example plugs this in through the library's OcrService seam.
 * Non-Arabic recognizers delegate to the built-in ML Kit default.
 */
class TesseractOcrService(context: Context) : OcrService {
    private val fallback = DefaultOcrService()
    private val dataParent: File =
        File(context.filesDir, "tess").apply { mkdirs() }

    init {
        // Copy bundled ara.traineddata into tessdata/ on first run.
        val tessdata = File(dataParent, "tessdata").apply { mkdirs() }
        val target = File(tessdata, "ara.traineddata")
        if (!target.exists()) {
            context.assets.open("tessdata/ara.traineddata").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        if (recognizer != OcrRecognizer.ARABIC || bitmap == null) {
            return fallback.recognize(bitmap, recognizer)
        }
        return withContext(Dispatchers.Default) {
            val api = TessBaseAPI()
            try {
                api.init(dataParent.absolutePath, "ara")
                api.setImage(bitmap)
                api.getUTF8Text() // forces recognition
                val out = mutableListOf<OcrTextBox>()
                val it = api.resultIterator
                it.begin()
                do {
                    val text = it.getUTF8Text(
                        TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    val r = it.getBoundingRect(
                        TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    if (!text.isNullOrBlank() && r != null) {
                        out += OcrTextBox(
                            text.trim(), r.left, r.top,
                            r.width(), r.height())
                    }
                } while (it.next(
                        TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                out
            } finally {
                api.recycle()
            }
        }
    }
}
```

- [ ] **Step 4: Inject it into the Arabic example config**

In the Arabic example config file (search
`android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic`
for the `LanguageProfile(` whose `ocrRecognizer = OcrRecognizer.ARABIC`),
add `ocrService = TesseractOcrService(context)` to that profile. The
example config factory already takes a `Context` (per
`docs/configuration.md`), so thread it through.

- [ ] **Step 5: Build the examples (CI parity guard)**

Run:
`cd android && ./gradlew :examples:assembleDebug && cd ..`
Expected: BUILD SUCCESSFUL — proves the seam + Tesseract adapter compile
and the asset is packaged.

- [ ] **Step 6: Commit**

```bash
git add android/examples/build.gradle.kts settings.gradle.kts \
        android/examples/src/main/assets/tessdata/ara.traineddata \
        android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic
git commit -m "feat(example): Arabic Android example uses Tesseract4Android OcrService"
```

---

## Task 8: Documentation

**Files:**

- Modify: `docs/parity.md`
- Modify: `docs/configuration.md`
- Modify: `docs/ocr-languages.md`

- [ ] **Step 1: parity.md**

Add a mapping row (in the type/file mapping table) for
`OCRService` ↔ `OcrService` and a deliberate-difference entry: "Arabic
example: iOS uses the Vision default; Android injects a Tesseract4Android
`OcrService` (ML Kit has no Arabic model)."

- [ ] **Step 2: configuration.md**

In the `LanguageProfile` section table add a row:
`ocrService` | `OCRService?` / `OcrService?` | optional OCR backend
override; `nil`/`null` uses the built-in Vision / ML Kit path. Add a short
prose paragraph and a one-line code example showing a custom service passed
to a profile.

- [ ] **Step 3: ocr-languages.md**

Re-add an `arabic` row: iOS `✅ native (Vision R3)`; Android
`✅ via OCRService (see Arabic example)`. Add a short "Custom OCR backends"
section linking the Arabic example and noting the seam is the supported way
to add scripts the platform default lacks.

- [ ] **Step 4: Lint markdown tables and commit**

```bash
uvx fmt-md-tables -i docs/parity.md docs/configuration.md docs/ocr-languages.md
git add docs/parity.md docs/configuration.md docs/ocr-languages.md
git commit -m "docs: document OCRService seam and Android Arabic support"
```

---

## Final verification

- [ ] iOS: `swift test` — all green; `swiftformat ./Sources ./Tests` clean.
- [ ] Android: `cd android && ./gradlew test lintDebug :biangbiang-ui:assembleRelease :examples:assembleDebug`
      — all green.
- [ ] Confirm no `LanguageProfile` call site broke (defaulted params keep
      existing example configs compiling on both platforms).
- [ ] Open PR from `feat/ocr-service-seam` (do not direct-merge to `main`).
