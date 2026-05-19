package dev.veeso.biangbiangui

import androidx.compose.ui.graphics.Color
import dev.veeso.biangbiangui.ui.brandColorOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the `Branding.accentColorHex` -> `Color` seam. Regression: the camera
 * screen used to hardcode Chinese red, ignoring `accentColorHex` (so Harakat's
 * `#006C35` rendered red). Mirrors iOS `Color(hex:)` decoding.
 */
class BrandColorTest {
    private fun assertChannels(c: Color?, r: Float, g: Float, b: Float, a: Float = 1f) {
        requireNotNull(c)
        assertEquals(r, c.red, 0.01f)
        assertEquals(g, c.green, 0.01f)
        assertEquals(b, c.blue, 0.01f)
        assertEquals(a, c.alpha, 0.01f)
    }

    @Test
    fun decodesSixDigitHex() {
        // Harakat Lens accent (Saudi green) — the bug case.
        assertChannels(brandColorOrNull("#006C35"), 0f, 0x6C / 255f, 0x35 / 255f)
    }

    @Test
    fun decodesChineseRed() {
        assertChannels(brandColorOrNull("#DE2910"), 0xDE / 255f, 0x29 / 255f, 0x10 / 255f)
    }

    @Test
    fun decodesThreeDigitHex() {
        assertChannels(brandColorOrNull("#000"), 0f, 0f, 0f)
    }

    @Test
    fun decodesEightDigitHexWithAlpha() {
        assertChannels(brandColorOrNull("#DE291080"), 0xDE / 255f, 0x29 / 255f, 0x10 / 255f, 0x80 / 255f)
    }

    @Test
    fun toleratesMissingHashAndWhitespace() {
        assertChannels(brandColorOrNull("  006C35  "), 0f, 0x6C / 255f, 0x35 / 255f)
    }

    @Test
    fun returnsNullOnMalformedHex() {
        assertNull(brandColorOrNull("not-a-color"))
        assertNull(brandColorOrNull("#12"))
        assertNull(brandColorOrNull(""))
    }
}
