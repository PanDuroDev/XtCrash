package io.github.soufianodev.xtcrash.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportOutcomeTest {

    @Test
    fun outcomeSemanticsAreDistinct() {
        // The four values must remain distinct: acceptance, decline,
        // definite failure, and uncertainty are different states.
        val values = ReportOutcome.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(ReportOutcome.ACCEPTED))
        assertTrue(values.contains(ReportOutcome.NOT_HANDLED))
        assertTrue(values.contains(ReportOutcome.FAILED))
        assertTrue(values.contains(ReportOutcome.UNKNOWN))
    }

    @Test
    fun acceptedIsNotTheSameAsFailedOrUnknown() {
        assertTrue(ReportOutcome.ACCEPTED != ReportOutcome.FAILED)
        assertTrue(ReportOutcome.ACCEPTED != ReportOutcome.UNKNOWN)
        assertTrue(ReportOutcome.FAILED != ReportOutcome.UNKNOWN)
    }
}
