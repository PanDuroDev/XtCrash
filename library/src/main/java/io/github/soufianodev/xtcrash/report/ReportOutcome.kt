package io.github.soufianodev.xtcrash.report

/**
 * Outcome of a custom [ReportSender] or [ReportActionHandler] attempt.
 *
 * <p>The four values are deliberately distinct. In particular, [ACCEPTED] means
 * only that the integration durably accepted responsibility for the request —
 * it does <em>not</em> mean a browser opened, a page loaded, an email was sent,
 * or an issue was submitted.</p>
 */
enum class ReportOutcome {
    /**
     * The integration durably accepted responsibility for the request before
     * returning this value. No further automatic reporting flow is started.
     */
    ACCEPTED,

    /**
     * The integration declined the request without accepting it and without
     * leaving a possible acceptance side effect. The normal destination action
     * runs instead.
     */
    NOT_HANDLED,

    /**
     * The operation definitely failed, with no acceptance and no possibility of
     * later acceptance. An accurate failure message is shown and the safe
     * destination-specific fallback is offered.
     */
    FAILED,

    /**
     * Acceptance may have occurred, but its status cannot be reliably
     * established (for example a timeout or exception after the work started).
     * The outcome is reported as uncertain and no second flow is started
     * automatically.
     */
    UNKNOWN
}
