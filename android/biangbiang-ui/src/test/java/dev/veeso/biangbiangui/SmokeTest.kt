package dev.veeso.biangbiangui

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun versionMatchesRelease() {
        assertEquals("0.4.0", BiangBiangUi.VERSION)
    }
}
