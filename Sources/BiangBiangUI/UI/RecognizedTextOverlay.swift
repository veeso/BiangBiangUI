//
//  RecognizedTextOverlay.swift
//  BiangBiangUI
//
//  Port of RecognizedTextOverlay from BiangBiang Hanzi.
//  Generalisation notes vs. the reference:
//    • `hanzi`/`pinyin` parameters renamed to `original`/`transliteration`.
//    • `showPinyin` → `cameraModel.showTransliteration` (renamed in OCRCameraModel).
//    • Scale-ratio logic: `original.count / transliteration.count` — identical
//      semantics; the names changed but the formula is verbatim.
//    • Long-press save: `ctx.settings.addHistory(original:transliteration:variantId:)`
//      with `ctx.activeVariant?.id ?? ""`, replacing the reference's Cantonese/
//      Mandarin `HistoryVariant` enum (which is app-specific).
//    • Font scaling, toast wiring — verbatim from reference.
//    • Coordinate conversion: rewritten for the OCRService seam. Boxes now
//      arrive in source-image pixel space (top-left origin); the live-preview
//      path normalises back to Vision's metadata rect so the visible
//      placement is identical to the pre-seam behaviour.
//

#if canImport(UIKit)
    import AVFoundation
    import SwiftUI
    import UIKit

    /// Tappable overlay rendered on top of a recognised text box.
    ///
    /// - Tap: copies `textToDisplay` to the clipboard and briefly shows the
    ///   in-overlay checkmark + the global copied toast.
    /// - Long-press: saves the entry to history via `ctx.settings.addHistory` and
    ///   shows the global saved toast.
    ///
    /// Coordinate conversion (`pixelRectToViewRect` / `aspectFitRect`) maps
    /// `OCRService` pixel-space boxes onto the view; the live-preview branch
    /// preserves the pre-seam `layerRectConverted` placement exactly.
    public struct RecognizedTextOverlay: View {
        @Environment(BiangBiangContext.self) private var ctx
        @State private var isCopied = false
        @State private var measuredSize: CGSize = .zero

        let cameraModel: OCRCameraModel
        let original: String
        let transliteration: String
        let boundingBox: RecognizedTextBox
        let viewSize: CGSize

        private static let minFontSize: CGFloat = 12
        private static let minTapTarget: CGFloat = 44
        private static let edgeInset: CGFloat = 4
        private static let pillGap: CGFloat = 2

        public init(
            cameraModel: OCRCameraModel,
            original: String,
            transliteration: String,
            boundingBox: RecognizedTextBox,
            viewSize: CGSize
        ) {
            self.cameraModel = cameraModel
            self.original = original
            self.transliteration = transliteration
            self.boundingBox = boundingBox
            self.viewSize = viewSize
        }

        public var body: some View {
            let frame = pixelRectToViewRect(boundingBox.pixelRect, in: viewSize)
            let textToDisplay = cameraModel.showTransliteration ? transliteration : original
            let scaleRatio =
                cameraModel.showTransliteration
                    ? CGFloat(original.count) / CGFloat(max(transliteration.count, 1)) : 1.0
            let scaleFactor = min(max(scaleRatio, 0.6), 1.0)
            let fontSize = max(Self.minFontSize, frame.height * scaleFactor)

            let estimatedHeight = max(Self.minTapTarget, fontSize + 12)
            let pillHeight =
                measuredSize.height > 0 ? measuredSize.height : estimatedHeight
            let pillWidth =
                measuredSize.width > 0
                    ? measuredSize.width
                    : max(Self.minTapTarget, frame.width)

            let desiredX = frame.midX
            let desiredY = frame.minY - pillHeight * 0.5 - Self.pillGap
            let position = clampPosition(
                CGPoint(x: desiredX, y: desiredY),
                size: CGSize(width: pillWidth, height: pillHeight),
                in: viewSize
            )

            Button {
                copy(textToDisplay)
            } label: {
                HStack(spacing: 4) {
                    Text(textToDisplay)
                        .font(.system(size: fontSize, weight: .medium))
                        .foregroundStyle(.primary)
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                    if isCopied {
                        Image(systemName: "checkmark")
                            .font(.system(size: fontSize * 0.7, weight: .bold))
                            .foregroundStyle(Color.accentColor)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    RoundedRectangle(cornerRadius: AppDesign.cornerRadiusCompact)
                        .fill(.regularMaterial)
                        .shadow(
                            color: .black.opacity(0.25),
                            radius: 2,
                            x: 0,
                            y: 1
                        )
                )
                .scaleEffect(isCopied ? 1.08 : 1.0)
                .background(sizeReader)
            }
            .buttonStyle(.plain)
            .simultaneousGesture(
                LongPressGesture(minimumDuration: 0.3).onEnded { _ in
                    ctx.settings.addHistory(
                        original: original,
                        transliteration: transliteration,
                        variantId: ctx.activeVariant?.id ?? ""
                    )
                    cameraModel.showSavedToast = true
                    Task {
                        try? await Task.sleep(for: .seconds(1.5))
                        cameraModel.showSavedToast = false
                    }
                }
            )
            .frame(
                minWidth: Self.minTapTarget,
                minHeight: Self.minTapTarget
            )
            .contentShape(Rectangle())
            .position(position)
            .animation(
                .easeOut(duration: AppDesign.shortAnimation),
                value: isCopied
            )
            .sensoryFeedback(.impact(weight: .light), trigger: isCopied) { _, new in
                new
            }
            .accessibilityLabel(Text(textToDisplay))
            .accessibilityHint("Tap to copy, long-press to save to History")
        }

        // MARK: - Size reader

        private var sizeReader: some View {
            GeometryReader { proxy in
                Color.clear
                    .preference(key: SizePreferenceKey.self, value: proxy.size)
            }
            .onPreferenceChange(SizePreferenceKey.self) { newSize in
                Task { @MainActor in
                    if newSize != measuredSize {
                        measuredSize = newSize
                    }
                }
            }
        }

        // MARK: - Helpers (verbatim from reference)

        private func clampPosition(
            _ point: CGPoint,
            size: CGSize,
            in container: CGSize
        ) -> CGPoint {
            guard size.width > 0, size.height > 0,
                  container.width > size.width,
                  container.height > size.height
            else { return point }
            let halfW = size.width * 0.5
            let halfH = size.height * 0.5
            let x = min(
                max(point.x, halfW + Self.edgeInset),
                container.width - halfW - Self.edgeInset
            )
            let y = min(
                max(point.y, halfH + Self.edgeInset),
                container.height - halfH - Self.edgeInset
            )
            return CGPoint(x: x, y: y)
        }

        private func copy(_ text: String) {
            UIPasteboard.general.string = text

            isCopied = true
            cameraModel.showCopiedToast = true
            Task {
                try? await Task.sleep(for: .milliseconds(600))
                withAnimation(.easeOut(duration: AppDesign.shortAnimation)) {
                    isCopied = false
                }
                try? await Task.sleep(for: .milliseconds(400))
                withAnimation(.easeOut(duration: AppDesign.shortAnimation)) {
                    cameraModel.showCopiedToast = false
                }
            }
        }

        // MARK: - Pixel-rect → view coordinate conversion

        /// Convert an `OCRService` box (source-image pixel space, top-left
        /// origin) to a view-space rect.
        ///
        /// Two display contexts, mirroring the pre-seam behaviour:
        /// - Captured/gallery still: the image is shown `scaledToFit`, so the
        ///   box is mapped onto that aspect-fit rect using the source image's
        ///   pixel size.
        /// - Live preview: `AVCaptureVideoPreviewLayer` owns the sensor→view
        ///   transform. We normalise `pixelRect` back to Vision's metadata
        ///   rect (0–1, bottom-left) and feed the *unchanged*
        ///   `layerRectConverted(fromMetadataOutputRect:)` + x-flip path, so
        ///   the visible placement is identical to before the OCRService seam.
        private func pixelRectToViewRect(_ rect: CGRect, in size: CGSize) -> CGRect {
            let imageSize = cameraModel.lastImagePixelSize
            guard imageSize.width > 0, imageSize.height > 0 else { return .zero }

            if cameraModel.capturedImage != nil {
                return aspectFitRect(rect, imageSize: imageSize, in: size)
            }

            if let previewLayer = cameraModel.previewLayer {
                // Invert DefaultOCRService.pixelRect(fromVisionNormalized:):
                // pixel (top-left) → Vision metadata rect (0–1, bottom-left).
                let nW = rect.width / imageSize.width
                let nH = rect.height / imageSize.height
                let nX = rect.minX / imageSize.width
                let nY = 1 - rect.minY / imageSize.height - nH
                let normalized = CGRect(x: nX, y: nY, width: nW, height: nH)

                let videoRect = previewLayer.layerRectConverted(
                    fromMetadataOutputRect: normalized
                )
                let flippedX = size.width - videoRect.origin.x - videoRect.width
                return CGRect(
                    x: flippedX,
                    y: videoRect.origin.y,
                    width: videoRect.width,
                    height: videoRect.height
                )
            }

            // Fallback when preview layer is unavailable: aspect-fit map.
            return aspectFitRect(rect, imageSize: imageSize, in: size)
        }

        /// Map a pixel-space rect (top-left origin) onto the `scaledToFit`
        /// display rect of an image of `imageSize` inside `viewSize`. No
        /// y-flip: pixel space and view space share a top-left origin.
        private func aspectFitRect(
            _ rect: CGRect,
            imageSize: CGSize,
            in viewSize: CGSize
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
    }

    // MARK: - SizePreferenceKey (private, verbatim from reference)

    private struct SizePreferenceKey: PreferenceKey {
        static let defaultValue: CGSize = .zero

        static func reduce(value: inout CGSize, nextValue: () -> CGSize) {
            value = nextValue()
        }
    }
#endif
