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
    fun baseUrlsWithEmbeddedCredentialsAreRejected() {
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl(
            "https://user:password@gitlab.example.com"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl(
            "http://token@gitlab.example.com:8443"))
    }

    @Test
    fun httpDestinationsRequireAWarningEvenWithoutPrefill() {
        assertTrue(IssueUrlBuilder.usesUnencryptedHttp(
            "http://gitlab.example.com/group/project/-/issues/new"))
        assertTrue(IssueUrlBuilder.usesUnencryptedHttp(
            "http://gitlab.example.com/group/project/-/issues/new?issue%5Btitle%5D=t"))
        assertFalse(IssueUrlBuilder.usesUnencryptedHttp(
            "https://gitlab.example.com/group/project/-/issues/new"))
        assertFalse(IssueUrlBuilder.usesUnencryptedHttp("not a url"))
    }

    @Test
    fun configuredBudgetIsClampedToSafeFloor() {
        assertEquals(IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES, IssueUrlBuilder.clampBudget(null))
        assertEquals(IssueUrlBuilder.MIN_URL_BUDGET_BYTES, IssueUrlBuilder.clampBudget(1))
        assertEquals(1234, IssueUrlBuilder.clampBudget(1234))
    }

    @Test
    fun oversizedTitleReturnsBareIssuePageInsteadOfMalformedUrl() {
        // Title alone exceeds the budget: the result must not keep the title
        // (no trimming can shrink it), so only the bare page is offered.
        val budget = "https://github.com/o/r/issues/new?title=".length + 10
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t".repeat(500),
            blocks = listOf(block("body")),
            budgetBytes = budget,
        )
        assertEquals("https://github.com/o/r/issues/new", built.url)
        assertEquals("", built.body)
        assertTrue(built.shortened)
    }

    @Test
    fun oversizedTitleAndUnfitBarePageYieldsEmptyUrl() {
        // Neither the title nor the bare page fits: report an empty url so the
        // caller offers Copy instead of opening a malformed target.
        val budget = 10
        val built = IssueUrlBuilder.github(
            owner = "o",
            repository = "r",
            title = "t".repeat(500),
            blocks = listOf(block("body")),
            budgetBytes = budget,
        )
        assertEquals("", built.url)
        assertEquals("", built.body)
        assertTrue(built.shortened)
    }

    @Test
    fun baseUrlWithQueryOrFragmentIsRejected() {
        // The appended issue path would land inside the query or fragment
        // instead of the path (https://host?x=1 + /p/-/issues/new).
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.com?x=1"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.com/#anchor"))
        assertFalse(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.com/?x=1"))
        assertTrue(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.com"))
        assertTrue(IssueUrlBuilder.isSupportedBaseUrl("https://gitlab.example.com:8443"))
    }

    @Test
    fun githubRejectsUnsafePathComponents() {
        // Unencoded components could escape their path segment and move the
        // prefill into a query or fragment of a different target.
        val safe = IssueUrlBuilder.github(
            owner = "some-owner",
            repository = "some_repo.v2",
            title = "t",
            blocks = listOf(block("body")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )
        assertTrue(safe.url.startsWith("https://github.com/some-owner/some_repo.v2/"))

        for (unsafeOwner in listOf("ow#ner", "ow?ner", "ow/ner", "ow%2Fner", "ow ner", "")) {
            assertEquals(
                "owner '$unsafeOwner' must yield no url",
                "",
                IssueUrlBuilder.github(
                    owner = unsafeOwner,
                    repository = "r",
                    title = "t",
                    blocks = listOf(block("body")),
                    budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
                ).url,
            )
        }
        assertEquals(
            "",
            IssueUrlBuilder.github(
                owner = "o",
                repository = "re#po",
                title = "t",
                blocks = listOf(block("body")),
                budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
            ).url,
        )
    }

    @Test
    fun finishedUrlValidationRejectsFragmentsAndMalformedUrls() {
        assertTrue(IssueUrlBuilder.isValidIssueUrl(
            "https://github.com/o/r/issues/new?title=t&body=b"))
        assertTrue(IssueUrlBuilder.isValidIssueUrl(
            "https://github.com/o/r/issues/new"))
        // A fragment means content escaped the query and would be dropped by
        // the browser; malformed or relative inputs must fail to parse.
        assertFalse(IssueUrlBuilder.isValidIssueUrl(
            "https://github.com/o/r/issues/new#frag"))
        assertFalse(IssueUrlBuilder.isValidIssueUrl("https://"))
        assertFalse(IssueUrlBuilder.isValidIssueUrl("not a url"))
        assertFalse(IssueUrlBuilder.isValidIssueUrl("/relative/path"))
        assertFalse(IssueUrlBuilder.isValidIssueUrl(
            "https://user:password@gitlab.example.com/group/project/-/issues/new"))
    }
}
