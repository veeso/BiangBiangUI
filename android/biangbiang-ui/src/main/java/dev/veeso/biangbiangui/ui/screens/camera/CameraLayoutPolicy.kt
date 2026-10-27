package dev.veeso.biangbiangui.ui.screens.camera

import android.view.Surface

object CameraLayoutPolicy {
    enum class Side { LEFT, RIGHT }

    const val MIN_SHORT_EDGE = 500f
    const val LENS_ON_RIGHT_ROTATION = Surface.ROTATION_90

    fun isSplit(widthDp: Float, heightDp: Float, isBuiltInDisplay: Boolean): Boolean {
        return isBuiltInDisplay && widthDp > heightDp &&
            minOf(widthDp, heightDp) >= MIN_SHORT_EDGE
    }

    fun cameraSide(displayRotation: Int): Side {
        if (displayRotation != Surface.ROTATION_90 &&
            displayRotation != Surface.ROTATION_270) return Side.RIGHT
        return if (displayRotation == LENS_ON_RIGHT_ROTATION) Side.RIGHT else Side.LEFT
    }

    fun landscapeSideRotation(displayRotation: Int, isLandscapeContent: Boolean): Int {
        if (!isLandscapeContent) return displayRotation
        return when (displayRotation) {
            Surface.ROTATION_0 -> Surface.ROTATION_90
            Surface.ROTATION_180 -> Surface.ROTATION_270
            else -> displayRotation
        }
    }
}
