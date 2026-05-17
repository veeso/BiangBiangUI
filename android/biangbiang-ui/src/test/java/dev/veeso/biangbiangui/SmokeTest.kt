package dev.veeso.biangbiangui

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun versionIsSemver() {
        assertEquals(3, BiangBiangUi.VERSION.split(".").size)
    }
}
