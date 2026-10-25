package io.github.soufianodev.xtcrash.report

/**
 * Optional integration that lets a consuming application (for example XtMapper)
 * accept responsibility for a crash report that XtCrash has already prepared.
 *
 * <p>The handler receives the immutable [PreparedReport], including the exact
 * destination [PreparedReport.url] when one applies. It must not rebuild
 * destination URLs — XtCrash owns title formatting, body formatting, URL
 * construction, encoding, and prefill-shortening decisions.</p>
 *
 * <p>Process-safe construction: reporting UI runs in the `:xtcrash_error`
 * process, so a handler instance or lambda registered only in the original
 * application process is not recoverable there. Configure the handler by class
 * with `reportActionHandler(MyHandler::class.java)`; the class is instantiated
 * by reflection in the error process and must therefore be public with a public
 * no-argument constructor, and must not perform side effects during
 * construction. This works from both Kotlin and Java.</p>
 *
 * <p>Keep the handler optional: apps without one continue to use the normal
 * action for the selected destination. Invoked on a worker thread within the
 * configured deadline. Returning [ReportOutcome.ACCEPTED] asserts that
 * responsibility was durably accepted before returning.</p>
 */
interface ReportActionHandler {

    /**
     * Offers the prepared report to this integration.
     *
     * @param report the immutable prepared report for one logical request
     * @return the outcome of this attempt; see [ReportOutcome] for semantics
     */
    fun onReport(report: PreparedReport): ReportOutcome
}
