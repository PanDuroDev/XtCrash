package io.github.soufianodev.xtcrash.report

/**
 * Developer-defined sending method used for the [ReportDestination.CUSTOM]
 * destination.
 *
 * <p>This is <em>not</em> limited to HTTP and is <em>not</em> an alias for
 * [ReportActionHandler]: it is the normal action for the CUSTOM destination,
 * whereas the handler optionally intercepts any destination before its normal
 * action runs.</p>
 *
 * <p>Implementations must be `java.io.Serializable` and declared as a top-level
 * or static (nested, `static`) class, because the sender travels inside the
 * serialized [io.github.soufianodev.xtcrash.config.CrashConfig] into the
 * `:xtcrash_error` process. An anonymous or non-static inner class, or a class
 * whose instance fields are not serializable, is rejected at configure time.</p>
 *
 * <p>Invoked on a worker thread, never the main thread. The configured deadline
 * applies, so implementations should avoid unbounded blocking work. Returning
 * [ReportOutcome.ACCEPTED] asserts that responsibility was durably accepted
 * before returning.</p>
 */
interface ReportSender : java.io.Serializable {

    /**
     * Sends the prepared report using this developer-defined method.
     *
     * @param report the immutable prepared report, including the exact
     * destination [PreparedReport.url] when one applies
     * @return the outcome of this attempt; see [ReportOutcome] for semantics
     */
    fun send(report: PreparedReport): ReportOutcome
}
