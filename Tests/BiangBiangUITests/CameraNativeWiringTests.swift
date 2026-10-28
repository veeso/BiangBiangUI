@testable import BiangBiangUI
import Testing
import UIKit

@MainActor
struct CameraNativeWiringTests {
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
