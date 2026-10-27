@testable import BiangBiangUI
import Testing
import UIKit

@MainActor
struct CameraStillSessionTests {
    @Test func endedSessionRejectsPublicGalleryInput() {
        let (camera, _) = model()
        camera.endRecognitionSession()
        camera.recognizeGalleryImage(image())
        #expect(camera.capturedImage == nil)
    }

    @Test func delayedGalleryLoadCannotSetImageAfterReentry() async {
        let (camera, _) = model()
        let revision = camera.beginRecognitionSession()
        let gate = ImageGate()
        let task = Task {
            await camera.loadGalleryImage(sessionRevision: revision) { await gate.load() }
        }
        await gate.waitUntilLoading()
        camera.endRecognitionSession()
        camera.beginRecognitionSession()
        gate.complete(image())
        await task.value
        #expect(camera.capturedImage == nil)
        #expect(camera.recognizedTexts.isEmpty)
        #expect(camera.sessionLog.entries.isEmpty)
    }

    @Test func delayedStillOCRCannotPublishAfterReentryAndCurrentRequestSucceeds() async {
        let (camera, service) = model()
        let first = camera.beginRecognitionSession()
        camera.recognizeGalleryImage(image(), sessionRevision: first)
        await service.waitUntilLoading()
        camera.endRecognitionSession()
        camera.deleteCapturedImage()
        let current = camera.beginRecognitionSession()
        await service.complete()
        for _ in 0 ..< 30 {
            await Task.yield()
        }
        #expect(camera.capturedImage == nil)
        #expect(camera.recognizedTexts.isEmpty)
        #expect(camera.sessionLog.entries.isEmpty)
        // Photo delivery uses this same accepted-image path.
        camera.recognizeGalleryImage(image(), sessionRevision: first)
        #expect(camera.capturedImage == nil)
        camera.recognizeGalleryImage(image(), sessionRevision: current)
        await service.waitUntilLoading()
        await service.complete()
        for _ in 0 ..< 30 {
            await Task.yield()
        }
        #expect(camera.capturedImage != nil)
        #expect(camera.recognizedTexts.map(\.text) == ["你"])
        #expect(camera.sessionLog.entries.map(\.original) == ["你"])
        #expect(camera.transliterationMap.values.first == "ni")
    }

    private func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 20, height: 20)).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 20))
        }
    }

    private func model() -> (OCRCameraModel, DelayedOCR) {
        struct Latin: Transliterator {
            func transliterate(_: String) -> String {
                "ni"
            }
        }
        let service = DelayedOCR()
        let profile = LanguageProfile(id: "zh", displayName: "Chinese",
                                      scriptRanges: [0x4E00 ... 0x9FFF], ocrRecognizer: .chinese,
                                      variants: [], ocrService: service)
        return (OCRCameraModel(profile: profile, engine: TextProcessingEngine(
            scriptRanges: profile.scriptRanges, transliterator: Latin()
        )), service)
    }
}

@MainActor
private final class ImageGate {
    private var continuation: CheckedContinuation<UIImage?, Never>?
    private var loading: CheckedContinuation<Void, Never>?
    func load() async -> UIImage? {
        await withCheckedContinuation {
            continuation = $0
            loading?.resume()
            loading = nil
        }
    }

    func waitUntilLoading() async {
        if continuation != nil {
            return
        }
        await withCheckedContinuation { loading = $0 }
    }

    func complete(_ image: UIImage) {
        continuation?.resume(returning: image); continuation = nil
    }
}

private actor DelayedOCR: OCRService {
    private var continuation: CheckedContinuation<[OCRTextBox], Never>?
    private var loading: CheckedContinuation<Void, Never>?
    func recognize(_: CGImage, recognizer _: OCRRecognizer) async -> [OCRTextBox] {
        await withCheckedContinuation {
            continuation = $0
            loading?.resume()
            loading = nil
        }
    }

    func waitUntilLoading() async {
        if continuation != nil {
            return
        }
        await withCheckedContinuation { loading = $0 }
    }

    func complete() {
        continuation?.resume(returning: [OCRTextBox(text: "你", rect: .zero)])
        continuation = nil
    }
}
