package io.github.soufianodev.xtcrash.report.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefillShortenerTest {

    private fun block(text: String) = PrefillShortener.Block(text)

    @Test
    fun keepsAllBlocksThatFit() {
        val result = PrefillShortener.shorten(listOf(block("a"), block("b")), budgetBytes = 100)
        assertEquals("ab", result.body)
        assertFalse(result.omittedContent)
    }

    @Test
    fun truncatesAtPriorityBoundaryWithMarker() {
        val first = "a".repeat(50)
        val second = "b".repeat(200)
        // Budget fits the first block plus the truncation marker, but not the second block.
        val budget = 200
        val result = PrefillShortener.shorten(listOf(block(first), block(second)), budgetBytes = budget)
        assertTrue(result.omittedContent)
        assertTrue(result.body.startsWith(first))
        assertTrue(result.body.contains(PrefillShortener.TRUNCATION_MARKER))
        assertTrue(result.body.toByteArray(Charsets.UTF_8).size <= budget)
    }

    @Test
    fun preservesHighestPriorityBlocksFirst() {
        val crashId = "Crash ID: XTCRASH-1"
        val stack = "S".repeat(500)
        val result = PrefillShortener.shorten(listOf(block(crashId), block(stack)), budgetBytes = 100)
        assertTrue(result.body.startsWith(crashId))
        assertTrue(result.omittedContent)
    }

    @Test
    fun zeroBudgetDropsEverythingButFlagsOmission() {
        val result = PrefillShortener.shorten(listOf(block("abc")), budgetBytes = 0)
        assertEquals("", result.body)
        assertTrue(result.omittedContent)
    }

    @Test
    fun emptyInputIsNotOmitted() {
        val result = PrefillShortener.shorten(emptyList(), budgetBytes = 10)
        assertEquals("", result.body)
        assertFalse(result.omittedContent)
    }

    @Test
    fun takeHeadBytesRespectsByteBudget() {
        val text = "é".repeat(100) // 2 bytes each
        val head = PrefillShortener.takeHeadBytes(text, 10)
        assertTrue(head.toByteArray(Charsets.UTF_8).size <= 10)
        assertTrue(head.length <= 10)
    }

    @Test
    fun utf8BytesCountsBytesNotChars() {
        assertEquals(2, PrefillShortener.utf8Bytes("é"))
        assertEquals(3, PrefillShortener.utf8Bytes("€"))
        assertEquals(5, PrefillShortener.utf8Bytes("é€"))
    }
}
