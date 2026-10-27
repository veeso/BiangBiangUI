@testable import BiangBiangUI
import SwiftUI

@main
struct PaneHost: App {
    var body: some Scene {
        WindowGroup {
            if ProcessInfo.processInfo.arguments.contains("geometry") {
                CameraGeometryHarness()
            } else {
                Harness()
            }
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

struct CameraGeometryHarness: View {
    @State private var model: OCRCameraModel
    @State private var context: BiangBiangContext
    @State private var expectedBottom = 0.0
    @State private var previewDescription = ""

    init() {
        let profile = LanguageProfile(id: "zh", displayName: "Chinese",
                                      scriptRanges: [0x4E00 ... 0x9FFF], ocrRecognizer: .chinese, variants: [])
        let config = BiangBiangConfig(
            branding: Branding(appName: "Test", accentColorHex: "FF0000", logoAssetName: "",
                               buttonLogoAssetName: "", githubRepo: "", supportEmail: "",
                               appStoreId: "", playStoreId: ""),
            languages: [profile], extraSettings: [], plugins: [], features: FeatureFlags(), strings: nil
        )
        let context = BiangBiangContext(config: config, settings: SettingsStore(), audio: SystemTTSAudioProvider())
        _context = State(initialValue: context)
        _model = State(initialValue: OCRCameraModel(profile: profile, engine: context.engine))
    }

    var body: some View {
        TabView {
            CameraLiveScreen(cameraModel: model)
                .tabItem { Label("Camera", systemImage: "camera") }
        }
        .environment(context)
        .overlay(alignment: .bottomLeading) {
            VStack {
                Text(verbatim: String(expectedBottom)).accessibilityIdentifier("expectedBottom")
                Text(previewDescription).accessibilityIdentifier("previewGeometry")
            }
            .allowsHitTesting(false)
        }
        .task {
            // Seed a synthetic upright frame after the real screen has begun its session.
            // The simulator supplies no live camera; preview geometry is still native.
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(200))
                guard let layer = model.previewLayer,
                      let view = layer.superlayer?.delegate as? UIView,
                      let window = view.window else { continue }
                let preview = view.convert(layer.frame, to: window)
                let box = RecognizedTextBox(text: "Alignment", pixelRect: CGRect(x: 700, y: 500, width: 400, height: 120))
                if ProcessInfo.processInfo.arguments.contains("still"), model.capturedImage == nil {
                    // Both captured photos and gallery images render through capturedImage.
                    // The green source rectangle lets XCUITest measure actual rendered pixels.
                    model.capturedImage = UIGraphicsImageRenderer(size: CGSize(width: 1920, height: 1080)).image { context in
                        UIColor.white.setFill()
                        context.fill(CGRect(x: 0, y: 0, width: 1920, height: 1080))
                        UIColor.green.setFill()
                        // Match the OCR box's top and center; extend the sides so
                        // even an accessibility-sized pill cannot hide the marker.
                        context.fill(CGRect(x: 100, y: 500, width: 1600, height: 120))
                    }
                }
                model.lastImagePixelSize = CGSize(width: 1920, height: 1080)
                if model.recognizedTexts.isEmpty {
                    model.recognizedTexts = [box]
                    model.transliterationMap = [box.id: "Alignment"]
                }
                let scale = min(preview.width / 1920, preview.height / 1080)
                expectedBottom = preview.midY - 540 * scale + 500 * scale - 2
                previewDescription = "\(preview)"
            }
        }
    }
}
