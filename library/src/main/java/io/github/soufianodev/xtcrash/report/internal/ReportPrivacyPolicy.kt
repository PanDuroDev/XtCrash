package io.github.soufianodev.xtcrash.report.internal

/**
 * Pure helpers for the web-report privacy confirmation. Keeping the visibility
 * decisions and section order here lets unit tests cover the policy used by
 * production without requiring an Android UI or framework runtime.
 */
internal object ReportPrivacyPolicy {

    /**
     * Selects the truthful clipboard instruction for a shortened report.
     * This is shared by the production confirmation path and unit tests.
     */
    fun manualPasteNote(
        prefillOmitsReport: Boolean,
        reportCopiedToClipboard: Boolean,
        manualPasteNote: String,
        manualPasteNotCopiedNote: String,
    ): String? = when {
        !prefillOmitsReport -> null
        reportCopiedToClipboard -> manualPasteNote
        else -> manualPasteNotCopiedNote
    }

    /**
     * Includes the transport warning only for plaintext HTTP, the prefill
     * disclosure only when a non-empty title or description is actually in
     * the prepared URL, and the handler disclosure last so the user sees the
     * complete-report handoff after the destination and field details. The
     * caller supplies localized strings from resources.
     */
    fun composeConfirmationSections(
        url: String,
        title: String?,
        description: String?,
        httpWarning: String,
        prefillDisclosure: String,
        destination: String,
        fields: String,
        handlerDisclosure: String? = null,
    ): String {
        val transportWarning = httpWarning.takeIf {
            IssueUrlBuilder.usesUnencryptedHttp(url)
        }
        val disclosure = prefillDisclosure.takeIf {
            !title.isNullOrEmpty() || !description.isNullOrEmpty()
        }

        return listOfNotNull(
            transportWarning,
            disclosure,
            destination,
            fields,
            handlerDisclosure,
        ).joinToString("\n\n")
    }
}
