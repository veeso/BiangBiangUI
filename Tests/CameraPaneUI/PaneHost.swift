@testable import BiangBiangUI
import SwiftUI

@main
struct PaneHost: App {
    var body: some Scene {
        WindowGroup {
            Harness()
        }
    }
}

struct Harness: View {
    @State private var log: CameraSessionLog = {
        var log = CameraSessionLog()
        _ = log.ingest([(original: "你", transliteration: "ni"), (original: "好", transliteration: "hao")])
        return log
    }()

    @State private var copied = ""
    @State private var saved = ""
    @State private var copyCount = 0
    @State private var saveCount = 0
    private var strings: UIStrings {
        var strings = UIStrings()
        strings.cameraLogTitle = "Testi trovati"
        strings.cameraLogClear = "Cancella"
        strings.cameraLogEmpty = "Nessun testo"
        strings.copy = "Copia"
        strings.save = "Salva"
        strings.tapToCopyLongPressToSave = "Tocca per copiare, tieni premuto per salvare"
        return strings
    }

    var body: some View {
        VStack {
            Text("Copied: \(copied) · \(copyCount)").accessibilityIdentifier("copied")
            Text("Saved: \(saved) · \(saveCount)").accessibilityIdentifier("saved")
            CameraSessionLogPane(log: log, strings: strings,
                                 accent: .red, highlightedIDs: [],
                                 onCopy: { copied = "\($0.original)/\($0.transliteration)"; copyCount += 1 },
                                 onSave: { saved = "\($0.original)/\($0.transliteration)"; saveCount += 1 },
                                 onClear: { log.clear() })
        }
    }
}
