package dev.veeso.biangbiangui

import dev.veeso.biangbiangui.protocols.Transliterator
import dev.veeso.biangbiangui.services.TextProcessingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors iOS Phase 2 `TextProcessingEngineTests`. */
class TextProcessingEngineTest {

    /** Maps every character in the span to "x", joined by single spaces. */
    private val fakeLatin = Transliterator { s ->
        s.map { "x" }.joinToString(" ")
    }

    /** CJK Unified Ideographs: U+4E00–U+9FFF */
    private val cjk = listOf(0x4E00u..0x9FFFu)

    /** CJK + CJK Extension A (U+3400–U+4DBF). */
    private val cjkPlusExtA = listOf(0x3400u..0x4DBFu, 0x4E00u..0x9FFFu)

    private fun engine(ranges: List<UIntRange> = cjk) =
        TextProcessingEngine(ranges, fakeLatin)

    @Test
    fun returnsNilWhenNoScript() {
        assertNull(engine().process("Pizza123"))
    }

    @Test
    fun detectsScript() {
        assertTrue(engine().containsScript("你好Pizza"))
    }

    @Test
    fun doesNotDetectScriptInPureAscii() {
        assertFalse(engine().containsScript("Hello world"))
    }

    @Test
    fun insertsSpacesAroundLatinRuns() {
        assertEquals("x x NASA x x.", engine().process("我在NASA工作."))
    }

    @Test
    fun preservesEmojiAndPunctuation() {
        assertEquals("x x x🥟", engine().process("我喜欢🥟"))
    }

    @Test
    fun leadingSpanAtStringStartNoLeadingSpace() {
        assertEquals("x x", engine().process("你好"))
    }

    @Test
    fun punctuationPreventsLeadingSpace() {
        assertEquals("Hello,x x", engine().process("Hello,你好"))
    }

    @Test
    fun trailingSpaceBeforeAsciiDigit() {
        assertEquals("x 5", engine().process("我5"))
    }

    @Test
    fun multiRangeDetection() {
        val extA = "㐀" // 㐀, U+3400 — outside cjk, inside cjkPlusExtA
        assertFalse(engine(cjk).containsScript(extA))
        assertTrue(engine(cjkPlusExtA).containsScript(extA))
    }

    @Test
    fun spacesCleanedUpBeforePunctuation() {
        assertEquals("x x.", engine().process("你好 ."))
    }
}
