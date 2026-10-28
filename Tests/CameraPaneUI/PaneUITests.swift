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
}
