package dev.veeso.biangbiangui

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import dev.veeso.biangbiangui.services.SettingsStore
import dev.veeso.biangbiangui.ui.screens.camera.copyCameraLogEntry
import dev.veeso.biangbiangui.ui.screens.camera.saveCameraLogEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.rules.TemporaryFolder
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.veeso.biangbiangui.config.UiStrings
import dev.veeso.biangbiangui.services.camera.CameraSessionLog
import dev.veeso.biangbiangui.ui.screens.camera.CameraSessionLogPane
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class CameraSessionLogPaneTest(private val showLatin: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "showLatin={0}")
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun actionsHaveIndependentEffects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val settings = SettingsStore(PreferenceDataStoreFactory.create {
            tmp.newFile("history.preferences_pb")
        })
        val log = mutableStateOf(CameraSessionLog().apply { ingest(listOf("你" to "ni")) })
        val actions = mutableListOf<String>()
        var copied = false
        var saved = false
        compose.setContent {
            MaterialTheme {
                CameraSessionLogPane(
                    log = log.value,
                    strings = UiStrings(copy = "Copia", save = "Salva",
                        cameraLogTitle = "Testi trovati", cameraLogClear = "Cancella",
                        cameraLogEmpty = "Nessun testo",
                        tapToCopyLongPressToSave = "Tocca per copiare, tieni premuto per salvare"), accent = Color.Red,
                    highlightedIDs = emptySet(),
                    onCopy = { copyCameraLogEntry(it, showLatin, clipboard) { copied = true; actions += "copy" } },
                    onSave = { entry -> runBlocking {
                        saveCameraLogEntry(entry, settings, "active") { saved = true; actions += "save" }
                    } },
                    onClear = { actions += "clear"; log.value = CameraSessionLog() },
                )
            }
        }
        compose.onNodeWithText("Testi trovati · 1").assertIsDisplayed()
        compose.onNodeWithText("Cancella").assertIsDisplayed()
        compose.onNodeWithText("Nessun testo").assertDoesNotExist()
        compose.onNodeWithContentDescription("你, ni; Tocca per copiare, tieni premuto per salvare").assertExists()
        compose.onNodeWithText("你").performClick()
        compose.runOnIdle {
            assertEquals(if (showLatin) "ni" else "你", clipboard.primaryClip?.getItemAt(0)?.text)
            assertEquals(true, copied)
            assertEquals(false, saved)
            assertEquals(0, runBlocking { settings.history.first().size })
            copied = false
        }
        compose.onNodeWithText("你").performTouchInput { longClick(durationMillis = 1200) }
        compose.runOnIdle {
            assertEquals(false, copied)
            assertEquals(true, saved)
            val entry = runBlocking { settings.history.first().single() }
            assertEquals("你", entry.original)
            assertEquals("ni", entry.transliteration)
            assertEquals("active", entry.variantId)
            saved = false
        }
        compose.onNodeWithText("你").performClick()
        compose.runOnIdle { assertEquals(true, copied); assertEquals(false, saved) }
        compose.runOnIdle {
            val actions = compose.onNodeWithText("你").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertEquals(listOf("Copia", "Salva"), actions.map { it.label })
            actions.last().action()
        }
        compose.runOnIdle { assertEquals(true, saved) }
        compose.onNodeWithText("Cancella").performClick()
        compose.onNodeWithText("Testi trovati · 0").assertIsDisplayed()
        compose.onNodeWithText("Cancella").assertIsDisplayed()
        compose.onNodeWithText("Nessun testo").assertIsDisplayed()
        compose.onNodeWithText("你").assertDoesNotExist()
        compose.onNodeWithText("ni").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("copy", "save", "copy", "save", "clear"), actions)
        }
    }

}
