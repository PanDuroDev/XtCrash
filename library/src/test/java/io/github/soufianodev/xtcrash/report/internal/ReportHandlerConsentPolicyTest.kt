package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportHandlerConsentPolicyTest {
    @Test
    fun webDestinationsRequireConsentBeforeConfiguredHandlerRuns() {
        assertTrue(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.GITHUB,
                handlerConfigured = true,
            )
        )
        assertTrue(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.GITLAB,
                handlerConfigured = true,
            )
        )
    }

    @Test
    fun noHandlerDoesNotChangeTheNormalWebConfirmationPath() {
        assertFalse(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.GITHUB,
                handlerConfigured = false,
            )
        )
        assertFalse(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.GITLAB,
                handlerConfigured = false,
            )
        )
    }

    @Test
    fun nonWebDestinationsKeepTheirExistingHandlerFlow() {
        assertFalse(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.EMAIL,
                handlerConfigured = true,
            )
        )
        assertFalse(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                ReportDestination.CUSTOM,
                handlerConfigured = true,
            )
        )
        assertFalse(
            ReportHandlerConsentPolicy.requiresConsentBeforeHandler(
                null,
                handlerConfigured = true,
            )
        )
    }
}
