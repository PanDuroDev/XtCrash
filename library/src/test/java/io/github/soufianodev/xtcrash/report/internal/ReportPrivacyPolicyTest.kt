package io.github.soufianodev.xtcrash.report.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrivacyPolicyTest {

    @Test
    fun manualPasteNoteAndHttpWarningPrecedeLongDestinationAndFieldDetails() {
        val sections = ReportPrivacyPolicy.composeConfirmationSections(
            manualPasteNote = "MANUAL_PASTE_REQUIRED",
            httpWarning = "HTTP_IS_UNENCRYPTED",
            prefillDisclosure = "PREFILL_MAY_BE_SENT_BEFORE_SUBMIT",
            destination = "DESTINATION_URL_WITH_LONG_QUERY",
            fields = "DECODED_TITLE_AND_DESCRIPTION",
        )

        assertTrue(sections.indexOf("MANUAL_PASTE_REQUIRED") < sections.indexOf("HTTP_IS_UNENCRYPTED"))
        assertTrue(sections.indexOf("HTTP_IS_UNENCRYPTED") < sections.indexOf("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT"))
        assertTrue(sections.indexOf("PREFILL_MAY_BE_SENT_BEFORE_SUBMIT") < sections.indexOf("DESTINATION_URL_WITH_LONG_QUERY"))
        assertTrue(sections.indexOf("DESTINATION_URL_WITH_LONG_QUERY") < sections.indexOf("DECODED_TITLE_AND_DESCRIPTION"))
    }

    @Test
    fun absentOptionalWarningsDoNotAddEmptySections() {
        assertEquals(
            "DESTINATION_URL\n\nFIELDS",
            ReportPrivacyPolicy.composeConfirmationSections(
                manualPasteNote = null,
                httpWarning = null,
                prefillDisclosure = null,
                destination = "DESTINATION_URL",
                fields = "FIELDS",
            ),
        )
    }
}
