package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class PasteInstructionTest {

    @Test
    fun failedCopyAlwaysTellsTheUserToCopyFirst() {
        assertEquals(
            PasteInstruction.Kind.COPY_THEN_PASTE,
            PasteInstruction.select(copied = false, destination = ReportDestination.GITHUB),
        )
        assertEquals(
            PasteInstruction.Kind.COPY_THEN_PASTE,
            PasteInstruction.select(copied = false, destination = ReportDestination.EMAIL),
        )
        assertEquals(
            PasteInstruction.Kind.COPY_THEN_PASTE,
            PasteInstruction.select(copied = false, destination = null),
        )
    }

    @Test
    fun successfulCopyAfterValidationFailureNeverAsksToCopyAgain() {
        // Regression: destination validation fails before preparation, so no
        // prepared report exists; the fallback Copy still succeeded. The
        // instruction must come from the real copy result plus the configured
        // destination — not from a fresh PreparedReport saying "not copied",
        // which produced "The full report was copied … Copy the report, then
        // paste it …" in one dialog.
        assertEquals(
            PasteInstruction.Kind.ISSUE_FORM,
            PasteInstruction.select(copied = true, destination = ReportDestination.GITHUB),
        )
        assertEquals(
            PasteInstruction.Kind.ISSUE_FORM,
            PasteInstruction.select(copied = true, destination = ReportDestination.GITLAB),
        )
        assertEquals(
            PasteInstruction.Kind.ISSUE_FORM,
            PasteInstruction.select(copied = true, destination = ReportDestination.CUSTOM),
        )
    }

    @Test
    fun successfulCopyWithNoConfiguredDestinationStillAvoidsCopyAgain() {
        assertEquals(
            PasteInstruction.Kind.ISSUE_FORM,
            PasteInstruction.select(copied = true, destination = null),
        )
    }

    @Test
    fun successfulCopyForEmailSaysPasteIntoAnEmail() {
        assertEquals(
            PasteInstruction.Kind.EMAIL_COPIED,
            PasteInstruction.select(copied = true, destination = ReportDestination.EMAIL),
        )
    }
}
