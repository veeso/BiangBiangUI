//
//  CameraPreview.swift
//  BiangBiangUI
//

#if canImport(UIKit)
    import AVFoundation
    import SwiftUI
    import UIKit

    /// SwiftUI wrapper that renders the live camera preview.
    ///
    /// The container keeps the preview layer sized to its laid-out bounds.
    /// Attaching the layer to `OCRCameraModel` lets its rotation coordinator
    /// keep preview and capture orientation aligned.
    public struct CameraPreview: UIViewRepresentable {
        public let session: AVCaptureSession
        public let cameraModel: OCRCameraModel

        public init(session: AVCaptureSession, cameraModel: OCRCameraModel) {
            self.session = session
            self.cameraModel = cameraModel
        }

        public func makeUIView(context _: Context) -> UIView {
            let view = CameraPreviewContainerView()
            let previewLayer = AVCaptureVideoPreviewLayer(session: session)
            previewLayer.videoGravity = .resizeAspect
            view.layer.addSublayer(previewLayer)
            view.previewLayer = previewLayer
            cameraModel.attachPreviewLayer(previewLayer)

            return view
        }

        public func updateUIView(_ uiView: UIView, context _: Context) {
            uiView.setNeedsLayout()
        }
    }

    private final class CameraPreviewContainerView: UIView {
        var previewLayer: AVCaptureVideoPreviewLayer?

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer?.frame = bounds
        }
    }
#endif
