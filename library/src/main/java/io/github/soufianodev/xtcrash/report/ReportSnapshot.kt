package io.github.soufianodev.xtcrash.report

/**
 * One ordered section of the canonical crash report.
 *
 * <p>[text] contains the section exactly as it appears in the full report,
 * including its label lines and trailing separators, so concatenating the
 * sections of a [ReportSnapshot] reproduces [ReportSnapshot.text] byte for
 * byte. Sections are the single source for both the displayed report and
 * destination URL prefills, so prefill content is never re-parsed from text
 * markers.</p>
 */
data class ReportSection(
    val kind: Kind,
    val text: String,
) {
    /** Section identities, in canonical report order. */
    enum class Kind {
        /** Crash ID (when enabled), date, thread, exception class, optional message. */
        HEADER,

        /** Package, app/XtCrash versions, optional build date, OS and device facts. */
        DEVICE,

        /** Developer-supplied [io.github.soufianodev.xtcrash.config.CrashConfig.additionalReportInfo]. */
        ADDITIONAL_INFO,

        /** Captured stack trace (when enabled). */
        STACK_TRACE,

        /** Tracked-activity log (when enabled and present). */
        ACTIVITY_LOG,
    }
}

/**
 * Canonical crash report as one immutable snapshot: the exact full text plus
 * its ordered [sections]. Built-in error screens obtain a single snapshot per
 * crash and use it for display and report dispatch, so the text and the
 * sections can never disagree.
 */
data class ReportSnapshot(
    /** The complete canonical report text. */
    val text: String,
    /** The ordered sections that concatenate to [text]. */
    val sections: List<ReportSection>,
)
