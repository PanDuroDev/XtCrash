package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination

/**
 * Keeps report-bearing issue URLs behind the same explicit user-consent gate
 * even when an application installs a custom report-action handler.
 */
internal object ReportHandlerConsentPolicy {
    fun requiresConsentBeforeHandler(
        destination: ReportDestination?,
        handlerConfigured: Boolean,
    ): Boolean = handlerConfigured &&
        (destination == ReportDestination.GITHUB || destination == ReportDestination.GITLAB)
}
