package dev.veeso.biangbiangui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import dev.veeso.biangbiangui.ui.screens.camera.CameraLayoutPolicy.Side
import dev.veeso.biangbiangui.ui.screens.camera.CameraSplitLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1400dp-h1400dp-mdpi")
class CameraSplitLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun geometrySideAndDirectionChangesPreserveCamera() {
        var width by mutableStateOf(1024.dp)
        var height by mutableStateOf(744.dp)
        var builtIn by mutableStateOf(true)
        var side by mutableStateOf(Side.RIGHT)
        var cameraStarts = 0
        val directions = mutableMapOf<String, LayoutDirection>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.requiredSize(width, height)) {
                    CameraSplitLayout(builtIn, side, sidePane = {
                        directions["log"] = LocalLayoutDirection.current
                        Box(Modifier.fillMaxSize().testTag("log"))
                    }, cameraPane = {
                        DisposableEffect(Unit) {
                            cameraStarts++
                            onDispose {}
                        }
                        directions["camera"] = LocalLayoutDirection.current
                        Box(Modifier.fillMaxSize().testTag("camera"))
                    })
                }
            }
        }
        data class Case(val w: Int, val h: Int, val builtIn: Boolean,
                        val side: Side, val split: Boolean)
        val cases = listOf(
            Case(1024, 744, true, Side.RIGHT, true),
            Case(1024, 744, true, Side.LEFT, true),
            Case(744, 1024, true, Side.RIGHT, false),
            Case(1024, 744, false, Side.RIGHT, false),
            Case(1024, 744, true, Side.RIGHT, true),
        )
        cases.forEach { case ->
            compose.runOnIdle {
                width = case.w.dp; height = case.h.dp
                builtIn = case.builtIn; side = case.side
            }
            val camera = compose.onNodeWithTag("camera")
            camera.assertWidthIsEqualTo((case.w / if (case.split) 2 else 1).dp)
            if (case.split) {
                val log = compose.onNodeWithTag("log").assertExists()
                log.assertWidthIsEqualTo((case.w / 2).dp)
                val cameraX = camera.fetchSemanticsNode().boundsInRoot.left
                val logX = log.fetchSemanticsNode().boundsInRoot.left
                assertEquals(case.side == Side.RIGHT, cameraX > logX)
                assertEquals(LayoutDirection.Rtl, directions["log"])
            } else compose.onNodeWithTag("log").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, cameraStarts)
                assertEquals(LayoutDirection.Rtl, directions["camera"])
            }
        }
    }
}
