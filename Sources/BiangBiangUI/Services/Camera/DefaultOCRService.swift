import CoreGraphics
import Vision

/// Built-in Vision-backed OCR. Used whenever a `LanguageProfile` does not
/// provide an `ocrService`. Behaviour is identical to the pre-seam
/// `OCRCameraModel` Vision path.
public struct DefaultOCRService: OCRService {
    public init() {}

    public func recognize(_ image: CGImage,
                          recognizer: OCRRecognizer) async -> [OCRTextBox]
    {
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
                            imageHeight: image.height
                        )
                    )
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
                          imageHeight: Int) -> CGRect
    {
        let w = CGFloat(imageWidth), h = CGFloat(imageHeight)
        return CGRect(
            x: n.minX * w,
            y: (1 - n.minY - n.height) * h,
            width: n.width * w,
            height: n.height * h
        )
    }

    static func recognitionLanguages(
        for recognizer: OCRRecognizer
    ) -> [String] {
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
