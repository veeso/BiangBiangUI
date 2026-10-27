@testable import BiangBiangUI
import CoreGraphics
import Testing

struct CameraOrientationTests {
    @Test func landscapeCoordinatesUsePaneBounds() {
        let result = OCRCameraModel.landscapeAspectFitRect(
            CGRect(x: 200, y: 100, width: 400, height: 80),
            imageSize: CGSize(width: 1920, height: 1080),
            viewSize: CGSize(width: 512, height: 744)
        )
        #expect(abs(result.minX - 53.333333) < 0.001)
        #expect(abs(result.minY - 254.666667) < 0.001)
        #expect(abs(result.width - 106.666667) < 0.001)
        #expect(abs(result.height - 21.333333) < 0.001)
    }

    @MainActor @Test func ignoresResultsFromOldOrientation() {
        struct Identity: Transliterator {
            func transliterate(_ text: String) -> String {
                text
            }
        }
        let engine = TextProcessingEngine(
            scriptRanges: [0x4E00 ... 0x9FFF], transliterator: Identity()
        )
        let profile = LanguageProfile(
            id: "zh",
            displayName: "Chinese",
            scriptRanges: [0x4E00 ... 0x9FFF],
            ocrRecognizer: .chinese,
            variants: []
        )
        let camera = OCRCameraModel(profile: profile, engine: engine)
        camera.beginRecognitionSession()
        let session = camera.recognitionSessionRevision
        let old = camera.liveOrientationRevision
        camera.updateInterfaceOrientation(.landscapeLeft)
        let boxes = [OCRTextBox(text: "你", rect: .zero)]
        camera.applyLiveRecognized(
            boxes,
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100),
            sessionRevision: session,
            orientationRevision: old
        )
        #expect(camera.recognizedTexts.isEmpty)
        camera.applyLiveRecognized(
            boxes,
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100),
            sessionRevision: session,
            orientationRevision: camera.liveOrientationRevision
        )
        #expect(camera.recognizedTexts.map(\.text) == ["你"])
    }
}
