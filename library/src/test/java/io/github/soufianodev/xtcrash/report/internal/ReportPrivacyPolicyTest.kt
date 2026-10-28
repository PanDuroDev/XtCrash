package io.github.soufianodev.xtcrash.report.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrivacyPolicyTest {

    private fun confirmation(
        url: String,
        title: String? = null,
        description: String? = null,
        prefillOmitsReport: Boolean = false,
        reportCopiedToClipboard: Boolean = false,
    ): String = ReportPrivacyPolicy.composeConfirmationSections(
        url = url,
        title = title,
        description = description,
        prefillOmitsReport = prefillOmitsReport,
        reportCopiedToClipboard = reportCopiedToClipboard,
        manualPasteNote = "REPORT_COPIED_TO_CLIPBOARD_PASTE_BEFORE_SUBMIT",
        manualPasteNotCopiedNote = "REPORT_NOT_COPIED_PASTE_MANUALLY",
        httpWarning = "HTTP_IS_UNENCRYPTED",
        prefillDisclosure = "PREFILL_MAY_BE_SENT_BEFORE_SUBMIT",
        destination = "DESTINATION_URL",
        fields = "DECODED_TITLE_AND_DESCRIPTION",
    )

    @Test
    fun bareHttpUrlIncludesWarningEvenWhenNoPrefillIsPresent() {
        val message = confirmation(
            url = "http://gitlab.example.com/group/project/-/issues/new",
        )

        assertTrue(message.contains("HTTP_IS_UNENCRYPTED"))
        assertFalse(message.contains("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT"))
        assertTrue(message.indexOf("HTTP_IS_UNENCRYPTED") < message.indexOf("DESTINATION_URL"))
    }

    @Test
    fun bareHttpsUrlDoesNotIncludeHttpWarningOrPrefillDisclosure() {
        val message = confirmation(
            url = "https://gitlab.example.com/group/project/-/issues/new",
        )

        assertFalse(message.contains("HTTP_IS_UNENCRYPTED"))
        assertFalse(message.contains("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT"))
        assertEquals("DESTINATION_URL\n\nDECODED_TITLE_AND_DESCRIPTION", message)
    }

    @Test
    fun prefilledHttpsUrlStillDisclosesThatValuesMayBeSentBeforeSubmission() {
        val message = confirmation(
            url = "https://gitlab.example.com/group/project/-/issues/new?issue%5Btitle%5D=Crash",
            title = "Crash",
        )

        assertTrue(message.contains("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT"))
        assertFalse(message.contains("HTTP_IS_UNENCRYPTED"))
        assertTrue(message.indexOf("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT") < message.indexOf("DESTINATION_URL"))
    }

    @Test
    fun shortenedHttpReportShowsSuccessfulClipboardNoteBeforeWarningAndDetails() {
        val message = confirmation(
            url = "http://gitlab.example.com/group/project/-/issues/new",
            prefillOmitsReport = true,
            reportCopiedToClipboard = true,
        )

        val copyNote = "REPORT_COPIED_TO_CLIPBOARD_PASTE_BEFORE_SUBMIT"
        assertTrue(message.contains(copyNote))
        assertFalse(message.contains("REPORT_NOT_COPIED_PASTE_MANUALLY"))
        assertTrue(message.indexOf(copyNote) < message.indexOf("HTTP_IS_UNENCRYPTED"))
        assertTrue(message.indexOf("HTTP_IS_UNENCRYPTED") < message.indexOf("DESTINATION_URL"))
    }

    @Test
    fun shortenedHttpReportShowsFailureNoteWhenClipboardCopyFails() {
        val message = confirmation(
            url = "http://gitlab.example.com/group/project/-/issues/new",
            prefillOmitsReport = true,
            reportCopiedToClipboard = false,
        )

        val failedNote = "REPORT_NOT_COPIED_PASTE_MANUALLY"
        assertTrue(message.contains(failedNote))
        assertFalse(message.contains("REPORT_COPIED_TO_CLIPBOARD_PASTE_BEFORE_SUBMIT"))
        assertTrue(message.indexOf(failedNote) < message.indexOf("HTTP_IS_UNENCRYPTED"))
        assertTrue(message.indexOf("HTTP_IS_UNENCRYPTED") < message.indexOf("DESTINATION_URL"))
    }

    @Test
    fun optionalManualPasteNoteIsAbsentWhenReportFitsInUrl() {
        val message = confirmation(
            url = "https://gitlab.example.com/group/project/-/issues/new?issue%5Btitle%5D=Crash",
            title = "Crash",
            prefillOmitsReport = false,
        )

        assertFalse(message.contains("REPORT_COPIED_TO_CLIPBOARD_PASTE_BEFORE_SUBMIT"))
        assertFalse(message.contains("REPORT_NOT_COPIED_PASTE_MANUALLY"))
    }
}
