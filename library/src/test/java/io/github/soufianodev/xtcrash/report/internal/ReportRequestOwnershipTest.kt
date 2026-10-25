package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.PreparedReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRequestOwnershipTest {

    private fun prepared() = PreparedReport()

    @Test
    fun tryBeginAllowsOneRequestAtATime() {
        val ownership = ReportRequestOwnership()
        assertTrue(ownership.tryBegin())
        assertFalse("second tap while in flight must be ignored", ownership.tryBegin())
        ownership.releaseInFlight()
        assertTrue(ownership.tryBegin())
    }

    @Test
    fun beginRequestSupersedesPreviousOwner() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        assertTrue(ownership.owns("r1"))
        assertFalse(ownership.owns("r2"))

        ownership.releaseInFlight()
        ownership.tryBegin()
        ownership.beginRequest("r2")
        assertFalse("the superseded request must not keep ownership", ownership.owns("r1"))
        assertTrue(ownership.owns("r2"))
    }

    @Test
    fun confirmationIsConsumedExactlyOnce() {
        val ownership = ReportRequestOwnership()
        val report = prepared()
        ownership.stageConfirmation(report)
        assertEquals(report, ownership.consumeConfirmation())
        assertNull("a second confirm must be a no-op", ownership.consumeConfirmation())
    }

    @Test
    fun clearConfirmationDropsStagedConfirmation() {
        val ownership = ReportRequestOwnership()
        ownership.stageConfirmation(prepared())
        ownership.clearConfirmation()
        assertNull(ownership.consumeConfirmation())
    }

    @Test
    fun newRequestClearsStagedConfirmation() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation(prepared())
        ownership.releaseInFlight()
        ownership.tryBegin()
        ownership.beginRequest("r2")
        assertNull(
            "a confirmation staged for the previous request must not be openable",
            ownership.consumeConfirmation(),
        )
    }

    @Test
    fun invalidateDiscardsEverything() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation(prepared())

        ownership.invalidate()

        assertTrue(ownership.isInvalidated)
        assertFalse(ownership.owns("r1"))
        assertNull(ownership.consumeConfirmation())
        assertFalse("an invalidated dispatcher must not start requests", ownership.tryBegin())
        ownership.stageConfirmation(prepared())
        assertNull("an invalidated dispatcher must not stage confirmations", ownership.consumeConfirmation())
    }
}
