import BiangBiangUI
import Testing
import UIKit

@MainActor
struct CameraPublicGalleryTests {
    @Test func publicModelAcceptsGalleryImageWithoutInternalLifecycleSetup() {
        struct Latin: Transliterator {
            func transliterate(_ text: String) -> String {
                text
            }
        }
        let profile = LanguageProfile(id: "zh", displayName: "Chinese",
                                      scriptRanges: [0x4E00 ... 0x9FFF], ocrRecognizer: .chinese,
                                      variants: [])
        let camera = OCRCameraModel(profile: profile, engine: TextProcessingEngine(
            scriptRanges: profile.scriptRanges, transliterator: Latin()
        ))
        let image = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 20)).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 20))
        }
        camera.recognizeGalleryImage(image)
        #expect(camera.capturedImage === image)
    }
}
