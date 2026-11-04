package io.github.soufianodev.xtcrash.report.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportHandlerConfirmationStateTest {
    @Test
    fun stagedConfirmationCanInvokeHandlerOnlyOnce() {
        val state = ReportHandlerConfirmationState()
        state.stage("request-1")

        assertTrue(state.consumeFor("request-1"))
        assertFalse(state.consumeFor("request-1"))
    }

    @Test
    fun staleRequestCannotConsumeAnotherRequestsConfirmation() {
        val state = ReportHandlerConfirmationState()
        state.stage("request-current")

        assertFalse(state.consumeFor("request-stale"))
        assertTrue(state.consumeFor("request-current"))
    }

    @Test
    fun clearingAfterCancellationPreventsHandlerInvocation() {
        val state = ReportHandlerConfirmationState()
        state.stage("request-1")
        state.clear()

        assertFalse(state.consumeFor("request-1"))
    }

    @Test
    fun clearingOneRequestDoesNotClearAnotherRequestsConfirmation() {
        val state = ReportHandlerConfirmationState()
        state.stage("request-current")
        state.clearIf("request-stale")

        assertTrue(state.consumeFor("request-current"))
    }
}
