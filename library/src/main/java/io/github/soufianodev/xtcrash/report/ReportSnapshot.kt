package io.github.soufianodev.xtcrash.report

import java.util.Collections

/**
 * One ordered section of the canonical crash report.
 *
 * [text] contains the section exactly as it appears in the full report,
 * including its label lines and trailing separators, so concatenating the
 * sections of a [ReportSnapshot] reproduces [ReportSnapshot.text] byte for
 * byte. Sections are the single source for both the displayed report and
 * destination URL prefills, so prefill content is never re-parsed from text
 * markers.
 */
data class ReportSection(
    val kind: Kind,
    val text: String,
) {
    /** Section identifiers, in canonical report order. */
    enum class Kind {
        /** Crash id (when enabled), crash date, thread, throwable, message. */
        HEADER,

        /** Device and app facts: package, versions, OS, model, brand. */
        DEVICE,

        /** Developer-supplied [io.github.soufianodev.xtcrash.config.CrashConfig.additionalReportInfo]. */
        ADDITIONAL_INFO,

        /** Captured stack trace, when enabled. */
        STACK_TRACE,

        /** Tracked-activity log, when enabled and non-blank. */
        ACTIVITY_LOG,
    }
}

/**
 * Canonical crash report as one snapshot: the exact full [text] plus the
 * ordered [sections] it is built from.
 *
 * Built-in error screens obtain one snapshot per crash and use it for both
 * display and report dispatch, so the two can never disagree. The section
 * list is copied at construction, so neither a retained input list nor a
 * caller can mutate this snapshot afterwards.
 */
class ReportSnapshot(
    /** The complete canonical report text. */
    val text: String,
    sections: List<ReportSection>,
) {
    private val sectionList = Collections.unmodifiableList(sections.toList())

    /** The ordered sections that concatenate to [text], byte for byte. */
    val sections: List<ReportSection> get() = sectionList

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReportSnapshot) return false
        return text == other.text && sectionList == other.sectionList
    }

    override fun hashCode(): Int = 31 * text.hashCode() + sectionList.hashCode()

    override fun toString(): String = "ReportSnapshot(text=$text, sections=$sectionList)"
}
