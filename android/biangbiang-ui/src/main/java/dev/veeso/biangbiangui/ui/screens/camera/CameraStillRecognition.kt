package dev.veeso.biangbiangui.ui.screens.camera

import android.graphics.Bitmap
import dev.veeso.biangbiangui.services.camera.OcrBox

internal fun acceptCameraImage(
    state: CameraSessionLogState,
    revision: Long,
    image: Bitmap?,
    onAccepted: (Bitmap?) -> Unit,
) {
    if (state.isCurrentSession(revision)) onAccepted(image)
}

internal suspend fun recognizeCameraImage(
    state: CameraSessionLogState,
    revision: Long,
    recognize: suspend () -> List<OcrBox>,
    onAccepted: (List<OcrBox>) -> Unit,
) {
    if (!state.isCurrentSession(revision)) return
    val boxes = recognize()
    if (state.accept(boxes, revision)) onAccepted(boxes)
}
