# Plugging a custom OCR backend

By default the library recognises text with Apple Vision (iOS) and Google
ML Kit (Android). When the platform engine cannot read a script — for
example Arabic on Android, which ML Kit has no model for — supply your own
OCR backend through the `ocrService` seam on a `LanguageProfile`.

The seam is **recognise-only**. Your service receives one upright,
normalised still image plus the active script hint, and returns the
recognised text runs in image-pixel coordinates (top-left origin). The
library still owns frame capture, rotation, throttling, debouncing,
transliteration, and the result overlay — you do not touch any of that.

## The interface

iOS:

```swift
public protocol OCRService: Sendable {
    func recognize(_ image: CGImage,
                    recognizer: OCRRecognizer) async -> [OCRTextBox]
}

public struct OCRTextBox: Sendable, Equatable {
    public let text: String
    public let rect: CGRect   // image-pixel space, top-left origin
}
```

Android:

```kotlin
fun interface OcrService {
    suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox>
}

data class OcrTextBox(
    val text: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,   // image-pixel space, top-left origin
)
```

`recognizer` is the active profile's script hint, so one implementation can
dispatch by script and delegate everything else to the built-in default
(`DefaultOCRService` / `DefaultOcrService`).

## Wiring it in

Pass your service as the optional `ocrService` on the `LanguageProfile`.
`nil` / `null` keeps the built-in Vision / ML Kit path.

iOS:

```swift
LanguageProfile(
    id: "ar",
    displayName: "Arabic",
    scriptRanges: [0x0600 ... 0x06FF],
    ocrRecognizer: .arabic,
    variants: [...],
    ocrService: MyArabicOCRService()   // omit for the Vision default
)
```

Android:

```kotlin
LanguageProfile(
    id = "ar",
    displayName = "Arabic",
    scriptRanges = listOf(0x0600u..0x06FFu),
    ocrRecognizer = OcrRecognizer.ARABIC,
    variants = listOf(...),
    ocrService = TesseractOcrService(context),   // omit for the ML Kit default
)
```

## Minimal implementation

A custom service that handles one script and delegates the rest to the
built-in default.

Swift:

```swift
struct MyOCRService: OCRService {
    private let fallback = DefaultOCRService()

    func recognize(_ image: CGImage,
                    recognizer: OCRRecognizer) async -> [OCRTextBox] {
        guard recognizer == .arabic else {
            return await fallback.recognize(image, recognizer: recognizer)
        }
        let runs = MyEngine.read(image)            // your OCR call
        return runs.map { OCRTextBox(text: $0.text, rect: $0.pixelRect) }
    }
}
```

Kotlin:

```kotlin
class MyOcrService : OcrService {
    private val fallback = DefaultOcrService()

    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        if (recognizer != OcrRecognizer.ARABIC || bitmap == null) {
            return fallback.recognize(bitmap, recognizer)
        }
        return MyEngine.read(bitmap).map {           // your OCR call
            OcrTextBox(it.text, it.left, it.top, it.width, it.height)
        }
    }
}
```

## A real example

The Android Arabic example ships a working `TesseractOcrService`
(Tesseract4Android, with a bundled `ara.traineddata`). It runs Tesseract
for `ARABIC` and delegates every other script to the built-in
`DefaultOcrService`. See
`android/examples/src/main/java/dev/veeso/biangbiangui/examples/arabic/TesseractOcrService.kt`.

## Contract notes

- Return boxes in **image-pixel** coordinates, top-left origin. Do not
  apply rotation — the library hands you an already-upright image.
- Android: return an empty list when `bitmap` is `null` (a unit-test stub
  case; production callers always pass a real bitmap).
- Recognition failure may throw; the library treats it the same as the
  built-in path.
- Keep the call cancellation-friendly; the live path is throttled to one
  frame per second.

## See also

- [Configuration: `ocrService` field](./configuration.md#languageprofile)
- [OCR language support](./ocr-languages.md)
