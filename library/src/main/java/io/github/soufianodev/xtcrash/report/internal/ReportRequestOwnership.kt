package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.PreparedReport

/**
 * Main-thread-confined ownership state for one reporting request at a time:
 * the active request id, the in-flight guard, and the pending web-URL
 * confirmation.
 *
 * <p>Kept free of Android types so the concurrency-sensitive rules are unit
 * testable: a newer request supersedes an older one, a confirmation is
 * consumed at most once, and [invalidate] makes every late notification,
 * outcome, handoff, and confirmation from the discarded request a no-op.
 * Invalidation only drops local effects — it never implies that an external
 * handler's acceptance was cancelled or rolled back.</p>
 */
internal class ReportRequestOwnership {

    private var activeRequestId: String? = null
    private var inFlight: Boolean = false
    private var confirmation: PreparedReport? = null
    private var invalidated: Boolean = false

    /** True once this dispatcher was replaced or destroyed; it must go quiet. */
    val isInvalidated: Boolean get() = invalidated

    /** Attempts to open the in-flight window for a new user-initiated request. */
    fun tryBegin(): Boolean {
        if (inFlight || invalidated) {
            return false
        }
        inFlight = true
        return true
    }

    /** Registers [requestId] as active, superseding any previous confirmation. */
    fun beginRequest(requestId: String) {
        activeRequestId = requestId
        confirmation = null
    }

    /** True when [requestId] still owns this dispatcher's results and handoffs. */
    fun owns(requestId: String): Boolean = !invalidated && activeRequestId == requestId

    /** Ends the in-flight window (terminal phase or user dismissal). */
    fun releaseInFlight() {
        inFlight = false
    }

    /** Stages the confirmation for the active request's web navigation. */
    fun stageConfirmation(prepared: PreparedReport) {
        if (!invalidated) {
            confirmation = prepared
        }
    }

    /**
     * Consumes the pending confirmation, returning it exactly once. A stale,
     * cancelled, replaced, invalidated, or already-consumed confirm gets null
     * and must not navigate.
     */
    fun consumeConfirmation(): PreparedReport? {
        val staged = confirmation
        confirmation = null
        return staged
    }

    /** Clears a staged confirmation without consuming it (user cancelled). */
    fun clearConfirmation() {
        confirmation = null
    }

    /**
     * Discards everything owned by the previous request: late worker results,
     * queued listener notifications, destination side effects, and pending
     * confirmations all become no-ops.
     */
    fun invalidate() {
        invalidated = true
        activeRequestId = null
        inFlight = false
        confirmation = null
    }
}
