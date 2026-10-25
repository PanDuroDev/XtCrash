package io.github.soufianodev.xtcrash.report

/**
 * The normal reporting action for the "Send crash report" button.
 *
 * <p>This selects where a prepared crash report goes. It is deliberately
 * independent of the optional [ReportActionHandler], which lets a consuming
 * app take responsibility for a report regardless of the selected destination.
 * It is also not a synonym for a custom sender: [CUSTOM] names a
 * destination whose normal action is a developer-defined [ReportSender].</p>
 *
 * <p>When no destination is configured, the report is shared through the
 * generic Android Sharesheet (the pre-existing behavior).</p>
 */
enum class ReportDestination {
    /** Email-only flow, suggested recipient from `supportEmail()`. */
    EMAIL,

    /** Prefilled GitHub issue form. Requires an explicit owner and repository. */
    GITHUB,

    /** Prefilled GitLab new-issue form. Requires an explicit project path and base URL. */
    GITLAB,

    /** Developer-defined sender configured with `reportSender(...)`. */
    CUSTOM
}
