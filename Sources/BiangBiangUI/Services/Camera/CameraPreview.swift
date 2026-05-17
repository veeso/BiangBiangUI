//
//  CameraPreview.swift
//  BiangBiangUI
//

#if canImport(UIKit)
import AVFoundation
import SwiftUI

/// SwiftUI wrapper that renders the live camera preview.
///
/// Ported from `CameraPreview` in BiangBiang Hanzi; the only change is
/// that the `CameraModel` reference is renamed to `OCRCameraModel`.
public struct CameraPreview: UIViewRepresentable {
    public let session: AVCaptureSession
    public let cameraModel: OCRCameraModel

    public init(session: AVCaptureSession, cameraModel: OCRCameraModel) {
        self.session = session
        self.cameraModel = cameraModel
    }

    public func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspect
        cameraModel.previewLayer = previewLayer

        if let connection = previewLayer.connection {
            if connection.isVideoRotationAngleSupported(0) {
                connection.videoRotationAngle = 0
            }
        }

        view.layer.addSublayer(previewLayer)
        context.coordinator.previewLayer = previewLayer

        // Ensure initial sizing
        previewLayer.frame = view.bounds

        return view
    }

    public func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            context.coordinator.previewLayer?.frame = uiView.bounds
            cameraModel.previewLayer?.frame = uiView.bounds
        }
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    public class Coordinator {
        public var previewLayer: AVCaptureVideoPreviewLayer?
    }
}
#endif
