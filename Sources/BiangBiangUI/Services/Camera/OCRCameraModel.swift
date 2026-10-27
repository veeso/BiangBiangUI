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

    /// AVFoundation + OCR camera model, generalised for any `LanguageProfile`.
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

        /// Recognised text boxes from the most recent OCR pass.
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
        /// Increments whenever the live preview's orientation basis changes.
        /// Live OCR results are accepted only for the revision they captured.
        private(set) var liveOrientationRevision: UInt64 = 0
        /// Recognition results are accepted only within this camera-screen visit.
        private(set) var recognitionSessionRevision: UInt64 = 0
        /// Public model consumers can recognize immediately; explicit session end still invalidates callbacks.
        private var recognitionSessionActive = true
        /// In-memory session history; never persisted.
        private(set) var sessionLog = CameraSessionLog()
        /// IDs whose insertion highlight has not expired.
        private(set) var highlightedSessionLogIDs: Set<UUID> = []

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
        @ObservationIgnored private var rotationCoordinator:
            AVCaptureDevice.RotationCoordinator?
        @ObservationIgnored private var rotationAngleObservation: NSKeyValueObservation?
        @ObservationIgnored private var interfaceOrientation: UIInterfaceOrientation = .portrait
        /// Read and written only on `queue`, which serializes capture callbacks
        /// with connection rotation updates.
        @ObservationIgnored private(set) nonisolated(unsafe) var frameOrientationRevision: UInt64 = 0
        /// Read and written only on `queue` with `frameOrientationRevision`.
        @ObservationIgnored private nonisolated(unsafe) var frameSessionRevision: UInt64 = 0
        @ObservationIgnored private var currentAppliedRotationAngle: CGFloat = 0
        @ObservationIgnored private let rotationConnections: (() -> [any CameraRotationConnection])?
        @ObservationIgnored private let horizonAngle: (() -> CGFloat?)?
        @ObservationIgnored private let highlightSleep: @Sendable (Duration) async throws -> Void
        @ObservationIgnored private var highlightTasks: [UUID: Task<Void, Never>] = [:]
        /// Capture request revisions, keyed by AVFoundation's unique settings ID.
        /// Access is serialized on `queue`, including from the photo delegate.
        @ObservationIgnored private nonisolated(unsafe) var pendingPhotoSessionRevisions: [Int64: UInt64] = [:]
        /// Throttle timestamp. Confined to the serial `queue` (the only thing
        /// that reads/writes it is the nonisolated `captureOutput`).
        @ObservationIgnored private nonisolated(unsafe) var lastProcessingTime = Date.distantPast
        /// Mirrors `capturedImage != nil` for the nonisolated frame delegate so
        /// it can suspend live OCR without touching main-actor state. A benign
        /// cross-thread `Bool` race here costs at most one extra/dropped frame.
        @ObservationIgnored private nonisolated(unsafe) var captureSuspended = false
        @ObservationIgnored private var isConfigured = false

        var isLiveLandscape: Bool {
            capturedImage == nil && interfaceOrientation.isLandscape
        }

        // MARK: - Init

        public convenience init(profile: LanguageProfile, engine: TextProcessingEngine) {
            self.init(profile: profile, engine: engine, highlightSleep: { try await Task.sleep(for: $0) })
        }

        init(profile: LanguageProfile, engine: TextProcessingEngine,
             highlightSleep: @escaping @Sendable (Duration) async throws -> Void,
             rotationConnections: (() -> [any CameraRotationConnection])? = nil,
             horizonAngle: (() -> CGFloat?)? = nil)
        {
            self.rotationConnections = rotationConnections
            self.horizonAngle = horizonAngle
            self.highlightSleep = highlightSleep
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
            let settings = AVCapturePhotoSettings()
            let sessionRevision = recognitionSessionRevision
            queue.sync {
                pendingPhotoSessionRevisions[settings.uniqueID] = sessionRevision
            }
            output.capturePhoto(with: settings, delegate: self)
        }

        /// `AVCapturePhotoCaptureDelegate` — receives the compressed photo data.
        public nonisolated func photoOutput(
            _: AVCapturePhotoOutput,
            didFinishProcessingPhoto photo: AVCapturePhoto,
            error _: Error?
        ) {
            let sessionRevision = queue.sync {
                pendingPhotoSessionRevisions.removeValue(forKey: photo.resolvedSettings.uniqueID)
            }
            guard let sessionRevision,
                  let imageData = photo.fileDataRepresentation(),
                  let image = UIImage(data: imageData)
            else { return }
            Task { @MainActor in
                self.recognizeGalleryImage(image, sessionRevision: sessionRevision)
            }
        }

        // MARK: - Live video output

        /// `AVCaptureVideoDataOutputSampleBufferDelegate` — throttled live OCR.
        public nonisolated func captureOutput(
            _: AVCaptureOutput,
            didOutput sampleBuffer: CMSampleBuffer,
            from _: AVCaptureConnection
        ) {
            // `queue` also serializes connection updates. Capture this before
            // throttling or creating the image so actor scheduling cannot
            // label an old buffer with a newer orientation revision.
            let orientationRevision = frameOrientationRevision
            let sessionRevision = frameSessionRevision

            // Do not capture while a still image is set.
            if captureSuspended {
                return
            }

            let now = Date()
            guard now.timeIntervalSince(lastProcessingTime) > 1 else { return }
            lastProcessingTime = now

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
                  let cgImage = Self.cgImage(from: pixelBuffer) else { return }
            let service = service
            let recognizer = recognizer
            let engine = engine
            let size = CGSize(
                width: CVPixelBufferGetWidth(pixelBuffer),
                height: CVPixelBufferGetHeight(pixelBuffer)
            )
            Task { @MainActor in
                let boxes = await service.recognize(cgImage, recognizer: recognizer)
                self.applyLiveRecognized(
                    boxes,
                    engine: engine,
                    imagePixelSize: size,
                    sessionRevision: sessionRevision,
                    orientationRevision: orientationRevision
                )
            }
        }

        // MARK: - Gallery

        /// Loads `uiImage` as the active image and runs OCR on it.
        public func recognizeGalleryImage(_ uiImage: UIImage) {
            recognizeGalleryImage(uiImage, sessionRevision: recognitionSessionRevision)
        }

        func loadGalleryImage(sessionRevision: UInt64, load: @MainActor () async -> UIImage?) async {
            guard let image = await load() else { return }
            recognizeGalleryImage(image, sessionRevision: sessionRevision)
        }

        func recognizeGalleryImage(_ uiImage: UIImage, sessionRevision: UInt64) {
            guard recognitionSessionActive, sessionRevision == recognitionSessionRevision else { return }
            captureSuspended = true
            capturedImage = uiImage
            recognizeText(from: uiImage, sessionRevision: sessionRevision)
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

        /// Updates preview and live-buffer orientation from the hosting scene.
        /// Connection changes share the serial frame queue so a frame records
        /// the revision that was active when its buffer entered OCR.
        func updateInterfaceOrientation(_ orientation: UIInterfaceOrientation) {
            guard orientation != .unknown, orientation != interfaceOrientation else { return }

            interfaceOrientation = orientation
            liveOrientationRevision &+= 1
            clearLiveRecognitionForOrientationChange()
            applyCurrentConnectionOrientation(revision: liveOrientationRevision)
        }

        /// Called by `CameraPreview` when its layer becomes available. This
        /// also handles a preview attached after session configuration.
        func attachPreviewLayer(_ previewLayer: AVCaptureVideoPreviewLayer) {
            self.previewLayer = previewLayer
            configureRotationCoordinator()
            applyCurrentConnectionOrientation(revision: liveOrientationRevision)
        }

        @discardableResult
        func beginRecognitionSession() -> UInt64 {
            recognitionSessionRevision &+= 1
            recognitionSessionActive = true
            let revision = recognitionSessionRevision
            queue.sync {
                frameSessionRevision = revision
                pendingPhotoSessionRevisions.removeAll()
            }
            return revision
        }

        func endRecognitionSession() {
            recognitionSessionActive = false
            recognitionSessionRevision &+= 1
            let revision = recognitionSessionRevision
            queue.sync {
                frameSessionRevision = revision
                pendingPhotoSessionRevisions.removeAll()
            }
            clearSessionLog()
            recognizedTexts = []
            transliterationMap.removeAll()
        }

        func clearSessionLog() {
            var updatedLog = sessionLog
            updatedLog.clear()
            sessionLog = updatedLog
            highlightTasks.values.forEach { $0.cancel() }
            highlightTasks.removeAll()
            highlightedSessionLogIDs.removeAll()
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

            configureRotationCoordinator()
            applyCurrentConnectionOrientation(revision: liveOrientationRevision)
        }

        private func configureRotationCoordinator() {
            guard let device else { return }
            let coordinator = AVCaptureDevice.RotationCoordinator(
                device: device,
                previewLayer: previewLayer
            )
            rotationCoordinator = coordinator
            rotationAngleObservation = coordinator.observe(
                \.videoRotationAngleForHorizonLevelPreview,
                options: [.initial, .new]
            ) { [weak self] _, change in
                guard let angle = change.newValue else { return }
                Task { @MainActor in
                    self?.rotationCoordinatorDidChange(angle: angle)
                }
            }
        }

        func rotationCoordinatorDidChange(angle: CGFloat) {
            guard interfaceOrientation.isLandscape,
                  abs(angle - currentAppliedRotationAngle) > 0.01
            else { return }

            liveOrientationRevision &+= 1
            clearLiveRecognitionForOrientationChange()
            applyCurrentConnectionOrientation(
                revision: liveOrientationRevision,
                angle: angle
            )
        }

        private func clearLiveRecognitionForOrientationChange() {
            guard capturedImage == nil else { return }
            recognizedTexts = []
            transliterationMap.removeAll(keepingCapacity: true)
            lastImagePixelSize = .zero
        }

        private func applyCurrentConnectionOrientation(
            revision: UInt64,
            angle requestedAngle: CGFloat? = nil
        ) {
            let angle = requestedAngle ?? connectionRotationAngle(for: interfaceOrientation)
            let connections = rotationConnections?() ?? [previewLayer?.connection, videoOutput.connection(with: .video)].compactMap(\.self)

            queue.sync {
                if let angle {
                    for connection in connections where connection.isVideoRotationAngleSupported(angle) {
                        connection.videoRotationAngle = angle
                    }
                    frameOrientationRevision = revision
                } else {
                    frameOrientationRevision = revision
                }
            }

            if let angle {
                currentAppliedRotationAngle = angle
            }
        }

        private func connectionRotationAngle(
            for orientation: UIInterfaceOrientation
        ) -> CGFloat? {
            switch orientation {
            case .portrait, .portraitUpsideDown:
                // Preserve the established portrait capture path.
                0
            case .landscapeLeft, .landscapeRight:
                horizonAngle?() ?? rotationCoordinator?.videoRotationAngleForHorizonLevelPreview
            default:
                nil
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

        /// Shared `CIContext` reused across all live-frame conversions.
        /// `CIContext` is thread-safe, so a single static instance is correct
        /// and avoids the per-frame allocation cost of `CIContext()`.
        nonisolated static let ciContext = CIContext()

        /// Builds a still `CGImage` from the live buffer as delivered by
        /// `AVCaptureVideoDataOutput`. Any requested orientation has already
        /// been applied by its connection; this conversion does not rotate
        /// the pixels a second time.
        nonisolated static func cgImage(from pixelBuffer: CVPixelBuffer) -> CGImage? {
            let ci = CIImage(cvPixelBuffer: pixelBuffer)
            return Self.ciContext.createCGImage(ci, from: ci.extent)
        }

        /// Returns an upright `CGImage` for a still `UIImage`, baking
        /// `imageOrientation` into the pixels.
        ///
        /// `OCRService` deliberately takes only a `CGImage` (no orientation),
        /// mirroring the Android side which passes an upright `Bitmap`. The
        /// pipeline owns frame normalisation: the live path requests upright
        /// buffers from its video connection; this does the same for the
        /// captured/gallery still path so detected boxes and the
        /// pixel→view aspect-fit overlay are correct.
        nonisolated static func uprightCGImage(from image: UIImage) -> CGImage? {
            if image.imageOrientation == .up {
                return image.cgImage
            }
            let format = UIGraphicsImageRendererFormat.default()
            format.scale = image.scale
            let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
            let normalized = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: image.size))
            }
            return normalized.cgImage
        }

        /// Maps a top-left pixel rect onto an aspect-fit image inside the
        /// camera pane. Used for upright landscape live buffers, whose pixels
        /// already share the preview's orientation.
        nonisolated static func landscapeAspectFitRect(
            _ rect: CGRect,
            imageSize: CGSize,
            viewSize: CGSize
        ) -> CGRect {
            guard imageSize.width > 0, imageSize.height > 0,
                  viewSize.width > 0, viewSize.height > 0
            else { return .zero }

            let scale = min(
                viewSize.width / imageSize.width,
                viewSize.height / imageSize.height
            )
            let displayW = imageSize.width * scale
            let displayH = imageSize.height * scale
            let offsetX = (viewSize.width - displayW) * 0.5
            let offsetY = (viewSize.height - displayH) * 0.5

            return CGRect(
                x: offsetX + rect.minX * scale,
                y: offsetY + rect.minY * scale,
                width: rect.width * scale,
                height: rect.height * scale
            )
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

            let eligiblePairs = recognizedTexts.compactMap {
                box -> (original: String, transliteration: String)? in
                guard let transliteration = transliterationMap[box.id],
                      !transliteration.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                else { return nil }
                return (original: box.text, transliteration: transliteration)
            }
            var updatedLog = sessionLog
            let insertedIDs = updatedLog.ingest(eligiblePairs)
            sessionLog = updatedLog
            scheduleHighlights(for: insertedIDs)
        }

        @MainActor
        func applyLiveRecognized(
            _ boxes: [OCRTextBox],
            engine: TextProcessingEngine,
            imagePixelSize: CGSize,
            sessionRevision: UInt64,
            orientationRevision: UInt64
        ) {
            guard recognitionSessionActive, sessionRevision == recognitionSessionRevision,
                  orientationRevision == liveOrientationRevision
            else { return }
            applyRecognized(boxes, engine: engine, imagePixelSize: imagePixelSize)
        }

        private func scheduleHighlights(for insertedIDs: [UUID]) {
            let retainedIDs = Set(sessionLog.entries.map(\.id))
            let evictedIDs = highlightTasks.keys.filter { !retainedIDs.contains($0) }
            for id in evictedIDs {
                highlightTasks.removeValue(forKey: id)?.cancel()
            }
            highlightedSessionLogIDs.formIntersection(retainedIDs)

            for id in insertedIDs where retainedIDs.contains(id) {
                highlightedSessionLogIDs.insert(id)
                let sleep = highlightSleep
                highlightTasks[id] = Task { @MainActor [weak self] in
                    try? await sleep(.seconds(AppDesign.logHighlightDuration))
                    guard let self, !Task.isCancelled else { return }
                    self.highlightedSessionLogIDs.remove(id)
                    self.highlightTasks.removeValue(forKey: id)
                }
            }
        }

        private func recognizeText(from image: UIImage, sessionRevision: UInt64) {
            guard let cgImage = Self.uprightCGImage(from: image) else { return }
            let service = service
            let recognizer = recognizer
            let engine = engine
            let size = CGSize(width: cgImage.width, height: cgImage.height)
            Task { @MainActor in
                let boxes = await service.recognize(cgImage, recognizer: recognizer)
                guard self.recognitionSessionActive, sessionRevision == self.recognitionSessionRevision else { return }
                self.applyRecognized(boxes, engine: engine, imagePixelSize: size)
            }
        }
    }
#endif
