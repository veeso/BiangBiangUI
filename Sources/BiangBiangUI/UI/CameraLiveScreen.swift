//
//  CameraLiveScreen.swift
//  BiangBiangUI
//
//  Config-driven port of CameraLiveView from BiangBiang Hanzi.
//  Generalisation notes vs. the reference:
//    • All user-facing strings come from ctx.config.strings
//      (tapToCopyLongPressToSave, textCopied, savedToHistory).
//    • `showPinyin` → `showTransliteration` (renamed in OCRCameraModel).
//    • Toggle-transliteration button uses `ctx.config.branding.logoAssetName`
//      instead of the hard-coded "BiangBiang" asset name.
//    • `RecognizedTextOverlay` takes `hanzi`/`pinyin` renamed to match; the
//      library struct receives `original` and `transliteration` arguments.
//    • Plugin camera seam: on every recognizedTexts change, for each box with
//      a transliteration, a `ProcessedText(.camera)` is built and dispatched to
//      `plugin.onProcessedText(_:)`.  The first plugin that returns a non-nil
//      `inlineResultView(for:)` is presented as a half-sheet.  When no plugin
//      matches the sheet stays nil and the behavior is identical to the reference.
//    • `CopyToast(message:)` now takes the localised string from ctx.config.strings.
//    • All timings, animations and layout are verbatim from the reference.
//

#if canImport(UIKit)
import PhotosUI
import SwiftUI

/// Live camera surface with OCR overlays and controls.
///
/// Receives an already-constructed `OCRCameraModel` from `CameraScreen`.
public struct CameraLiveScreen: View {
    @Environment(BiangBiangContext.self) private var ctx
    @Bindable var cameraModel: OCRCameraModel

    @State private var selectedItem: PhotosPickerItem?
    @State private var pinchBaseZoom: CGFloat = 1.0

    // Plugin sheet: the first non-nil inline view for the most-recently
    // processed recognised texts, or nil when no plugin claims it.
    @State private var pluginSheetContent: PluginSheetItem?

    public init(cameraModel: OCRCameraModel) {
        self.cameraModel = cameraModel
    }

    public var body: some View {
        GeometryReader { geo in
            ZStack {
                CameraPreview(
                    session: cameraModel.session,
                    cameraModel: cameraModel
                )
                .ignoresSafeArea()

                if let image = cameraModel.capturedImage {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black)
                        .ignoresSafeArea()
                }

                ForEach(cameraModel.recognizedTexts) { box in
                    if let transliteration = cameraModel.transliterationMap[box.id] {
                        RecognizedTextOverlay(
                            cameraModel: cameraModel,
                            original: box.text,
                            transliteration: transliteration,
                            boundingBox: box,
                            viewSize: geo.size
                        )
                    }
                }

                if !cameraModel.recognizedTexts.isEmpty {
                    VStack {
                        Text(ctx.config.strings.tapToCopyLongPressToSave)
                            .font(.caption2)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(.ultraThinMaterial, in: Capsule())
                            .padding(.top, 8)
                        Spacer()
                    }
                    .allowsHitTesting(false)
                }

                VStack {
                    Spacer()
                    if cameraModel.capturedImage == nil
                        && !cameraModel.availableZoomPresets.isEmpty
                    {
                        zoomPresetBar
                    }
                    controlBar
                }

                if cameraModel.showCopiedToast {
                    VStack {
                        Spacer()
                        CopyToast(message: ctx.config.strings.textCopied)
                            .padding(.bottom, AppDesign.bottomToolbarPadding)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
                }

                if cameraModel.showSavedToast {
                    VStack {
                        Spacer()
                        CopyToast(message: ctx.config.strings.savedToHistory)
                            .padding(.bottom, AppDesign.bottomToolbarPadding + 44)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
                }
            }
            .animation(
                .easeOut(duration: AppDesign.toastAnimation),
                value: cameraModel.showCopiedToast
            )
            .animation(
                .easeOut(duration: AppDesign.toastAnimation),
                value: cameraModel.showSavedToast
            )
        }
        .simultaneousGesture(
            MagnifyGesture()
                .onChanged { value in
                    cameraModel.setZoom(pinchBaseZoom * value.magnification)
                }
                .onEnded { _ in
                    pinchBaseZoom = cameraModel.zoomFactor
                }
        )
        .task {
            await cameraModel.checkPermissionsAndStart()
        }
        // Plugin camera seam: fire onProcessedText for each box whenever the
        // recognised set changes, then present the first inline view as a sheet.
        // Inert when ctx.config.plugins is empty (Chinese/no-plugin apps).
        .onChange(of: cameraModel.recognizedTexts) { _, boxes in
            dispatchPluginHooks(for: boxes)
        }
        .sheet(item: $pluginSheetContent) { item in
            item.view
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - Zoom preset bar (verbatim from reference)

    private var zoomPresetBar: some View {
        HStack(spacing: AppDesign.stackSpacing) {
            ForEach(cameraModel.availableZoomPresets, id: \.self) { preset in
                let isActive = abs(cameraModel.zoomFactor - preset) < 0.05
                Button {
                    cameraModel.setZoom(preset)
                    pinchBaseZoom = preset
                } label: {
                    Text("\(Int(preset))x")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(
                            width: AppDesign.tapTarget,
                            height: AppDesign.tapTarget
                        )
                        .background(
                            Circle()
                                .fill(
                                    isActive
                                        ? Color.red.opacity(0.85)
                                        : Color.gray.opacity(0.6)
                                )
                                .shadow(radius: 3)
                        )
                }
                .accessibilityLabel("Zoom \(Int(preset))x")
            }
        }
        .padding(.bottom, AppDesign.stackSpacing)
    }

    // MARK: - Control bar (verbatim from reference)

    private var controlBar: some View {
        HStack {
            if cameraModel.capturedImage != nil {
                CircularIconButton(
                    title: "Retake photo",
                    systemImage: "xmark.circle.fill",
                    action: cameraModel.deleteCapturedImage
                )
            } else {
                toggleTransliterationButton
                CircularIconButton(
                    title: "Take photo",
                    systemImage: "camera.fill",
                    action: cameraModel.capturePhoto
                )
                galleryPickerButton
            }
        }
        .padding(.bottom, AppDesign.bottomToolbarPadding)
    }

    // MARK: - Toggle-transliteration button

    /// Generalisation: asset name comes from `ctx.config.branding.logoAssetName`
    /// instead of the hard-coded "BiangBiang" string in the reference.
    private var toggleTransliterationButton: some View {
        Button {
            cameraModel.showTransliteration.toggle()
        } label: {
            Image(ctx.config.branding.logoAssetName)
                .resizable()
                .scaledToFit()
                .frame(width: 28, height: 28)
                .foregroundStyle(.white)
                .padding()
                .background(
                    Circle()
                        .fill(
                            cameraModel.showTransliteration
                                ? Color.red.opacity(0.8)
                                : Color.gray.opacity(0.8)
                        )
                        .shadow(radius: AppDesign.buttonShadow)
                )
        }
        .animation(
            .easeInOut(duration: AppDesign.shortAnimation),
            value: cameraModel.showTransliteration
        )
        .accessibilityLabel("Toggle Transliteration")
    }

    // MARK: - Gallery picker button (verbatim from reference)

    private var galleryPickerButton: some View {
        PhotosPicker(
            selection: $selectedItem,
            matching: .images,
            photoLibrary: .shared()
        ) {
            Image(systemName: "photo.on.rectangle")
                .font(.system(size: 24))
                .foregroundStyle(.white)
                .padding()
                .background(
                    Circle()
                        .fill(.gray)
                        .shadow(radius: AppDesign.buttonShadow)
                )
        }
        .accessibilityLabel("Scan photo from gallery")
        .onChange(of: selectedItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let image = UIImage(data: data)
                {
                    cameraModel.recognizeGalleryImage(image)
                }
                selectedItem = nil
            }
        }
    }

    // MARK: - Plugin seam (camera)

    /// Fires `onProcessedText` on every plugin for each box that has a
    /// transliteration entry, then looks for an inline result view.
    ///
    /// This is inert (no visible effect) when `ctx.config.plugins` is empty or
    /// when no plugin returns a non-nil `inlineResultView(for:)`.
    private func dispatchPluginHooks(for boxes: [RecognizedTextBox]) {
        guard !ctx.config.plugins.isEmpty else { return }
        let variantId = ctx.activeVariant?.id ?? ""

        var firstSheetView: AnyView?

        for box in boxes {
            guard let transliteration = cameraModel.transliterationMap[box.id] else { continue }
            let pt = ProcessedText(
                original: box.text,
                transliteration: transliteration,
                variantId: variantId,
                source: .camera
            )
            for plugin in ctx.config.plugins {
                plugin.onProcessedText(pt)
                if firstSheetView == nil {
                    firstSheetView = plugin.inlineResultView(for: pt)
                }
            }
        }

        // Only update state when something changed to avoid spurious redraws.
        if firstSheetView != nil {
            pluginSheetContent = firstSheetView.map { PluginSheetItem(view: $0) }
        }
    }
}

// MARK: - PluginSheetItem

/// Identifiable wrapper around an `AnyView` so it can be used with
/// `.sheet(item:)`.  A new UUID is minted each time a plugin view is presented,
/// which forces the sheet to refresh when the content changes.
private struct PluginSheetItem: Identifiable {
    let id = UUID()
    let view: AnyView
}

// MARK: - CircularIconButton (private, verbatim from reference)

private struct CircularIconButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(title, systemImage: systemImage, action: action)
            .labelStyle(.iconOnly)
            .font(.system(size: 24))
            .foregroundStyle(.white)
            .padding()
            .background(
                Circle()
                    .fill(.gray)
                    .shadow(radius: AppDesign.buttonShadow)
            )
    }
}
#endif
