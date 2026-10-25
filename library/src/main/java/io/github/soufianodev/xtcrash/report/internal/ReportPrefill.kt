package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportSection

/**
 * Pure mapping from canonical report sections to destination URL prefill
 * blocks. Sections are projected, never re-parsed from text markers, so the
 * inclusion configuration is respected automatically: a section excluded from
 * the canonical report does not exist in the list and therefore cannot enter
 * the prefill.
 */
internal object ReportPrefill {

    /** Prefill priority: triage value first, canonical order broken only here. */
    private val PRIORITY = listOf(
        ReportSection.Kind.HEADER,
        ReportSection.Kind.ADDITIONAL_INFO,
        ReportSection.Kind.STACK_TRACE,
        ReportSection.Kind.DEVICE,
        ReportSection.Kind.ACTIVITY_LOG,
    )

    /**
     * Prefill plan for a canonical report.
     *
     * @param blocks prioritized prefill blocks (empty when [unusableSections])
     * @param unusableSections true when the supplied sections are absent or do
     *   not describe [canonicalReport]; the caller must then produce no prefill
     *   body and treat a non-empty canonical report as omitted
     */
    data class Plan(
        val blocks: List<PrefillShortener.Block>,
        val unusableSections: Boolean,
    )

    /**
     * Validates the host-supplied sections against the canonical text and
     * maps usable sections to prioritized prefill blocks. Sections are
     * considered consistent only when they concatenate to exactly
     * [canonicalReport]; inconsistency is a caller bug and must never produce
     * a prefill that silently misrepresents the report.
     */
    fun plan(sections: List<ReportSection>?, canonicalReport: String): Plan {
        if (sections.isNullOrEmpty() || sections.joinToString("") { it.text } != canonicalReport) {
            return Plan(emptyList(), unusableSections = true)
        }
        // A repeated kind would make any one-section-per-kind projection drop
        // content while the concatenation check still passed, so it is treated
        // as an inconsistent section list rather than silently shortened.
        val kinds = sections.map { it.kind }
        if (kinds.toSet().size != kinds.size) {
            return Plan(emptyList(), unusableSections = true)
        }
        val byKind = sections.associateBy { it.kind }
        val blocks = PRIORITY.mapNotNull { kind ->
            byKind[kind]?.takeIf { it.text.isNotEmpty() }?.let { PrefillShortener.Block(it.text) }
        }
        return Plan(blocks, unusableSections = false)
    }
}
