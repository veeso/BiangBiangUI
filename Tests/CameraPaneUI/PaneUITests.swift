import UIKit
import XCTest

final class PaneUITests: XCTestCase {
    @MainActor
    func testPopulatedPaneActionsAndEmptyState() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Testi trovati · 2"].waitForExistence(timeout: 5))
        let row = app.buttons["你, ni"]
        XCTAssertTrue(row.exists)
        XCTAssertTrue(app.buttons["好, hao"].exists)
        row.tap()
        XCTAssertEqual(app.staticTexts["copied"].label, "Copied: 你/ni · 1")
        XCTAssertEqual(app.staticTexts["saved"].label, "Saved:  · 0")
        let secondRow = app.buttons["好, hao"]
        secondRow.press(forDuration: 1)
        XCTAssertEqual(app.staticTexts["saved"].label, "Saved: 好/hao · 1")
        XCTAssertEqual(app.staticTexts["copied"].label, "Copied: 你/ni · 1")
        secondRow.tap()
        XCTAssertEqual(app.staticTexts["copied"].label, "Copied: 好/hao · 2")
        XCTAssertEqual(app.staticTexts["saved"].label, "Saved: 好/hao · 1")
        let clear = app.buttons["Cancella"]
        XCTAssertGreaterThanOrEqual(clear.frame.width, 44)
        XCTAssertGreaterThanOrEqual(clear.frame.height, 44)
        clear.tap()
        XCTAssertTrue(app.staticTexts["Testi trovati · 0"].exists)
        XCTAssertTrue(app.staticTexts["Nessun testo"].exists)
        XCTAssertFalse(row.exists)
    }

    @MainActor
    func testLandscapeOverlayUsesNativePreviewWithAsymmetricSafeAreas() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        try assertOverlayGeometry(still: false)
    }

    @MainActor
    func testStillImageOverlayTracksRenderedMarkerWithAsymmetricSafeAreas() throws {
        continueAfterFailure = false
        defer { XCUIDevice.shared.orientation = .portrait }
        for orientation: UIDeviceOrientation in [.portrait, .landscapeLeft, .landscapeRight] {
            XCUIDevice.shared.orientation = orientation
            try assertOverlayGeometry(still: true)
        }
    }

    @MainActor
    private func assertOverlayGeometry(still: Bool) throws {
        let app = XCUIApplication()
        app.launchArguments = still ? ["geometry", "still"] : ["geometry"]
        app.launch()
        let overlay = app.buttons["Alignment"]
        XCTAssertTrue(overlay.waitForExistence(timeout: 10))
        defer { app.terminate() }
        if still {
            let screenshot = XCUIScreen.main.screenshot()
            let attachment = XCTAttachment(screenshot: screenshot)
            attachment.lifetime = .keepAlways
            add(attachment)
            let marker = try renderedGreenMarker(in: screenshot.image, screenSize: app.frame.size)
            XCTAssertEqual(overlay.frame.maxY, marker.minY - 2, accuracy: 2)
            XCTAssertEqual(overlay.frame.midX, marker.midX, accuracy: 2)
            return
        }
        let expected = app.staticTexts["expectedBottom"]
        XCTAssertTrue(expected.waitForExistence(timeout: 5))
        let bottom = try XCTUnwrap(Double(expected.label))
        XCTAssertGreaterThan(bottom, 0)
        XCTAssertEqual(overlay.frame.maxY, bottom, accuracy: 2,
                       "Native preview: \(app.staticTexts["previewGeometry"].label)")
    }

    private func renderedGreenMarker(in image: UIImage, screenSize: CGSize) throws -> CGRect {
        // XCUITest screenshots carry orientation metadata in landscape.
        // Drawing the UIImage applies that transform before reading its pixels.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let upright = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        let cgImage = try XCTUnwrap(upright.cgImage)
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        try pixels.withUnsafeMutableBytes { buffer in
            let context = try XCTUnwrap(CGContext(data: buffer.baseAddress, width: width, height: height,
                                                  bitsPerComponent: 8, bytesPerRow: width * 4,
                                                  space: CGColorSpaceCreateDeviceRGB(),
                                                  bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue
                                                      | CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        for y in 0 ..< height {
            var rowMinX = width
            var rowMaxX = 0
            for x in 0 ..< width {
                let offset = (y * width + x) * 4
                if pixels[offset] < 40, pixels[offset + 1] > 220, pixels[offset + 2] < 40 {
                    rowMinX = min(rowMinX, x)
                    rowMaxX = max(rowMaxX, x)
                }
            }
            // Ignore the small green system camera privacy indicator.
            if rowMaxX - rowMinX > 40 {
                minX = min(minX, rowMinX)
                minY = min(minY, y)
                maxX = max(maxX, rowMaxX)
                maxY = max(maxY, y)
            }
        }
        XCTAssertLessThan(minX, maxX, "Synthetic image marker must be visible")
        let scale = CGFloat(width) / screenSize.width
        return CGRect(x: CGFloat(minX) / scale, y: CGFloat(minY) / scale,
                      width: CGFloat(maxX - minX + 1) / scale, height: CGFloat(maxY - minY + 1) / scale)
    }
}
