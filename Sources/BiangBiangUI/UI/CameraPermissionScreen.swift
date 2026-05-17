//
//  CameraPermissionScreen.swift
//  BiangBiangUI
//
//  Shown when camera permission is denied or restricted.
//

#if canImport(UIKit)
import SwiftUI
import UIKit

/// Full-screen placeholder shown when camera permission is denied or restricted.
///
/// All user-visible strings are injected by the caller (e.g. from `config.strings`).
public struct CameraPermissionScreen: View {
    @Environment(\.openURL) private var openURL

    let title: String
    let message: String
    let openSettingsLabel: String

    public init(title: String, message: String, openSettingsLabel: String) {
        self.title = title
        self.message = message
        self.openSettingsLabel = openSettingsLabel
    }

    public var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: "camera.slash")
        } description: {
            Text(message)
        } actions: {
            Button(openSettingsLabel, action: openSystemSettings)
                .buttonStyle(.borderedProminent)
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        openURL(url)
    }
}
#endif
