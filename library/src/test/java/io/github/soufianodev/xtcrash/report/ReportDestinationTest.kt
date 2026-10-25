package io.github.soufianodev.xtcrash.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDestinationTest {

    @Test
    fun destinationSelectsNormalAction() {
        assertEquals(ReportDestination.EMAIL, ReportDestination.valueOf("EMAIL"))
        assertEquals(ReportDestination.GITHUB, ReportDestination.valueOf("GITHUB"))
        assertEquals(ReportDestination.GITLAB, ReportDestination.valueOf("GITLAB"))
        assertEquals(ReportDestination.CUSTOM, ReportDestination.valueOf("CUSTOM"))
    }

    @Test
    fun destinationIsIndependentOfHandlerAndSender() {
        // The destination enum must not encode the handler or the custom sender:
        // CUSTOM names a destination whose normal action is a ReportSender, and
        // the optional handler is configured separately from any destination.
        val names = ReportDestination.entries.map { it.name }
        assertFalse(names.any { it.contains("HANDLER") })
        assertEquals(4, names.size)
    }

    @Test
    fun preparedReportKeepsCanonicalTextSeparateFromUrl() {
        val report = PreparedReport()
        report.crashId = "XTCRASH-ABC"
        report.requestId = "req-1"
        report.destination = ReportDestination.GITHUB
        report.reportText = "canonical full report"
        report.subject = "Crash report"
        report.url = "https://github.com/o/r/issues/new?title=t&body=b"
        report.prefillOmitsReport = true

        assertEquals("canonical full report", report.reportText)
        assertTrue(report.prefillOmitsReport)
        assertNotNull(report.url)
    }

    @Test
    fun preparedReportDefaultsHaveNoUrl() {
        val report = PreparedReport()
        assertNull(report.url)
        assertFalse(report.prefillOmitsReport)
        assertFalse(report.reportCopiedToClipboard)
    }
}
