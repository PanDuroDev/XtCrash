package io.github.soufianodev.xtcrash.report

import java.io.Serializable

/**
 * Immutable, process-safe snapshot of a prepared crash report handed to a
 * custom [ReportSender] or an optional [ReportActionHandler].
 *
 * <p>Every field is a plain immutable value: no Activity, logger, Binder
 * connection, or mutable internal state crosses the process boundary. The
 * value survives ordinary UI recreation and the `:xtcrash_error` process
 * because it is carried in the serialized crash config / intent payload.</p>
 *
 * <p>The canonical [reportText] is always the complete report. Destination
 * URL shortening (see [prefillOmitsReport]) never mutates it.</p>
 */
class PreparedReport : Serializable {

    companion object {
        private const val serialVersionUID = 7000001L
    }

    /** Identifier of the crash this report describes. */
    var crashId: String = ""
        internal set

    /**
     * Identifier of this logical reporting request. A single crash can have
     * more than one legitimate request, so this is distinct from [crashId] and
     * is the value to use for de-duplicating retries of the same request.
     */
    var requestId: String = ""
        internal set

    /**
     * The selected destination, or `null` when none is configured (generic
     * Android Sharesheet).
     */
    var destination: ReportDestination? = null
        internal set

    /** The complete canonical report text, never shortened. */
    var reportText: String = ""
        internal set

    /** Destination-specific subject or issue title. */
    var subject: String = ""
        internal set

    /**
     * The exact encoded destination URL when the selected destination is
     * web-based, otherwise `null`. Consumers must not rebuild or mutate it.
     */
    var url: String? = null
        internal set

    /**
     * `true` when a shortened URL prefill omits some canonical report content,
     * so the user must paste the remainder manually.
     */
    var prefillOmitsReport: Boolean = false
        internal set

    /**
     * `true` when the complete canonical report was copied to the clipboard to
     * compensate for a shortened or absent URL prefill.
     */
    var reportCopiedToClipboard: Boolean = false
        internal set

    override fun toString(): String {
        return "PreparedReport(crashId=$crashId, requestId=$requestId, destination=$destination, " +
                "subject=$subject, url=$url, prefillOmitsReport=$prefillOmitsReport, " +
                "reportCopiedToClipboard=$reportCopiedToClipboard, reportLength=${reportText.length})"
    }
}
