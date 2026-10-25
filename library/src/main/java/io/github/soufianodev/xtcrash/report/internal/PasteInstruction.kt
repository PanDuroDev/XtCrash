package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination

/**
 * Selects the clipboard instruction a fallback message may truthfully show.
 *
 * <p>Kept free of Android types so the rule that can contradict the actual
 * clipboard state is unit testable: a successful copy must never be followed
 * by "copy the report", and a failed copy must never claim one.</p>
 */
internal object PasteInstruction {

    enum class Kind {
        /** Nothing was copied: the user must copy before pasting. */
        COPY_THEN_PASTE,
        /** Copied and the destination is email: paste into an email. */
        EMAIL_COPIED,
        /** Copied: paste into the destination's form or issue page. */
        ISSUE_FORM,
    }

    fun select(copied: Boolean, destination: ReportDestination?): Kind = when {
        !copied -> Kind.COPY_THEN_PASTE
        destination == ReportDestination.EMAIL -> Kind.EMAIL_COPIED
        else -> Kind.ISSUE_FORM
    }
}
