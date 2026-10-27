package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.services.camera.CameraSessionLog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class CameraSessionLogTest {
    @Test fun insertionAndTrimmedDuplicatesKeepIdentityAndPosition() {
        val log = CameraSessionLog()
        val ids = log.ingest(listOf(" 你\n" to "ni", "好" to "hao",
            "你" to "ignored"))
        assertEquals(listOf("好", "你"), log.entries.map { it.original })
        assertEquals(ids.reversed(), log.entries.map { it.id })
        val before = log.entries.toList()
        assertTrue(log.ingest(listOf("你" to "changed",
            " 好 " to "changed")).isEmpty())
        assertEquals(before, log.entries)
        assertTrue(log.ingest(emptyList()).isEmpty())
        val copy = log.copy()
        copy.clear()
        assertEquals(before, log.entries)
    }

    @Test fun capEvictsOldestAndAllowsEvictedTextAgain() {
        val log = CameraSessionLog()
        log.ingest((0..50).map { "$it" to "t$it" })
        assertEquals(50, log.entries.size)
        assertEquals("50", log.entries.first().original)
        assertEquals("1", log.entries.last().original)
        assertEquals(1, log.ingest(listOf("0" to "again")).size)
        assertEquals("0", log.entries.first().original)
        assertEquals("2", log.entries.last().original)
    }

    @Test fun clearStartsFreshAndIgnoresBlankOriginals() {
        val log = CameraSessionLog()
        assertTrue(log.ingest(listOf(" \n" to "ignored")).isEmpty())
        val old = log.ingest(listOf("你" to "ni"))
        log.clear()
        assertTrue(log.entries.isEmpty())
        assertNotEquals(old, log.ingest(listOf("你" to "ni")))
    }
}

@RunWith(Parameterized::class)
class CameraSessionLogWhitespaceTest(private val codePoint: Int, private val trims: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "codePoint={0}, trims={1}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(0x0009, true),
            arrayOf(0x000A, true),
            arrayOf(0x000B, true),
            arrayOf(0x000C, true),
            arrayOf(0x000D, true),
            arrayOf(0x0020, true),
            arrayOf(0x0085, true),
            arrayOf(0x00A0, true),
            arrayOf(0x1680, true),
            arrayOf(0x2000, true),
            arrayOf(0x2001, true),
            arrayOf(0x2002, true),
            arrayOf(0x2003, true),
            arrayOf(0x2004, true),
            arrayOf(0x2005, true),
            arrayOf(0x2006, true),
            arrayOf(0x2007, true),
            arrayOf(0x2008, true),
            arrayOf(0x2009, true),
            arrayOf(0x200A, true),
            arrayOf(0x2028, true),
            arrayOf(0x2029, true),
            arrayOf(0x202F, true),
            arrayOf(0x205F, true),
            arrayOf(0x3000, true),
            arrayOf(0x0000, false),
            arrayOf(0x0008, false),
            arrayOf(0x000E, false),
            arrayOf(0x001C, false),
            arrayOf(0x001D, false),
            arrayOf(0x001E, false),
            arrayOf(0x001F, false),
            arrayOf(0x0084, false),
            arrayOf(0x0086, false),
            arrayOf(0x180E, false),
            arrayOf(0x200B, false),
            arrayOf(0x200C, false),
            arrayOf(0x2060, false),
            arrayOf(0xFEFF, false),
            arrayOf(0x1F600, false)
        )
    }

    @Test fun boundaryTrimmingAndInteriorPreservation() {
        val boundary = String(Character.toChars(codePoint))
        val wrapped = boundary + "你" + boundary
        val log = CameraSessionLog()
        if (trims) {
            assertTrue(log.ingest(listOf(boundary to "ignored")).isEmpty())
            log.ingest(listOf(wrapped to "ni"))
            assertEquals(listOf("你"), log.entries.map { it.original })
            val before = log.entries
            assertTrue(log.ingest(listOf("你" to "changed")).isEmpty())
            assertEquals(before, log.entries)
        } else {
            log.ingest(listOf(wrapped to "ni", "你" to "ni"))
            assertEquals(listOf("你", wrapped), log.entries.map { it.original })
        }
        val interior = "你" + boundary + "好"
        log.ingest(listOf(interior to "ni hao"))
        assertEquals(interior, log.entries.first().original)
    }
}
