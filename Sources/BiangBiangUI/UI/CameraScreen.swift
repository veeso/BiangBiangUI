//
//  CameraScreen.swift
//  BiangBiangUI
//
//  Config-driven port of CameraModeView from BiangBiang Hanzi.
//  Generalisation notes vs. the reference:
//    • `CameraModel()` → `OCRCameraModel(profile: ctx.activeProfile, engine: ctx.engine)`.
//      The model is held as `@State private var cameraModel: OCRCameraModel?` and
//      built lazily in `.task` because `@Environment` values are not accessible at
//      `@State` property-initialiser time (Swift evaluates those expressions before
//      the environment is injected). Once built, the optional is never nil during
//      the active lifetime of the view.
//    • Permission screen: `CameraPermissionScreen` with strings from `ctx.config.strings`
//      rather than the hard-coded `CameraPermissionView`.
//    • Inner live view: `CameraLiveScreen` (renamed from `CameraLiveView`).
//

#if canImport(UIKit)
import SwiftUI

/// Top-level camera entry point.
///
/// Routes to `CameraPermissionScreen` when access is denied, otherwise to
/// `CameraLiveScreen`. All user-facing strings and configuration come from
/// `BiangBiangContext` in the SwiftUI environment.
///
/// Usage:
///
///     TabView {
///         CameraScreen()
///             .tabItem { Label(ctx.config.strings.tabCamera, systemImage: "camera") }
///     }
///     .environment(context)
public struct CameraScreen: View {
    @Environment(BiangBiangContext.self) private var ctx

    /// Held as an optional so it can be built in `.task` once the environment
    /// is available.  The reference app used `@State private var cameraModel = CameraModel()`
    /// with a no-argument initialiser; because `OCRCameraModel` requires the
    /// profile and engine from `ctx`, we defer construction to `.task`.
    @State private var cameraModel: OCRCameraModel?

    public init() {}

    public var body: some View {
        Group {
            if let cameraModel {
                if cameraModel.missingCameraPermission {
                    CameraPermissionScreen(
                        title: ctx.config.strings.cameraDisabledTitle,
                        message: ctx.config.strings.cameraDisabledMessage,
                        openSettingsLabel: ctx.config.strings.openSettings
                    )
                } else {
                    CameraLiveScreen(cameraModel: cameraModel)
                }
            }
        }
        .task {
            // Build the model exactly once; `.task` re-runs only when the
            // task identity changes (none here), so this guard is belt-and-suspenders.
            guard cameraModel == nil else { return }
            cameraModel = OCRCameraModel(profile: ctx.activeProfile, engine: ctx.engine)
        }
    }
}
#endif
