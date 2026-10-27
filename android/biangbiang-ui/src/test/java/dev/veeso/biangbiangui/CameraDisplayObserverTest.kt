package dev.veeso.biangbiangui

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.veeso.biangbiangui.ui.screens.camera.CameraDisplayObserver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class CameraDisplayObserverTest {
    @get:Rule val compose = createComposeRule()

    @Test fun followsAttachmentRotationAndExternalDisplayChanges() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val manager = base.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val builtIn = manager.getDisplay(Display.DEFAULT_DISPLAY)
        val externalId = ShadowDisplayManager.addDisplay("w1000dp-h700dp-land")
        val external = manager.getDisplay(externalId)
        val context = mutableStateOf(base.createDisplayContext(builtIn))
        val configuration = mutableStateOf(Configuration(base.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        })
        val observations = mutableListOf<Pair<Boolean, Int>>()
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context.value,
                                     LocalConfiguration provides configuration.value) {
                CameraDisplayObserver { internal, rotation -> observations += internal to rotation }
            }
        }
        compose.runOnIdle { assertEquals(true to Surface.ROTATION_0, observations.last()) }
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            shadowOf(builtIn).setRotation(Surface.ROTATION_90)
        }
        compose.runOnIdle { assertEquals(true to Surface.ROTATION_90, observations.last()) }
        compose.runOnIdle { shadowOf(builtIn).setRotation(Surface.ROTATION_270) }
        compose.runOnIdle { assertEquals(true to Surface.ROTATION_270, observations.last()) }
        compose.runOnIdle { context.value = base.createDisplayContext(external) }
        compose.runOnIdle { assertEquals(false, observations.last().first) }
        compose.runOnIdle {
            context.value = base.createDisplayContext(builtIn)
            configuration.value = Configuration(configuration.value).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
            }
            shadowOf(builtIn).setRotation(Surface.ROTATION_0)
        }
        compose.runOnIdle { assertEquals(true to Surface.ROTATION_0, observations.last()) }
        compose.runOnIdle { shadowOf(builtIn).setRotation(Surface.ROTATION_0) }
        compose.runOnIdle { assertEquals(true to Surface.ROTATION_0, observations.last()) }
        ShadowDisplayManager.removeDisplay(externalId)
    }
}
