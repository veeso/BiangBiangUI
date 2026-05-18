@testable import BiangBiangUI
import CoreGraphics
import Testing

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
}
