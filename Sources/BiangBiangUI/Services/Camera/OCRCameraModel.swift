//
//  OCRCameraModel.swift
//  BiangBiangUI
//

#if canImport(UIKit)
    @preconcurrency import AVFoundation
    import CoreImage
    import Foundation
    import Observation
    import UIKit

    /// Text box identified by the OCR pass.
    ///
    /// `pixelRect` is in source-image pixel coordinates with a top-left
    /// origin (the `OCRService` contract).
    public struct RecognizedTextBox: Identifiable, Equatable, Sendable {
        public let id = UUID()
        public let text: String
        /// Bounding box in source-image pixel space (top-left origin).
        public let pixelRect: CGRect

        public init(text: String, pixelRect: CGRect) {
            self.text = text
            self.pixelRect = pixelRect
        }
    }

    /// AVFoundation + Vision camera model, generalised for any `LanguageProfile`.
    ///
    /// Ported from `CameraModel` in BiangBiang Hanzi. Generalisations:
    /// - Initialised with a `LanguageProfile` and an injected `TextProcessingEngine`.
    /// - Recognition languages are derived from `profile.ocrRecognizer`.
    /// - `pinyinMap` is renamed `transliterationMap` (identical semantics).
    /// - No app-specific Quran/history state; all other behaviour is verbatim.
    @Observable
    @MainActor
    public final class OCRCameraModel: NSObject, AVCapturePhotoCaptureDelegate,
        AVCaptureVideoDataOutputSampleBufferDelegate
    {
        // MARK: - Public observable state

        /// Recognised text boxes from the most recent Vision pass.
        public var recognizedTexts: [RecognizedTextBox] = []
        /// Maps each box's `id` to the engine-processed transliteration string.
        /// `nil`-return from the engine means no script was found; that box is absent.
        public var transliterationMap: [UUID: String] = [:]
        /// Still image captured by the shutter button or loaded from gallery.
        public var capturedImage: UIImage?
        /// `true` when the user has denied / restricted camera access.
        public var missingCameraPermission: Bool = false
        /// When `true`, overlays show the transliteration; when `false`, the raw script.
        public var showTransliteration: Bool = true
        /// Transient toast: "text copied to clipboard".
        public var showCopiedToast: Bool = false
        /// Transient toast: "saved to history".
        public var showSavedToast: Bool = false
        /// Pixel size of the image the most recent `recognizedTexts` were
        /// detected in. The overlay maps `pixelRect` (image-pixel, top-left)
        /// onto the view using this size. `.zero` until the first pass.
        public var lastImagePixelSize: CGSize = .zero

        // MARK: - Zoom

        /// UI-facing zoom factor (1.0 == standard wide lens).
        public var zoomFactor: CGFloat = 1.0
        /// Zoom presets available on the active physical device.
        public var availableZoomPresets: [CGFloat] = []

        // MARK: - AVFoundation

        /// The capture session. Exposed so `CameraPreview` can attach its preview layer.
        ///
        /// `nonisolated(unsafe)` permits `Task.detached` to call `startRunning()`/
        /// `stopRunning()` off the main actor, which is the pattern AVFoundation
        /// requires. `AVCaptureSession` is internally thread-safe for start/stop.
        @ObservationIgnored public nonisolated(unsafe) let session = AVCaptureSession()

        // MARK: - Private

        @ObservationIgnored private let profile: LanguageProfile
        /// `TextProcessingEngine` is `@unchecked Sendable`, so the nonisolated
        /// camera-queue delegate can hand spans to it without an actor hop.
        @ObservationIgnored private nonisolated let engine: TextProcessingEngine
        /// OCR backend, resolved once from `profile` at init. `any OCRService`
        /// is `Sendable`, so the nonisolated frame delegate can capture it.
        @ObservationIgnored private nonisolated let service: any OCRService
        /// Script hint passed to the service, resolved once at init so the
        /// nonisolated frame delegate never touches main-actor state.
        @ObservationIgnored private nonisolated let recognizer: OCRRecognizer

        @ObservationIgnored private var device: AVCaptureDevice?
        @ObservationIgnored private var zoomSwitchOverFactor: CGFloat = 1.0
        @ObservationIgnored private var maxUIZoom: CGFloat = 1.0

        @ObservationIgnored private let videoOutput = AVCaptureVideoDataOutput()
        @ObservationIgnored private let output = AVCapturePhotoOutput()
        @ObservationIgnored private let queue = DispatchQueue(
            label: "camera.frame.processing"
        )
        @ObservationIgnored public var previewLayer: AVCaptureVideoPreviewLayer?
        /// Throttle timestamp. Confined to the serial `queue` (the only thing
        /// that reads/writes it is the nonisolated `captureOutput`).
        @ObservationIgnored private nonisolated(unsafe) var lastProcessingTime = Date.distantPast
        /// Mirrors `capturedImage != nil` for the nonisolated frame delegate so
        /// it can suspend live OCR without touching main-actor state. A benign
        /// cross-thread `Bool` race here costs at most one extra/dropped frame.
        @ObservationIgnored private nonisolated(unsafe) var captureSuspended = false
        @ObservationIgnored private var isConfigured = false

        // MARK: - Init

        public init(profile: LanguageProfile, engine: TextProcessingEngine) {
            self.profile = profile
            self.engine = engine
            service = Self.resolveService(profile)
            recognizer = profile.ocrRecognizer
        }

        /// Resolves the OCR backend for `profile`, falling back to the
        /// built-in `DefaultOCRService` when the profile supplies none.
        nonisolated static func resolveService(_ profile: LanguageProfile) -> any OCRService {
            profile.ocrService ?? DefaultOCRService()
        }

        // MARK: - Photo capture

        /// Captures a still photo and runs OCR on it.
        public func capturePhoto() {
            output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }

        /// `AVCapturePhotoCaptureDelegate` — receives the compressed photo data.
        public nonisolated func photoOutput(
            _: AVCapturePhotoOutput,
            didFinishProcessingPhoto photo: AVCapturePhoto,
            error _: Error?
        ) {
            guard let imageData = photo.fileDataRepresentation(),
                  let image = UIImage(data: imageData)
            else { return }
            captureSuspended = true
            Task { @MainActor in
                self.capturedImage = image
                self.recognizeText(from: image)
            }
        }

        // MARK: - Live video output

        /// `AVCaptureVideoDataOutputSampleBufferDelegate` — throttled live OCR.
        public nonisolated func captureOutput(
            _: AVCaptureOutput,
            didOutput sampleBuffer: CMSampleBuffer,
            from _: AVCaptureConnection
        ) {
            // Do not capture while a still image is set.
            if captureSuspended { return }

            let now = Date()
            guard now.timeIntervalSince(lastProcessingTime) > 1 else { return }
            lastProcessingTime = now

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
                  let cgImage = Self.cgImage(from: pixelBuffer) else { return }
            let service = self.service
            let recognizer = self.recognizer
            let engine = self.engine
            let size = CGSize(width: cgImage.width, height: cgImage.height)
            Task { @MainActor in
                let boxes = await service.recognize(cgImage, recognizer: recognizer)
                self.applyRecognized(boxes, engine: engine, imagePixelSize: size)
            }
        }

        // MARK: - Gallery

        /// Loads `uiImage` as the active image and runs OCR on it.
        public func recognizeGalleryImage(_ uiImage: UIImage) {
            captureSuspended = true
            capturedImage = uiImage
            recognizeText(from: uiImage)
        }

        // MARK: - Session lifecycle

        /// Checks camera permission and starts the capture session.
        ///
        /// Calls `configureAndStartSession()` if authorized; otherwise sets
        /// `missingCameraPermission` and/or requests permission.
        public func checkPermissionsAndStart() async {
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                await configureAndStartSession()
            case .notDetermined:
                let granted = await AVCaptureDevice.requestAccess(for: .video)
                if granted {
                    await configureAndStartSession()
                } else {
                    missingCameraPermission = true
                }
            case .denied, .restricted:
                print(
                    "⚠️ Camera permission denied. Enable them on Settings > Privacy > Camera"
                )
                missingCameraPermission = true
            @unknown default:
                missingCameraPermission = true
            }
        }

        /// Clears the active still image, returning the view to live-feed mode.
        public func deleteCapturedImage() {
            capturedImage = nil
            captureSuspended = false
        }

        /// Stops the capture session on a background thread.
        ///
        /// Swift 6 strict concurrency: `AVCaptureSession` is not `Sendable`, so
        /// `Task.detached` triggers a sending error. We use `DispatchQueue.global().async`
        /// instead — identical observable behaviour (off-main execution) without the
        /// `Sendable` constraint imposed by the Swift concurrency runtime. The
        /// `session` property is `nonisolated(unsafe)` so the closure can capture it.
        public func stopSession() async {
            let session = session
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                DispatchQueue.global(qos: .userInitiated).async {
                    if session.isRunning {
                        session.stopRunning()
                    }
                    continuation.resume()
                }
            }
        }

        // MARK: - Zoom

        /// Sets the UI zoom factor, clamped to the active device's limits.
        public func setZoom(_ uiZoom: CGFloat) {
            guard let device else { return }
            let clampedUI = clampZoom(uiZoom, min: 1.0, max: maxUIZoom)
            let deviceZoom = uiZoomToDeviceZoom(clampedUI, switchOverFactor: zoomSwitchOverFactor)
            let clampedDevice = clampZoom(
                deviceZoom,
                min: device.minAvailableVideoZoomFactor,
                max: device.maxAvailableVideoZoomFactor
            )
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = clampedDevice
                device.unlockForConfiguration()
                zoomFactor = clampedUI
            } catch {
                print("⚠️ Failed to set zoom: \(error)")
            }
        }

        // MARK: - Private: session setup

        private func configureAndStartSession() async {
            // Configure only once: re-attaching inputs while the session is live
            // leaves the camera in a broken state on the second visit.
            if !isConfigured {
                configureSession()
                isConfigured = true
            }
            await startCaptureSession()
            // Re-apply zoom: addInput resets videoZoomFactor on some devices.
            setZoom(zoomFactor)
        }

        private func configureSession() {
            session.beginConfiguration()
            defer { session.commitConfiguration() }

            let virtualDiscovery = AVCaptureDevice.DiscoverySession(
                deviceTypes: [
                    .builtInTripleCamera,
                    .builtInDualWideCamera,
                    .builtInDualCamera,
                    .builtInWideAngleCamera,
                ],
                mediaType: .video,
                position: .back
            )

            let device =
                virtualDiscovery.devices.first(where: { $0.deviceType == .builtInTripleCamera })
                    ?? virtualDiscovery.devices.first(where: { $0.deviceType == .builtInDualWideCamera })
                    ?? virtualDiscovery.devices.first(where: { $0.deviceType == .builtInDualCamera })
                    ?? virtualDiscovery.devices.first(where: { $0.deviceType == .builtInWideAngleCamera })

            guard let device else { return }
            self.device = device

            // Configure focus, format, and zoom bookkeeping
            do {
                try device.lockForConfiguration()

                if let format = device.formats.first(where: {
                    $0.videoSupportedFrameRateRanges.first!.maxFrameRate >= 30
                }) {
                    device.activeFormat = format
                }

                if device.isFocusPointOfInterestSupported {
                    device.focusPointOfInterest = CGPoint(x: 0.5, y: 0.5)
                    device.focusMode = .autoFocus
                }

                // Near focus (helps macro / close-up text scanning)
                if device.isAutoFocusRangeRestrictionSupported {
                    device.autoFocusRangeRestriction = .near
                }

                // Continuous AF
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }

                // Exposure continuous
                if device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposureMode = .continuousAutoExposure
                }

                let switchOvers = device.virtualDeviceSwitchOverVideoZoomFactors
                    .map { CGFloat(truncating: $0) }
                let switchOver = switchOvers.first ?? 1.0
                zoomSwitchOverFactor = switchOver

                let maxDeviceZoom = device.maxAvailableVideoZoomFactor
                let maxUI = deviceZoomToUIZoom(maxDeviceZoom, switchOverFactor: switchOver)
                maxUIZoom = maxUI

                // Default to 1.0× (standard wide lens).
                let initialDeviceZoom = uiZoomToDeviceZoom(1.0, switchOverFactor: switchOver)
                device.videoZoomFactor = clampZoom(
                    initialDeviceZoom,
                    min: device.minAvailableVideoZoomFactor,
                    max: maxDeviceZoom
                )

                device.unlockForConfiguration()

                zoomFactor = 1.0
                availableZoomPresets = availablePresets(maxUIZoom: maxUIZoom)
            } catch {
                print("⚠️ Failed to configure camera focus: \(error)")
            }

            // Remove existing inputs to avoid duplicates
            for input in session.inputs {
                session.removeInput(input)
            }

            guard let input = try? AVCaptureDeviceInput(device: device) else { return }

            if session.canAddInput(input) {
                session.addInput(input)
            }
            if session.canAddOutput(output) {
                session.addOutput(output)
            }
            if session.canAddOutput(videoOutput) {
                videoOutput.setSampleBufferDelegate(self, queue: queue)
                session.addOutput(videoOutput)
            }
        }

        /// Start the session off the main thread.
        ///
        /// Uses `DispatchQueue.global().async` for the same reason as `stopSession()`:
        /// `AVCaptureSession` is not `Sendable` so `Task.detached` would fail
        /// under Swift 6 strict concurrency. The `session` property is
        /// `nonisolated(unsafe)` so the closure can capture it.
        private func startCaptureSession() async {
            let session = session
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                DispatchQueue.global(qos: .userInitiated).async {
                    if !session.isRunning {
                        session.startRunning()
                    }
                    continuation.resume()
                }
            }
        }

        // MARK: - Private: OCR plumbing

        /// Builds a still `CGImage` from a live camera frame. The resulting
        /// image is in the sensor's native pixel space, matching the
        /// coordinate space `OCRService` returns boxes in.
        nonisolated static func cgImage(from pixelBuffer: CVPixelBuffer) -> CGImage? {
            let ci = CIImage(cvPixelBuffer: pixelBuffer)
            return CIContext().createCGImage(ci, from: ci.extent)
        }

        /// Applies `OCRService` results: publishes the boxes (pixel-space,
        /// top-left) and rebuilds the transliteration map via the engine.
        @MainActor
        func applyRecognized(
            _ boxes: [OCRTextBox],
            engine: TextProcessingEngine,
            imagePixelSize: CGSize
        ) {
            lastImagePixelSize = imagePixelSize
            recognizedTexts = boxes.map {
                RecognizedTextBox(text: $0.text, pixelRect: $0.rect)
            }
            transliterationMap.removeAll(keepingCapacity: true)
            for box in recognizedTexts {
                if let processed = engine.process(box.text) {
                    transliterationMap[box.id] = processed
                }
            }
        }

        private func recognizeText(from image: UIImage) {
            guard let cgImage = image.cgImage else { return }
            let service = self.service
            let recognizer = self.recognizer
            let engine = self.engine
            let size = CGSize(width: cgImage.width, height: cgImage.height)
            Task { @MainActor in
                let boxes = await service.recognize(cgImage, recognizer: recognizer)
                self.applyRecognized(boxes, engine: engine, imagePixelSize: size)
            }
        }
    }
#endif
