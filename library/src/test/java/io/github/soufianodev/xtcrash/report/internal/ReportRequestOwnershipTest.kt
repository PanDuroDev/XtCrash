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
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r1", report)
        assertEquals(report, ownership.consumeConfirmation("r1"))
        assertNull("a second confirm must be a no-op", ownership.consumeConfirmation("r1"))
    }

    @Test
    fun confirmationIsScopedToItsRequest() {
        val ownership = ReportRequestOwnership()
        val report = prepared()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r1", report)
        assertNull(
            "a request that no longer owns the dispatcher must not consume",
            ownership.consumeConfirmation("r2"),
        )
        assertEquals(report, ownership.consumeConfirmation("r1"))
    }

    @Test
    fun staleRequestCannotStageConfirmation() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r2", prepared())
        assertNull("a superseded request must not stage", ownership.consumeConfirmation("r1"))
        assertNull(ownership.consumeConfirmation("r2"))
    }

    @Test
    fun clearConfirmationDropsStagedConfirmation() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r1", prepared())
        ownership.clearConfirmation()
        assertNull(ownership.consumeConfirmation("r1"))
    }

    @Test
    fun newRequestClearsStagedConfirmation() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r1", prepared())
        ownership.releaseInFlight()
        ownership.tryBegin()
        ownership.beginRequest("r2")
        assertNull(
            "a confirmation staged for the previous request must not be openable",
            ownership.consumeConfirmation("r1"),
        )
        assertNull(ownership.consumeConfirmation("r2"))
    }

    @Test
    fun invalidateDiscardsEverything() {
        val ownership = ReportRequestOwnership()
        ownership.tryBegin()
        ownership.beginRequest("r1")
        ownership.stageConfirmation("r1", prepared())

        ownership.invalidate()

        assertFalse(ownership.owns("r1"))
        assertNull(ownership.consumeConfirmation("r1"))
        assertFalse("an invalidated dispatcher must not start requests", ownership.tryBegin())
        ownership.beginRequest("r2")
        ownership.stageConfirmation("r2", prepared())
        assertNull(
            "an invalidated dispatcher must not stage confirmations",
            ownership.consumeConfirmation("r2"),
        )
        assertNull(ownership.consumeConfirmation("r1"))
    }
}
