@testable import BiangBiangUI
import CoreGraphics
import Testing
import UIKit

private struct LogLatin: Transliterator {
    func transliterate(_ text: String) -> String {
        text == "坏" ? "" : "latin"
    }
}

@MainActor
@Suite(.serialized)
struct CameraRecognitionLogTests {
    /// Polls for up to five seconds of wall time; a yield count depends on runner load.
    private func eventually(_ condition: () -> Bool) async {
        for _ in 0 ..< 500 {
            if condition() {
                return
            }
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func model(
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) -> (OCRCameraModel, TextProcessingEngine) {
        let engine = TextProcessingEngine(
            scriptRanges: [0x4E00 ... 0x9FFF], transliterator: LogLatin()
        )
        let profile = LanguageProfile(
            id: "zh",
            displayName: "Chinese",
            scriptRanges: [0x4E00 ... 0x9FFF],
            ocrRecognizer: .chinese,
            variants: []
        )
        return (OCRCameraModel(profile: profile, engine: engine, highlightSleep: sleep), engine)
    }

    @Test(arguments: [false, true]) func rowActionsCopySelectedTextAndSaveBothForms(showLatin: Bool) throws {
        let (camera, _) = model()
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = SettingsStore(userDefaults: defaults)
        let entry = CameraSessionLog.Entry(id: UUID(), original: "你", transliteration: "ni")
        camera.showTransliteration = showLatin
        var clipboard = ""
        CameraSessionLogActions.copy(entry, camera: camera, writeClipboard: { clipboard = $0 })
        #expect(clipboard == (showLatin ? "ni" : "你"))
        #expect(camera.showCopiedToast)
        #expect(!camera.showSavedToast)
        #expect(settings.history.isEmpty)
        camera.showCopiedToast = false
        clipboard = "unchanged"
        CameraSessionLogActions.save(entry, camera: camera, settings: settings, variantID: "active")
        #expect(clipboard == "unchanged")
        #expect(!camera.showCopiedToast)
        #expect(camera.showSavedToast)
        #expect(settings.history.first?.original == "你")
        #expect(settings.history.first?.transliteration == "ni")
        #expect(settings.history.first?.variantId == "active")
    }

    @Test func appliesEligibleResultsWithoutErasingOnEmptyFrame() {
        let (camera, engine) = model()
        camera.beginRecognitionSession()
        let boxes = ["你", "ASCII", "坏"].map {
            OCRTextBox(text: $0, rect: CGRect(x: 10, y: 10, width: 20, height: 10))
        }
        camera.applyRecognized(
            boxes,
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100)
        )
        #expect(camera.sessionLog.entries.map(\.original) == ["你"])
        camera.applyRecognized(
            [],
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100)
        )
        #expect(camera.sessionLog.entries.map(\.original) == ["你"])
        #expect(camera.recognizedTexts.isEmpty)
    }

    @Test func oldSessionResultsAreIgnoredAfterReentry() {
        let (camera, engine) = model()
        camera.beginRecognitionSession()
        let oldSession = camera.recognitionSessionRevision
        let oldOrientation = camera.liveOrientationRevision
        let boxes = [OCRTextBox(text: "你", rect: .zero)]
        camera.endRecognitionSession()
        camera.beginRecognitionSession()
        camera.applyLiveRecognized(
            boxes,
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100),
            sessionRevision: oldSession,
            orientationRevision: oldOrientation
        )
        #expect(camera.sessionLog.entries.isEmpty)
        camera.applyLiveRecognized(
            boxes,
            engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100),
            sessionRevision: camera.recognitionSessionRevision,
            orientationRevision: camera.liveOrientationRevision
        )
        #expect(camera.sessionLog.entries.count == 1)
        camera.endRecognitionSession()
        #expect(camera.sessionLog.entries.isEmpty)
    }

    @Test func frameDeliveredAfterEndCannotPopulateNextVisit() {
        let (camera, engine) = model()
        camera.beginRecognitionSession()
        camera.endRecognitionSession()
        camera.applyLiveRecognized(
            [OCRTextBox(text: "你", rect: .zero)], engine: engine,
            imagePixelSize: CGSize(width: 200, height: 100),
            sessionRevision: camera.recognitionSessionRevision,
            orientationRevision: camera.liveOrientationRevision
        )
        #expect(camera.sessionLog.entries.isEmpty)
        camera.beginRecognitionSession()
        #expect(camera.sessionLog.entries.isEmpty)
    }

    @Test func highlightsHaveIndependentDeadlinesAndCancelledTasksCannotAffectNewRows() async {
        let clock = HighlightClock()
        let (camera, engine) = model(sleep: { await clock.sleep($0) })
        camera.beginRecognitionSession()
        func ingest(_ text: String) {
            camera.applyRecognized([OCRTextBox(text: text, rect: .zero)], engine: engine,
                                   imagePixelSize: CGSize(width: 200, height: 100))
        }
        ingest("你")
        await clock.waitForSleepers(1)
        let first = camera.sessionLog.entries[0].id
        await clock.advance(.seconds(1))
        ingest("你") // A duplicate must not extend the first deadline.
        ingest("好")
        await clock.waitForSleepers(2)
        let second = camera.sessionLog.entries[0].id
        #expect(camera.highlightedSessionLogIDs == [first, second])
        await clock.advance(.milliseconds(499))
        #expect(camera.highlightedSessionLogIDs == [first, second])
        await clock.advance(.milliseconds(1))
        await eventually { camera.highlightedSessionLogIDs == [second] }
        #expect(camera.highlightedSessionLogIDs == [second])
        await clock.advance(.seconds(1))
        await eventually { camera.highlightedSessionLogIDs.isEmpty }
        #expect(camera.highlightedSessionLogIDs.isEmpty)

        ingest("新")
        await clock.waitForSleepers(1)
        camera.clearSessionLog()
        #expect(camera.highlightedSessionLogIDs.isEmpty)
        ingest("新")
        await clock.waitForSleepers(2)
        let fresh = camera.sessionLog.entries[0].id
        // Resume the cancelled old sleeper late, without resuming the new one.
        await clock.resumeFirst()
        for _ in 0 ..< 20 {
            await Task.yield()
        }
        #expect(camera.highlightedSessionLogIDs == [fresh])
        camera.endRecognitionSession()
        await clock.advance(.seconds(2))
        await eventually { camera.highlightedSessionLogIDs.isEmpty }
        #expect(camera.highlightedSessionLogIDs.isEmpty)
    }

    @Test func evictedRowsLoseTheirHighlights() async {
        let clock = HighlightClock()
        let (camera, engine) = model(sleep: { await clock.sleep($0) })
        camera.beginRecognitionSession()
        for index in 0 ..< 51 {
            camera.applyRecognized([OCRTextBox(text: "你\(index)", rect: .zero)],
                                   engine: engine, imagePixelSize: .zero)
        }
        #expect(camera.sessionLog.entries.count == 50)
        #expect(camera.highlightedSessionLogIDs == Set(camera.sessionLog.entries.map(\.id)))
        await clock.waitForSleepers(51)
        camera.endRecognitionSession()
        await clock.advance(.seconds(2))
    }
}

private actor HighlightClock {
    private var now: Duration = .zero
    private var sleepers: [(Duration, CheckedContinuation<Void, Never>)] = []

    func sleep(_ duration: Duration) async {
        await withCheckedContinuation { sleepers.append((now + duration, $0)) }
    }

    func waitForSleepers(_ count: Int) async {
        // Bound the wait by wall time: a yield count depends on runner load.
        for _ in 0 ..< 500 {
            if sleepers.count >= count {
                return
            }
            try? await Task.sleep(for: .milliseconds(10))
        }
        #expect(sleepers.count >= count)
    }

    func advance(_ duration: Duration) {
        now += duration
        let ready = sleepers.filter { $0.0 <= now }
        sleepers.removeAll { $0.0 <= now }
        ready.forEach { $0.1.resume() }
    }

    func resumeFirst() {
        sleepers.removeFirst().1.resume()
    }
}
