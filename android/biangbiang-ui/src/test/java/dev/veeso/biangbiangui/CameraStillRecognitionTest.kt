package dev.veeso.biangbiangui

import android.graphics.Bitmap
import dev.veeso.biangbiangui.services.camera.OcrBox
import dev.veeso.biangbiangui.ui.screens.camera.CameraSessionLogState
import dev.veeso.biangbiangui.ui.screens.camera.acceptCameraImage
import dev.veeso.biangbiangui.ui.screens.camera.recognizeCameraImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CameraStillRecognitionTest {
    @Test fun delayedImagesAndStillOcrCannotEnterNextVisit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val state = CameraSessionLogState { testScheduler.currentTime }
        try {
            val old = state.beginSession()
            val image = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            var captured: Bitmap? = null
            val pendingImage = CompletableDeferred<Bitmap>()
            val pendingOcr = CompletableDeferred<List<OcrBox>>()
            var overlays = emptyList<OcrBox>()
            var pluginCalls = 0
            launch {
                acceptCameraImage(state, old, pendingImage.await()) { captured = it }
            }
            launch {
                recognizeCameraImage(state, old, { pendingOcr.await() }) {
                    overlays = it
                    pluginCalls++
                }
            }
            runCurrent()
            state.endSession(recreating = false)
            val current = state.beginSession()
            pendingImage.complete(image)
            pendingOcr.complete(listOf(OcrBox("旧", "old", 0, 0, 10, 10)))
            runCurrent()
            assertNull(captured)
            assertTrue(overlays.isEmpty())
            assertTrue(state.log.entries.isEmpty())
            assertEquals(0, pluginCalls)
            acceptCameraImage(state, current, image) { captured = it }
            recognizeCameraImage(state, current, { listOf(OcrBox("你", "ni", 0, 0, 10, 10)) }) {
                overlays = it
                pluginCalls++
            }
            assertSame(image, captured)
            assertEquals("你", overlays.single().text)
            assertEquals("你", state.log.entries.single().original)
            assertEquals(1, pluginCalls)
        } finally {
            state.endSession(recreating = false)
            Dispatchers.resetMain()
        }
    }
}
