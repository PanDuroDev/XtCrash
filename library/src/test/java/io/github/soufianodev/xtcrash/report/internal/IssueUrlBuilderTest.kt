package io.github.soufianodev.xtcrash.report.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueUrlBuilderTest {

    private fun block(text: String) = PrefillShortener.Block(text)

    @Test
    fun encodesReservedCharactersInQueryValue() {
        val encoded = IssueUrlBuilder.encodeValue("a b&c=d#e/f?g")
        assertFalse(encoded.contains(" "))
        assertFalse(encoded.contains("&"))
        assertFalse(encoded.contains("="))
        assertTrue(encoded.startsWith("a%20b"))
    }

    @Test
    fun encodesUnicodeAsUtf8PercentBytes() {
        // "é" is 2 UTF-8 bytes -> %C3%A9
        val encoded = IssueUrlBuilder.encodeValue("é")
        assertEquals("%C3%A9", encoded)
    }

    @Test
    fun shortReportFitsWithoutShortening() {
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t",
            blocks = listOf(block("hello")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )
        assertFalse(built.shortened)
        // GitHub prefills the issue body from the `body` query parameter.
        assertTrue(built.url.contains("body=hello"))
        assertTrue(built.url.startsWith("https://github.com/o/r/issues/new?title="))
    }

    @Test
    fun urlStaysWithinByteBudgetForLongReport() {
        val longBody = "x".repeat(50_000)
        val budget = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t",
            blocks = listOf(block(longBody)),
            budgetBytes = budget,
        )
        val urlBytes = built.url.toByteArray(Charsets.UTF_8).size
        assertTrue("url $urlBytes bytes > budget $budget", urlBytes <= budget + 64)
        assertTrue(built.shortened)
    }

    @Test
    fun unicodeExpansionIsMeasuredInBytesNotChars() {
        // Each "é" encodes to 6 percent-chars (3 bytes * 3), so byte measurement
        // must account for expansion the char count would miss.
        val body = "é".repeat(2_000)
        val budget = 1_000
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t",
            blocks = listOf(block(body)),
            budgetBytes = budget,
        )
        assertTrue(built.shortened)
        val urlBytes = built.url.toByteArray(Charsets.UTF_8).size
        assertTrue("url $urlBytes bytes > budget $budget", urlBytes <= budget + 64)
    }

    @Test
    fun exactBudgetBoundaryIsNotShortened() {
        val budget = 600
        // Build a body that exactly consumes the budget after the fixed prefix.
        val fixed = "https://github.com/o/r/issues/new?title=t&body=".toByteArray(Charsets.UTF_8).size
        val bodyBytes = budget - fixed
        val body = "a".repeat(bodyBytes)
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t",
            blocks = listOf(block(body)),
            budgetBytes = budget,
        )
        assertFalse("exact-fit body should not be shortened", built.shortened)
        assertEquals(budget, built.url.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun gitlabBuildsNewIssuePathWithEncodedProjectSegments() {
        val built = IssueUrlBuilder.gitlab(
            baseUrl = "https://gitlab.com/",
            projectPath = "group/pro ject",
            title = "t",
            blocks = listOf(block("body")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )
        // GitLab prefills from `issue[title]` / `issue[description]`, percent-encoded.
        assertTrue(built.url.startsWith("https://gitlab.com/group/pro%20ject/-/issues/new?issue%5Btitle%5D=t&issue%5Bdescription%5D=body"))
    }

    @Test
    fun selfHostedGitLabBaseUrlIsPreserved() {
        val built = IssueUrlBuilder.gitlab(
            baseUrl = "https://gitlab.example.com",
            projectPath = "a/b",
            title = "t",
            blocks = listOf(block("b")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )
        assertTrue(built.url.startsWith("https://gitlab.example.com/a/b/-/issues/new"))
    }

    @Test
    fun baseUrlsAreValidated() {
        assertTrue(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.com"))
        assertTrue(IssueUrlBuilder.isSupportedBaseUrl("http://gitlab.example.com/"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("gitlab.com"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("https://"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("ftp://gitlab.com"))
    }

    @Test
    fun configuredBudgetIsClampedToSafeFloor() {
        assertEquals(IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES, IssueUrlBuilder.clampBudget(null))
        assertEquals(IssueUrlBuilder.MIN_URL_BUDGET_BYTES, IssueUrlBuilder.clampBudget(1))
        assertEquals(1234, IssueUrlBuilder.clampBudget(1234))
    }
}
