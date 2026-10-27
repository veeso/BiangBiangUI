package dev.veeso.biangbiangui

import android.view.Surface
import dev.veeso.biangbiangui.ui.screens.camera.CameraLayoutPolicy as Policy
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraLayoutPolicyTest {
    @Test fun splitTruthTable() {
        data class Case(val w: Float, val h: Float,
                        val builtIn: Boolean, val expected: Boolean)
        val cases = listOf(
            Case(744f, 1024f, true, false), Case(932f, 430f, true, false),
            Case(1024f, 744f, true, true), Case(1024f, 744f, false, false),
            Case(1000f, 499f, true, false), Case(1000f, 500f, true, true),
            Case(1000f, 501f, true, true), Case(500f, 500f, true, false),
            Case(501f, 500f, true, true), Case(0f, 0f, true, false),
        )
        cases.forEach {
            assertEquals(it.expected, Policy.isSplit(it.w, it.h, it.builtIn))
        }
    }

    @Test fun physicalSideUsesNamedOrientation() {
        val right = Policy.LENS_ON_RIGHT_ROTATION
        val opposite = if (right == Surface.ROTATION_90)
            Surface.ROTATION_270 else Surface.ROTATION_90
        assertEquals(Policy.Side.RIGHT, Policy.cameraSide(right))
        assertEquals(Policy.Side.LEFT, Policy.cameraSide(opposite))
        assertEquals(Policy.Side.RIGHT, Policy.cameraSide(-1))
    }

    @Test fun landscapeTabletRotationsUseTheSamePhysicalSideMapping() {
        val rightRotation = Policy.landscapeSideRotation(Surface.ROTATION_0, true)
        val leftRotation = Policy.landscapeSideRotation(Surface.ROTATION_180, true)
        assertEquals(Surface.ROTATION_90, rightRotation)
        assertEquals(Surface.ROTATION_270, leftRotation)
        assertEquals(Policy.Side.RIGHT, Policy.cameraSide(rightRotation))
        assertEquals(Policy.Side.LEFT, Policy.cameraSide(leftRotation))
        assertEquals(Surface.ROTATION_180,
            Policy.landscapeSideRotation(Surface.ROTATION_180, false))
    }
}
