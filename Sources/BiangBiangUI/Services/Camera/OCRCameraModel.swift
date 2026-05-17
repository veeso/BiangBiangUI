//
//  OCRCameraModel.swift
//  BiangBiangUI
//

#if canImport(UIKit)
@preconcurrency import AVFoundation
import Foundation
import Observation
import UIKit
import Vision

/// Text box identified by the Vision OCR pass.
///
/// `boundingBox` is in normalised coordinates (0–1) with Vision's
/// bottom-left origin.
public struct RecognizedTextBox: Identifiable, Sendable {
    public let id = UUID()
    public let text: String
    /// Normalised bounding box (0–1). Vision origin is bottom-left.
    public let boundingBox: CGRect
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
public final class OCRCameraModel: NSObject, @preconcurrency AVCapturePhotoCaptureDelegate,
    @preconcurrency AVCaptureVideoDataOutputSampleBufferDelegate
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
    @ObservationIgnored private let engine: TextProcessingEngine

    @ObservationIgnored private var device: AVCaptureDevice?
    @ObservationIgnored private var zoomSwitchOverFactor: CGFloat = 1.0
    @ObservationIgnored private var maxUIZoom: CGFloat = 1.0

    @ObservationIgnored private let videoOutput = AVCaptureVideoDataOutput()
    @ObservationIgnored private let output = AVCapturePhotoOutput()
    @ObservationIgnored private let queue = DispatchQueue(
        label: "camera.frame.processing"
    )
    @ObservationIgnored private var textRequest: VNRecognizeTextRequest!
    @ObservationIgnored public var previewLayer: AVCaptureVideoPreviewLayer?
    @ObservationIgnored private var lastProcessingTime = Date.distantPast
    @ObservationIgnored private var isConfigured = false

    // MARK: - Init

    public init(profile: LanguageProfile, engine: TextProcessingEngine) {
        self.profile = profile
        self.engine = engine
    }

    // MARK: - Photo capture

    /// Captures a still photo and runs OCR on it.
    public func capturePhoto() {
        output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
    }

    /// `AVCapturePhotoCaptureDelegate` — receives the compressed photo data.
    public func photoOutput(
        _: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error _: Error?
    ) {
        guard let imageData = photo.fileDataRepresentation(),
              let image = UIImage(data: imageData)
        else { return }
        capturedImage = image
        recognizeText(from: image)
    }

    // MARK: - Live video output

    /// `AVCaptureVideoDataOutputSampleBufferDelegate` — throttled live OCR.
    public func captureOutput(
        _: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from _: AVCaptureConnection
    ) {
        // Do not capture while a still image is set.
        if capturedImage != nil { return }

        let now = Date()
        guard now.timeIntervalSince(lastProcessingTime) > 1 else { return }
        lastProcessingTime = now

        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let request = makeTextRecognitionRequest()
        let handler = VNImageRequestHandler(
            cvPixelBuffer: pixelBuffer,
            orientation: .up,
            options: [:]
        )

        do {
            try handler.perform([request])
        } catch {
            print("⚠️ Vision error:", error)
        }
    }

    // MARK: - Gallery

    /// Loads `uiImage` as the active image and runs OCR on it.
    public func recognizeGalleryImage(_ uiImage: UIImage) {
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

    // MARK: - Private: Vision OCR

    /// Builds a `VNRecognizeTextRequest` whose completion handler posts
    /// results back to `@MainActor`.
    private func makeTextRecognitionRequest() -> VNRecognizeTextRequest {
        let request = VNRecognizeTextRequest { [weak self] req, _ in
            guard let self,
                  let results = req.results as? [VNRecognizedTextObservation]
            else { return }

            let boxes: [RecognizedTextBox] = results.compactMap {
                guard let top = $0.topCandidates(1).first else { return nil }
                return RecognizedTextBox(text: top.string, boundingBox: $0.boundingBox)
            }

            // Capture the engine value before crossing the actor boundary.
            let engine = self.engine

            Task { @MainActor in
                self.recognizedTexts = boxes
                self.transliterationMap.removeAll(keepingCapacity: true)
                for box in boxes {
                    if let processed = engine.process(box.text) {
                        self.transliterationMap[box.id] = processed
                    }
                }
            }
        }
        request.recognitionLanguages = Self.recognitionLanguages(for: profile.ocrRecognizer)
        request.recognitionLevel = .accurate
        return request
    }

    /// Maps an `OCRRecognizer` to the Vision language identifiers for the
    /// active Vision revision. Arabic falls back to `["ar-SA","ar"]` if no
    /// `ar*` tag is supported (port of Harakat Lens's `preferredArabicLanguages()`).
    private static func recognitionLanguages(for recognizer: OCRRecognizer) -> [String] {
        switch recognizer {
        case .chinese:
            return ["zh-Hans", "zh-Hant"]
        case .latin:
            return ["en-US"]
        case .arabic:
            return preferredArabicLanguages()
        case .japanese:
            return ["ja"]
        case .korean:
            return ["ko"]
        }
    }

    /// Filters the languages supported by Vision Revision 3 for Arabic tags.
    /// Ported verbatim from Harakat Lens `CameraModel.preferredArabicLanguages()`.
    private static func preferredArabicLanguages() -> [String] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.revision = VNRecognizeTextRequestRevision3
        let supported = (try? request.supportedRecognitionLanguages()) ?? []
        let arabic = supported.filter { $0.hasPrefix("ar") }
        return arabic.isEmpty ? ["ar-SA", "ar"] : arabic
    }

    private func recognizeText(from image: UIImage) {
        guard let cgImage = image.cgImage else { return }
        let request = makeTextRecognitionRequest()
        let handler = VNImageRequestHandler(
            cgImage: cgImage,
            orientation: cgOrientation(from: image.imageOrientation),
            options: [:]
        )
        try? handler.perform([request])
    }

    private func cgOrientation(
        from uiOrientation: UIImage.Orientation
    ) -> CGImagePropertyOrientation {
        switch uiOrientation {
        case .up: .up
        case .down: .down
        case .left: .left
        case .right: .right
        case .upMirrored: .upMirrored
        case .downMirrored: .downMirrored
        case .leftMirrored: .leftMirrored
        case .rightMirrored: .rightMirrored
        @unknown default: .up
        }
    }
}
#endif
