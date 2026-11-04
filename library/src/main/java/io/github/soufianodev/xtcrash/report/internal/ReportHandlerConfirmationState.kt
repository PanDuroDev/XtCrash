package io.github.soufianodev.xtcrash.report.internal

/**
 * Tracks the one initial web confirmation that is allowed to invoke a configured
 * report handler. Consumption is one-shot; later fallback confirmations open the
 * URL normally instead of retrying a failed or uncertain handler.
 */
internal class ReportHandlerConfirmationState {
    private var stagedRequestId: String? = null

    fun stage(requestId: String) {
        stagedRequestId = requestId
    }

    fun consumeFor(requestId: String): Boolean {
        if (stagedRequestId != requestId) return false
        stagedRequestId = null
        return true
    }

    fun clear() {
        stagedRequestId = null
    }

    fun clearIf(requestId: String) {
        if (stagedRequestId == requestId) stagedRequestId = null
    }
}
