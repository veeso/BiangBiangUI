# OCR language support

The OCR engine is **not injectable**. Each platform hardcodes its native
recognizer: Apple Vision on iOS, Google ML Kit on Android. The active engine
is selected per `LanguageProfile` via its `OCRRecognizer` value.

This page lists what each `OCRRecognizer` actually recognizes on each
platform, so config authors know which scripts work before shipping an app.

## Summary

| `OCRRecognizer` | iOS (Vision) | Android (ML Kit) |
| --------------- | ------------ | ---------------- |
| `chinese`       | ✅ native     | ✅ native         |
| `latin`         | ✅ native     | ✅ native         |
| `japanese`      | ✅ native     | ✅ native         |
| `korean`        | ✅ native     | ✅ native         |

All recognizers also pick up Latin characters in the same frame (digits,
punctuation, embedded ASCII), preserved in place by `TextProcessingEngine`.

## iOS — Apple Vision

`OCRCameraModel` uses `VNRecognizeTextRequest` at `.accurate` level.
`OCRRecognizer` maps to Vision language identifiers:

| `OCRRecognizer` | Vision languages     |
| --------------- | -------------------- |
| `chinese`       | `zh-Hans`, `zh-Hant` |
| `latin`         | `en-US`              |
| `japanese`      | `ja`                 |
| `korean`        | `ko`                 |

## Android — Google ML Kit

`OcrService` / `LiveOcrAnalyzer` use ML Kit on-device `TextRecognition` v2.
`OCRRecognizer` maps to a recognizer model:

| `OCRRecognizer` | ML Kit recognizer        |
| --------------- | ------------------------ |
| `chinese`       | `ChineseTextRecognizer`  |
| `latin`         | default Latin recognizer |
| `japanese`      | `JapaneseTextRecognizer` |
| `korean`        | `KoreanTextRecognizer`   |

Each non-Latin model also recognizes Latin script in the same image (per ML
Kit documentation).
