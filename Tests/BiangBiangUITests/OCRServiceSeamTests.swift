@testable import BiangBiangUI
import CoreGraphics
import Testing
import UIKit

struct OCRServiceSeamTests {
    struct StubService: OCRService {
        func recognize(_: CGImage,
                       recognizer _: OCRRecognizer) async -> [OCRTextBox]
        {
            [OCRTextBox(text: "你好",
                        rect: CGRect(x: 1, y: 2, width: 3, height: 4))]
        }
    }

    @Test func stubServiceReturnsBox() async throws {
        let img = try #require(CGContext(
            data: nil, width: 1, height: 1, bitsPerComponent: 8,
            bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )?.makeImage())
        let boxes = await StubService().recognize(img, recognizer: .chinese)
        #expect(boxes == [OCRTextBox(
            text: "你好", rect: CGRect(x: 1, y: 2, width: 3, height: 4)
        )])
    }

    @Test func defaultServiceConvertsNormalizedToPixelTopLeft() {
        // Vision box: normalized, bottom-left origin.
        let normalized = CGRect(x: 0.25, y: 0.10, width: 0.5, height: 0.20)
        let pixel = DefaultOCRService.pixelRect(
            fromVisionNormalized: normalized,
            imageWidth: 200, imageHeight: 100
        )
        // x: 0.25*200=50  w: 0.5*200=100  h: 0.20*100=20
        // top-left y = (1 - 0.10 - 0.20) * 100 = 70
        #expect(pixel == CGRect(x: 50, y: 70, width: 100, height: 20))
    }

    @Test func profileDefaultsToNilService() {
        let p = LanguageProfile(
            id: "zh", displayName: "Chinese",
            scriptRanges: [0x4E00 ... 0x9FFF],
            ocrRecognizer: .chinese, variants: []
        )
        #expect(p.ocrService == nil)
    }

    @Test func resolvedServiceFallsBackToDefault() {
        let p = LanguageProfile(
            id: "zh", displayName: "Chinese",
            scriptRanges: [0x4E00 ... 0x9FFF],
            ocrRecognizer: .chinese, variants: []
        )
        #expect(OCRCameraModel.resolveService(p) is DefaultOCRService)
    }

    @Test func uprightCGImageTransposesRotatedImage() throws {
        let ctx = try #require(CGContext(
            data: nil, width: 4, height: 2, bitsPerComponent: 8,
            bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        let base = try #require(ctx.makeImage())
        // 4x2 source; .right orientation → upright should be 2x4.
        let rotated = UIImage(cgImage: base, scale: 1, orientation: .right)
        let upright = OCRCameraModel.uprightCGImage(from: rotated)
        #expect(upright?.width == 2)
        #expect(upright?.height == 4)
    }
}
