package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.ReportSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrefillTest {

    private fun facts() = CanonicalReport.DeviceFacts(
        "com.example.app", "1.2.3", 42, "2026-01-01 00:00:00",
        "14", 34, "Pixel 8", "google", "Google",
    )

    private fun provider(
        stackTrace: String? = "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.kt:1)",
        activityLog: String? = "created\nresumed",
    ) = object : CanonicalReport.FieldProvider {
        override val crashId get() = "XTCRASH-ABC"
        override val crashDate get() = "2026-01-02 03:04:05"
        override val threadName get() = "main"
        override val throwableClass get() = "java.lang.RuntimeException"
        override val throwableMessage get() = "boom"
        override val stackTrace get() = stackTrace
        override val activityLog get() = activityLog
    }

    private fun config() = CrashConfig().apply { additionalReportInfo = "Screen: MainActivity" }

    private fun snapshot(config: CrashConfig = config()) =
        CanonicalReport.buildSnapshot(config, provider(), facts())

    private fun planOf(sections: List<ReportSection>?, text: String) =
        ReportPrefill.plan(sections, text)

    @Test
    fun completeSectionsMapInTriagePriority() {
        val snapshot = snapshot()
        val plan = planOf(snapshot.sections, snapshot.text)
        assertFalse(plan.unusableSections)
        assertEquals(
            listOf(
                snapshot.sections.first { it.kind == ReportSection.Kind.HEADER }.text,
                snapshot.sections.first { it.kind == ReportSection.Kind.ADDITIONAL_INFO }.text,
                snapshot.sections.first { it.kind == ReportSection.Kind.STACK_TRACE }.text,
                snapshot.sections.first { it.kind == ReportSection.Kind.DEVICE }.text,
                snapshot.sections.first { it.kind == ReportSection.Kind.ACTIVITY_LOG }.text,
            ),
            plan.blocks.map { it.text },
        )
    }

    @Test
    fun canonicalPriorityDiffersFromListOrderWithoutReorderingClaims() {
        // Sections arrive in canonical order (device before additional info);
        // the plan reorders without that counting as omission anywhere here.
        val snapshot = snapshot()
        assertEquals(
            listOf(
                ReportSection.Kind.HEADER,
                ReportSection.Kind.DEVICE,
                ReportSection.Kind.ADDITIONAL_INFO,
                ReportSection.Kind.STACK_TRACE,
                ReportSection.Kind.ACTIVITY_LOG,
            ),
            snapshot.sections.map { it.kind },
        )
        assertFalse(planOf(snapshot.sections, snapshot.text).unusableSections)
    }

    @Test
    fun excludedSectionIsAbsentFromBlocks() {
        val config = config().apply { isIncludeActivityLog = false }
        val snapshot = snapshot(config)
        val plan = planOf(snapshot.sections, snapshot.text)
        assertEquals(4, plan.blocks.size)
        assertFalse(plan.blocks.any { it.text.contains("Activity log:") })
    }

    @Test
    fun markerLikeMessageSurvivesProjection() {
        val provider = provider(stackTrace = "java.lang.RuntimeException: Package: fake\n\tat Foo.bar(Foo.kt:1)")
        val snapshot = CanonicalReport.buildSnapshot(config(), provider, facts())
        val plan = planOf(snapshot.sections, snapshot.text)
        assertFalse(plan.unusableSections)
        val header = plan.blocks.first().text
        assertTrue(header.contains("Message: boom"))
        assertTrue(plan.blocks.any { it.text.contains("Package: fake") })
        // device section carries the real package once, not a marker collision
        val deviceBlock = plan.blocks.first { it.text.startsWith("Package: com.example.app") }
        assertEquals(1, Regex("Package: com.example.app").findAll(deviceBlock.text).count())
    }

    @Test
    fun nullSectionsAreUnusable() {
        val snapshot = snapshot()
        val plan = planOf(null, snapshot.text)
        assertTrue(plan.unusableSections)
        assertTrue(plan.blocks.isEmpty())
    }

    @Test
    fun emptySectionsAreUnusable() {
        val snapshot = snapshot()
        val plan = planOf(emptyList(), snapshot.text)
        assertTrue(plan.unusableSections)
        assertTrue(plan.blocks.isEmpty())
    }

    @Test
    fun inconsistentSectionsAreUnusable() {
        val snapshot = snapshot()
        val plan = planOf(snapshot.sections, snapshot.text + "extra")
        assertTrue(plan.unusableSections)
        assertTrue(plan.blocks.isEmpty())
    }

    @Test
    fun duplicateSectionKindIsUnusableRatherThanSilentlyShortened() {
        val snapshot = snapshot()
        // Concatenation still matches, but one section per kind is required:
        // a projection would otherwise drop the first HEADER while
        // prefillOmitsReport stayed false.
        val duplicated = snapshot.sections +
                ReportSection(ReportSection.Kind.HEADER, "Crash ID: OTHER\n")
        val plan = planOf(duplicated, snapshot.text + "Crash ID: OTHER\n")
        assertTrue(plan.unusableSections)
        assertTrue(plan.blocks.isEmpty())
    }

    @Test
    fun emptySectionsAndTextYieldUnusablePlanWithoutOmission() {
        val plan = planOf(emptyList(), "")
        // The plan is unusable, but the caller-side rule
        // (canonicalReport.isNotEmpty()) marks no omission for an empty report.
        assertTrue(plan.unusableSections)
        assertTrue(plan.blocks.isEmpty())
    }

    @Test
    fun crashIdDisabledKeepsHeaderWithoutCrashId() {
        val config = config().apply { isIncludeCrashId = false }
        val snapshot = snapshot(config)
        val plan = planOf(snapshot.sections, snapshot.text)
        assertFalse(plan.unusableSections)
        assertFalse(plan.blocks.first().text.contains("Crash ID:"))
    }

    @Test
    fun stackTraceDisabledExcludesTraceBlock() {
        val config = config().apply { isIncludeStackTrace = false }
        val snapshot = snapshot(config)
        val plan = planOf(snapshot.sections, snapshot.text)
        assertFalse(plan.blocks.any { it.text.startsWith("Stack trace:") })
        assertEquals(
            listOf(
                ReportSection.Kind.HEADER,
                ReportSection.Kind.ADDITIONAL_INFO,
                ReportSection.Kind.DEVICE,
                ReportSection.Kind.ACTIVITY_LOG,
            ),
            plan.blocks.map { block ->
                snapshot.sections.first { it.text == block.text }.kind
            },
        )
    }
}
