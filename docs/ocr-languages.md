# OCR language support

Each platform uses its native recognizer by default: Apple Vision on iOS,
Google ML Kit on Android. The active engine is selected per `LanguageProfile`
via its `OCRRecognizer` value. A profile may also supply a custom
`ocrService` to override the default for scripts the platform engine does not
support — see [Custom OCR backends](#custom-ocr-backends) and
[Configuration](./configuration.md).

This page lists what each `OCRRecognizer` actually recognizes on each
platform, so config authors know which scripts work before shipping an app.

## Summary

| `OCRRecognizer` | iOS (Vision)         | Android (ML Kit)             |
| --------------- | -------------------- | ---------------------------- |
| `chinese`       | ✅ native             | ✅ native                     |
| `latin`         | ✅ native             | ✅ native                     |
| `arabic`        | ✅ native (Vision R3) | ✅ via OcrService (Tesseract) |
| `japanese`      | ✅ native             | ✅ native                     |
| `korean`        | ✅ native             | ✅ native                     |

All recognizers also pick up Latin characters in the same frame (digits,
punctuation, embedded ASCII), preserved in place by `TextProcessingEngine`.

## iOS — Apple Vision

`OCRCameraModel` uses `VNRecognizeTextRequest` at `.accurate` level.
`OCRRecognizer` maps to Vision language identifiers:

| `OCRRecognizer` | Vision languages     |
| --------------- | -------------------- |
| `chinese`       | `zh-Hans`, `zh-Hant` |
| `latin`         | `en-US`              |
| `arabic`        | `ar`                 |
| `japanese`      | `ja`                 |
| `korean`        | `ko`                 |

## Android — Google ML Kit

`StillImageOcr` / `LiveOcrAnalyzer` use ML Kit on-device `TextRecognition`
v2. `OcrRecognizer` maps to a recognizer model when no custom `ocrService`
is set:

| `OcrRecognizer` | ML Kit recognizer                       |
| --------------- | --------------------------------------- |
| `chinese`       | `ChineseTextRecognizer`                 |
| `latin`         | default Latin recognizer                |
| `arabic`        | no ML Kit model — requires `ocrService` |
| `japanese`      | `JapaneseTextRecognizer`                |
| `korean`        | `KoreanTextRecognizer`                  |

Each non-Latin model also recognizes Latin script in the same image (per ML
Kit documentation).

## Custom OCR backends

The OCR engine is normally the platform default (Apple Vision on iOS, Google
ML Kit on Android). A `LanguageProfile` may supply an `ocrService` to
override it for scripts the platform default does not support.

This is the supported way to extend OCR to unsupported scripts. The service
receives the platform-normalised still image and the profile's
`ocrRecognizer` as a script hint, and returns raw `OCRTextBox` /
`OcrTextBox` values in image-pixel space. The pipeline continues to own
rotation, throttle, debounce, spacing, and transliteration.

**Example — Arabic on Android.** ML Kit ships no Arabic model. The Android
Arabic example injects a `TesseractOcrService` backed by Tesseract4Android
with a bundled `ara.traineddata` asset on the Arabic `LanguageProfile`.
iOS Arabic uses Vision natively (Vision R3 supports Arabic), so its
`ocrService` is `nil`.

See [Configuration: ocrService field](./configuration.md#languageprofile)
for the field reference and code examples.
