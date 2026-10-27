import UIKit

@MainActor
enum CameraSessionLogActions {
    static func copy(_ entry: CameraSessionLog.Entry, camera: OCRCameraModel,
                     writeClipboard: (String) -> Void = { UIPasteboard.general.string = $0 })
    {
        writeClipboard(camera.showTransliteration ? entry.transliteration : entry.original)
        camera.showCopiedToast = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.5))
            camera.showCopiedToast = false
        }
    }

    static func save(_ entry: CameraSessionLog.Entry, camera: OCRCameraModel, settings: SettingsStore, variantID: String) {
        settings.addHistory(
            original: entry.original,
            transliteration: entry.transliteration,
            variantId: variantID
        )
        camera.showSavedToast = true
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.5))
            camera.showSavedToast = false
        }
    }
}
