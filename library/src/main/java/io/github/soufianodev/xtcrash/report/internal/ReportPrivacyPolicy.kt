package io.github.soufianodev.xtcrash.report.internal

/**
 * Pure helpers for the web-report privacy confirmation. Keeping the visibility
 * decisions and section order here lets unit tests cover the policy used by
 * production without requiring an Android UI or framework runtime.
 */
internal object ReportPrivacyPolicy {

    /**
     * Builds the confirmation sections from the prepared URL and report state.
     * HTTP warnings, prefill disclosure, and manual-paste guidance are selected
     * here so tests exercise the same decisions used by ReportDispatcher.
     */
    fun composeConfirmationSections(
        url: String,
        title: String?,
        description: String?,
        prefillOmitsReport: Boolean,
        reportCopiedToClipboard: Boolean,
        manualPasteNote: String,
        manualPasteNotCopiedNote: String,
        httpWarning: String,
        prefillDisclosure: String,
        destination: String,
        fields: String,
    ): String {
        val manualPasteMessage = when {
            !prefillOmitsReport -> null
            reportCopiedToClipboard -> manualPasteNote
            else -> manualPasteNotCopiedNote
        }
        val transportWarning = httpWarning.takeIf {
            IssueUrlBuilder.usesUnencryptedHttp(url)
        }
        val disclosure = prefillDisclosure.takeIf {
            !title.isNullOrEmpty() || !description.isNullOrEmpty()
        }

        return listOfNotNull(
            manualPasteMessage,
            transportWarning,
            disclosure,
            destination,
            fields,
        ).joinToString("\n\n")
    }
}
