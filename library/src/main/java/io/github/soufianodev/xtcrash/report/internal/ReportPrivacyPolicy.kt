package io.github.soufianodev.xtcrash.report.internal

/**
 * Pure helpers for the web-report privacy confirmation. Keeping section order
 * independent of Android resources lets unit tests guard the most important
 * consent cues without requiring a UI or Android framework runtime.
 */
internal object ReportPrivacyPolicy {

    /**
     * Keeps manual-paste instructions and transport warnings ahead of long
     * URLs and prefilled field values in the confirmation dialog.
     */
    fun composeConfirmationSections(
        manualPasteNote: String?,
        httpWarning: String?,
        prefillDisclosure: String?,
        destination: String,
        fields: String,
    ): String = listOfNotNull(
        manualPasteNote,
        httpWarning,
        prefillDisclosure,
        destination,
        fields,
    ).joinToString("\n\n")
}
