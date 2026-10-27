@testable import BiangBiangUI
import SwiftUI
import Testing
import UIKit

@MainActor
struct CameraSessionLogPaneTests {
    /// Rendered rows and actual tap/long-press/Clear wiring are covered by
    /// Tests/CameraPaneUI, which hosts this internal pane in a native app.
    @Test func accessibilityActionsUseOverridesAndDispatchIndependently() {
        var strings = UIStrings()
        strings.copy = "Copia"
        strings.save = "Salva"
        var actions: [String] = []
        let row = CameraSessionLogRow(
            entry: .init(id: UUID(), original: "你", transliteration: "ni"),
            strings: strings, accent: .red, isHighlighted: false, reduceMotion: true,
            onCopy: { actions.append("copy") }, onSave: { actions.append("save") }
        )
        #expect(row.namedActions.map(\.title) == ["Copia", "Salva"])
        row.namedActions[0].perform()
        #expect(actions == ["copy"])
        row.namedActions[1].perform()
        #expect(actions == ["copy", "save"])
    }

    @Test func exclusiveLongPressThenOrdinaryTapDispatchIndependentActions() async {
        var actions: [String] = []
        let row = CameraSessionLogRow(
            entry: .init(id: UUID(), original: "你", transliteration: "ni"),
            strings: UIStrings(), accent: .red, isHighlighted: false, reduceMotion: true,
            onCopy: { actions.append("copy") }, onSave: { actions.append("save") }
        )
        row.handleGesture(.first(true))
        // Hold beyond the old 350ms suppression timer. No delayed reset/action exists.
        try? await Task.sleep(for: .milliseconds(400))
        #expect(actions == ["save"])
        row.handleGesture(.second(()))
        #expect(actions == ["save", "copy"])
    }
}
