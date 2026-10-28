package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueUrlPrivacyPreviewTest {

    private fun block(text: String) = PrefillShortener.Block(text)

    @Test
    fun gitlabComPreviewUsesFinalPreparedUrlAndDecodedFields() {
        val built = IssueUrlBuilder.gitlab(
            baseUrl = "https://gitlab.com",
            projectPath = "group/project",
            title = "Crash: login failed",
            blocks = listOf(block("Device: Pixel 8\nReason: unexpected state")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )

        val preview = IssueUrlPrivacyPreviewBuilder.build(built.url, ReportDestination.GITLAB)

        assertNotNull(preview)
        assertEquals("https://gitlab.com/group/project/-/issues/new", preview!!.destinationUrl)
        assertEquals("Crash: login failed", preview.title)
        assertEquals(built.body, preview.description)
        assertFalse(preview.destinationUrl.contains("?"))
    }

    @Test
    fun selfHostedGitlabPreviewKeepsExplicitPortAndEncodedProjectPath() {
        val built = IssueUrlBuilder.gitlab(
            baseUrl = "https://gitlab.example.com:8443",
            projectPath = "engineering/mobile app",
            title = "Network error",
            blocks = listOf(block("Connection timed out")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )

        val preview = IssueUrlPrivacyPreviewBuilder.build(built.url, ReportDestination.GITLAB)

        assertNotNull(preview)
        assertEquals(
            "https://gitlab.example.com:8443/engineering/mobile%20app/-/issues/new",
            preview!!.destinationUrl,
        )
        assertEquals("Network error", preview.title)
        assertEquals(built.body, preview.description)
    }

    @Test
    fun shortenedReportPreviewMatchesValuesActuallyPresentInFinalUrl() {
        val fullBody = "diagnostic-line-".repeat(2_000)
        val built = IssueUrlBuilder.gitlab(
            baseUrl = "https://gitlab.com",
            projectPath = "group/project",
            title = "Crash summary",
            blocks = listOf(block(fullBody)),
            budgetBytes = 512,
        )

        val preview = IssueUrlPrivacyPreviewBuilder.build(built.url, ReportDestination.GITLAB)

        assertTrue("the URL should omit part of the report", built.shortened)
        assertNotNull(preview)
        assertEquals("Crash summary", preview!!.title)
        assertEquals("preview must reflect the actual prepared URL, not the full canonical report", built.body, preview.description)
        assertFalse("the shortened preview must not claim that the full report is in the URL", preview.description == fullBody)
        assertEquals("https://gitlab.com/group/project/-/issues/new", preview.destinationUrl)
    }

    @Test
    fun githubPreviewReadsGithubQueryNamesAndDecodesReservedCharacters() {
        val built = IssueUrlBuilder.github(
            owner = "org",
            repository = "app",
            title = "Crash & restart = failed",
            blocks = listOf(block("first line\nsecond line")),
            budgetBytes = IssueUrlBuilder.DEFAULT_URL_BUDGET_BYTES,
        )

        val preview = IssueUrlPrivacyPreviewBuilder.build(built.url, ReportDestination.GITHUB)

        assertNotNull(preview)
        assertEquals("https://github.com/org/app/issues/new", preview!!.destinationUrl)
        assertEquals("Crash & restart = failed", preview.title)
        assertEquals(built.body, preview.description)
    }

    @Test
    fun emptyPrefilledValuesAreDistinguishedFromMissingParameters() {
        val empty = IssueUrlPrivacyPreviewBuilder.build(
            "https://github.com/org/app/issues/new?title=&body=",
            ReportDestination.GITHUB,
        )
        val absent = IssueUrlPrivacyPreviewBuilder.build(
            "https://github.com/org/app/issues/new",
            ReportDestination.GITHUB,
        )

        assertNotNull(empty)
        assertEquals("", empty!!.title)
        assertEquals("", empty.description)
        assertNotNull(absent)
        assertNull(absent!!.title)
        assertNull(absent.description)
    }

    @Test
    fun invalidOrNonWebDestinationDoesNotProducePreview() {
        assertNull(IssueUrlPrivacyPreviewBuilder.build("", ReportDestination.GITHUB))
        assertNull(IssueUrlPrivacyPreviewBuilder.build("not a URL", ReportDestination.GITLAB))
        assertNull(
            IssueUrlPrivacyPreviewBuilder.build(
                "https://gitlab.com/group/project/-/issues/new",
                ReportDestination.CUSTOM,
            ),
        )
    }
}
