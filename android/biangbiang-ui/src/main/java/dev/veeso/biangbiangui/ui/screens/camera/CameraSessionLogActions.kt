package dev.veeso.biangbiangui.ui.screens.camera

import android.content.ClipData
import android.content.ClipboardManager
import dev.veeso.biangbiangui.services.SettingsStore
import dev.veeso.biangbiangui.services.camera.CameraSessionLog

internal fun copyCameraLogEntry(
    entry: CameraSessionLog.Entry,
    showTransliteration: Boolean,
    clipboard: ClipboardManager,
    showToast: () -> Unit,
) {
    val text = if (showTransliteration) entry.transliteration else entry.original
    clipboard.setPrimaryClip(ClipData.newPlainText("OCR text", text))
    showToast()
}

internal suspend fun saveCameraLogEntry(
    entry: CameraSessionLog.Entry,
    settings: SettingsStore,
    variantId: String,
    showToast: () -> Unit,
) {
    settings.addHistory(entry.original, entry.transliteration, variantId)
    showToast()
}
