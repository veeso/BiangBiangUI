package dev.veeso.biangbiangui.ui.screens.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Places camera and side panes from content bounds without owning camera resources. */
@Composable
internal fun CameraSplitLayout(
    isBuiltInDisplay: Boolean,
    cameraSide: CameraLayoutPolicy.Side,
    sidePane: @Composable () -> Unit,
    cameraPane: @Composable () -> Unit,
) {
    val inheritedLayoutDirection = LocalLayoutDirection.current
    val containerLayoutDirection = if (cameraSide == CameraLayoutPolicy.Side.RIGHT) {
        LayoutDirection.Ltr
    } else {
        LayoutDirection.Rtl
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isSplit = CameraLayoutPolicy.isSplit(
            maxWidth.value,
            maxHeight.value,
            isBuiltInDisplay,
        )
        val paneWidth = if (isSplit) maxWidth / 2 else maxWidth

        CompositionLocalProvider(LocalLayoutDirection provides containerLayoutDirection) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Start,
            ) {
                if (isSplit) {
                    Box(Modifier.width(paneWidth).fillMaxHeight()) {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides inheritedLayoutDirection
                        ) { sidePane() }
                    }
                }

                Box(Modifier.width(paneWidth).fillMaxHeight().clipToBounds()) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides inheritedLayoutDirection
                    ) { cameraPane() }
                }
            }
        }
    }
}
