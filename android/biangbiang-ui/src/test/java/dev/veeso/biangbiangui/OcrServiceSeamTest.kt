package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrServiceSeamTest {
    @Test
    fun stubServiceReturnsBox() = runBlocking {
        val svc = OcrService { _, _ ->
            listOf(OcrTextBox("你好", 1, 2, 3, 4))
        }
        val boxes = svc.recognize(null, OcrRecognizer.CHINESE)
        assertEquals(listOf(OcrTextBox("你好", 1, 2, 3, 4)), boxes)
    }
}
