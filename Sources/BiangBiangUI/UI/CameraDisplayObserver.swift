import SwiftUI
import UIKit

struct CameraDisplayObserver: UIViewRepresentable {
    let onChange: @MainActor (Bool, UIInterfaceOrientation) -> Void

    func makeUIView(context _: Context) -> CameraDisplayObservationView {
        let view = CameraDisplayObservationView()
        view.onChange = onChange
        view.refresh()
        return view
    }

    func updateUIView(_ view: CameraDisplayObservationView, context _: Context) {
        view.onChange = onChange
        view.refresh()
    }
}

@MainActor
final class CameraDisplayObservationView: UIView {
    var onChange: (@MainActor (Bool, UIInterfaceOrientation) -> Void)?

    var observation: (() -> (isBuiltIn: Bool, orientation: UIInterfaceOrientation))?

    private var refreshScheduled = false
    private var lastObservation: (isBuiltIn: Bool, orientation: UIInterfaceOrientation)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        observeDeviceOrientation()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        observeDeviceOrientation()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        Task { @MainActor in
            UIDevice.current.endGeneratingDeviceOrientationNotifications()
        }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        scheduleRefresh()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        scheduleRefresh()
    }

    func refresh() {
        scheduleRefresh()
    }

    private func observeDeviceOrientation() {
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(deviceOrientationDidChange),
            name: UIDevice.orientationDidChangeNotification,
            object: nil
        )
    }

    @objc private func deviceOrientationDidChange() {
        scheduleRefresh()
    }

    private func scheduleRefresh() {
        guard !refreshScheduled else { return }
        refreshScheduled = true

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            refreshScheduled = false

            let scene = window?.windowScene
            let observation = observation?() ?? (
                // `UIScreen.main` is one fixed screen; on multi-panel devices the
                // other built-in panel is not it. Only external scenes carry this role.
                isBuiltIn: scene.map { $0.session.role != .windowExternalDisplayNonInteractive } ?? false,
                orientation: scene?.interfaceOrientation ?? .unknown
            )
            guard lastObservation?.isBuiltIn != observation.isBuiltIn
                || lastObservation?.orientation != observation.orientation
            else { return }

            lastObservation = observation
            onChange?(observation.isBuiltIn, observation.orientation)
        }
    }
}
