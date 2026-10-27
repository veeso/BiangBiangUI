import AVFoundation
@testable import BiangBiangUI
import Testing
import UIKit

@MainActor
struct CameraNativeWiringTests {
    @Test func attachmentAndRotationUpdateBothConnectionsAndFrameRevision() {
        struct Identity: Transliterator {
            func transliterate(_ text: String) -> String {
                text
            }
        }
        let preview = RotationConnection()
        let video = RotationConnection()
        var angle: CGFloat = 180
        let profile = LanguageProfile(id: "zh", displayName: "Chinese",
                                      scriptRanges: [0x4E00 ... 0x9FFF], ocrRecognizer: .chinese, variants: [])
        let camera = OCRCameraModel(
            profile: profile,
            engine: TextProcessingEngine(scriptRanges: profile.scriptRanges, transliterator: Identity()),
            highlightSleep: { try await Task.sleep(for: $0) },
            rotationConnections: { [preview, video] }, horizonAngle: { angle }
        )
        camera.attachPreviewLayer(AVCaptureVideoPreviewLayer(session: camera.session))
        #expect(preview.angles == [0])
        #expect(video.angles == [0])
        camera.updateInterfaceOrientation(.landscapeLeft)
        #expect(preview.angles.last == 180)
        #expect(video.angles.last == 180)
        #expect(camera.frameOrientationRevision == 1)
        angle = 0
        camera.updateInterfaceOrientation(.landscapeRight)
        #expect(preview.angles.last == 0)
        #expect(video.angles.last == 0)
        #expect(camera.frameOrientationRevision == 2)
        camera.rotationCoordinatorDidChange(angle: 5)
        #expect(preview.angles.last == 5)
        #expect(video.angles.last == 5)
        #expect(camera.frameOrientationRevision == 3)
        camera.rotationCoordinatorDidChange(angle: 5)
        #expect(camera.frameOrientationRevision == 3)
        camera.updateInterfaceOrientation(.portrait)
        #expect(preview.angles.last == 0)
        #expect(video.angles.last == 0)
        #expect(camera.frameOrientationRevision == 4)
        camera.updateInterfaceOrientation(.portrait)
        #expect(camera.frameOrientationRevision == 4)
        #expect(camera.liveOrientationRevision == camera.frameOrientationRevision)
    }

    @Test func observerDeliversAttachmentOrientationsAndDisplayTransitionsOnlyOnce() async {
        let view = CameraDisplayObservationView()
        var builtIn = true
        var orientation = UIInterfaceOrientation.portrait
        view.observation = { (builtIn, orientation) }
        var observations: [(Bool, UIInterfaceOrientation)] = []
        view.onChange = { observations.append(($0, $1)) }
        func flush() async {
            await withCheckedContinuation { continuation in
                DispatchQueue.main.async { continuation.resume() }
            }
        }
        view.didMoveToWindow()
        await flush()
        #expect(observations.count == 1)
        #expect(observations.last?.0 == true)
        for next in [UIInterfaceOrientation.landscapeLeft, .landscapeRight, .portrait] {
            orientation = next
            NotificationCenter.default.post(name: UIDevice.orientationDidChangeNotification, object: nil)
            await flush()
            #expect(observations.last?.1 == next)
        }
        builtIn = false
        view.didMoveToWindow()
        await flush()
        #expect(observations.last?.0 == false)
        builtIn = true
        view.didMoveToWindow()
        await flush()
        #expect(observations.last?.0 == true)
        let count = observations.count
        view.layoutSubviews()
        view.refresh()
        await flush()
        #expect(observations.count == count)
    }
}

private final class RotationConnection: CameraRotationConnection {
    var angles: [CGFloat] = []
    var videoRotationAngle: CGFloat = 0 {
        didSet { angles.append(videoRotationAngle) }
    }

    func isVideoRotationAngleSupported(_: CGFloat) -> Bool {
        true
    }
}
