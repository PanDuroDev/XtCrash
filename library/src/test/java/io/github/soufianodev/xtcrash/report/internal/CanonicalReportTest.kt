package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.ReportSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalReportTest {

    private fun facts(
        packageName: String = "com.example.app",
        versionName: String = "1.2.3",
        versionCode: Long = 42,
        buildDate: String? = "2026-01-01 00:00:00",
        androidRelease: String = "14",
        sdkInt: Int = 34,
        deviceModel: String = "Pixel 8",
        brand: String = "google",
        manufacturer: String = "Google",
    ) = CanonicalReport.DeviceFacts(
        packageName, versionName, versionCode, buildDate,
        androidRelease, sdkInt, deviceModel, brand, manufacturer,
    )

    private fun provider(
        crashId: String = "XTCRASH-ABC",
        crashDate: String = "2026-01-02 03:04:05",
        threadName: String = "main",
        throwableClass: String = "java.lang.RuntimeException",
        throwableMessage: String? = "boom",
        stackTrace: String? = "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.kt:1)",
        activityLog: String? = null,
    ) = object : CanonicalReport.FieldProvider {
        override val crashId get() = crashId
        override val crashDate get() = crashDate
        override val threadName get() = threadName
        override val throwableClass get() = throwableClass
        override val throwableMessage get() = throwableMessage
        override val stackTrace get() = stackTrace
        override val activityLog get() = activityLog
    }

    private fun config() = CrashConfig()

    @Test
    fun includesAllAvailableFields() {
        val report = CanonicalReport.build(config(), provider(), facts())
        assertTrue(report.contains("Crash ID: XTCRASH-ABC"))
        assertTrue(report.contains("Crash date: 2026-01-02 03:04:05"))
        assertTrue(report.contains("Crash thread: main"))
        assertTrue(report.contains("Exception: java.lang.RuntimeException"))
        assertTrue(report.contains("Message: boom"))
        assertTrue(report.contains("Package: com.example.app"))
        assertTrue(report.contains("App version: 1.2.3 (42)"))
        assertTrue(report.contains("Build date: 2026-01-01 00:00:00"))
        assertTrue(report.contains("Android: 14 (API 34)"))
        assertTrue(report.contains("Device: Pixel 8"))
        assertTrue(report.contains("Stack trace:"))
        assertTrue(report.contains("at Foo.bar"))
    }

    @Test
    fun omitsOptionalFieldsWhenDisabled() {
        val config = config().apply {
            isIncludeCrashId = false
            isIncludeDeviceInfo = false
            isIncludeStackTrace = false
            isIncludeBuildDate = false
        }
        val report = CanonicalReport.build(config, provider(), facts())
        assertFalse(report.contains("Crash ID:"))
        assertFalse(report.contains("Package:"))
        assertFalse(report.contains("Stack trace:"))
        assertFalse(report.contains("Build date:"))
    }

    @Test
    fun includesAdditionalReportInfo() {
        val config = config().apply { additionalReportInfo = "Screen: MainActivity" }
        val report = CanonicalReport.build(config, provider(), facts())
        assertTrue(report.contains("Additional information:"))
        assertTrue(report.contains("Screen: MainActivity"))
    }

    @Test
    fun handlesUnicodeAndMultilineContent() {
        val provider = provider(
            throwableMessage = "Ünïcode érreur — multi\nline message",
            activityLog = "créated\nresumed\n",
        )
        val report = CanonicalReport.build(config(), provider, facts())
        assertTrue(report.contains("Ünïcode érreur"))
        assertTrue(report.contains("multi\nline message"))
        assertTrue(report.contains("Activity log:"))
        assertTrue(report.contains("créated"))
    }

    @Test
    fun handlesLongExceptionMessageAndNestedCauses() {
        val longMessage = "m".repeat(20_000)
        val stack = "java.lang.RuntimeException: $longMessage\n" +
                "\tat A.b(A.kt:1)\n" +
                "Caused by: java.io.IOException: nested\n" +
                "\tat C.d(C.kt:2)"
        val report = CanonicalReport.build(config(), provider(throwableMessage = longMessage, stackTrace = stack), facts())
        assertTrue(report.contains(longMessage))
        assertTrue(report.contains("Caused by: java.io.IOException"))
        assertTrue(report.contains("at C.d"))
    }

    @Test
    fun missingStackTraceShowsPlaceholder() {
        val report = CanonicalReport.build(config(), provider(stackTrace = null), facts())
        assertTrue(report.contains("No stack trace available."))
    }

    @Test
    fun activityLogOnlyShownWhenPresent() {
        val withLog = CanonicalReport.build(config(), provider(activityLog = "created\n"), facts())
        assertTrue(withLog.contains("Activity log:"))

        val withoutLog = CanonicalReport.build(config(), provider(activityLog = null), facts())
        assertFalse(withoutLog.contains("Activity log:"))
    }

    @Test
    fun canonicalReportIsStableAcrossCalls() {
        val provider = provider()
        val facts = facts()
        val first = CanonicalReport.build(config(), provider, facts)
        val second = CanonicalReport.build(config(), provider, facts)
        assertEquals(first, second)
    }

    @Test
    fun canonicalReportIsNotMutatedByPrefillShortening() {
        val canonical = CanonicalReport.build(config(), provider(), facts())
        val blocks = listOf(
            PrefillShortener.Block(canonical),
        )
        val before = canonical
        PrefillShortener.shorten(blocks, budgetBytes = 100)
        assertEquals("shortening must not mutate the canonical report", before, canonical)
    }

    @Test
    fun exactDefaultReportText() {
        val expected = "Crash ID: XTCRASH-ABC\n" +
                "Crash date: 2026-01-02 03:04:05\n" +
                "Crash thread: main\n" +
                "Exception: java.lang.RuntimeException\n" +
                "Message: boom\n" +
                "\n" +
                "Package: com.example.app\n" +
                "App version: 1.2.3 (42)\n" +
                "XtCrash version: " + CrashActivity.VERSION + "\n" +
                "Build date: 2026-01-01 00:00:00\n" +
                "Android: 14 (API 34)\n" +
                "Device: Pixel 8\n" +
                "Brand: google\n" +
                "Manufacturer: Google\n" +
                "\n" +
                "Stack trace:\n" +
                "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.kt:1)\n"
        assertEquals(expected, CanonicalReport.build(config(), provider(), facts()))
    }

    @Test
    fun exactTextWithAllOptionalSections() {
        val provider = provider(activityLog = "created\nresumed")
        val config = config().apply { additionalReportInfo = "Screen: MainActivity" }
        val expected = "Crash ID: XTCRASH-ABC\n" +
                "Crash date: 2026-01-02 03:04:05\n" +
                "Crash thread: main\n" +
                "Exception: java.lang.RuntimeException\n" +
                "Message: boom\n" +
                "\n" +
                "Package: com.example.app\n" +
                "App version: 1.2.3 (42)\n" +
                "XtCrash version: " + CrashActivity.VERSION + "\n" +
                "Build date: 2026-01-01 00:00:00\n" +
                "Android: 14 (API 34)\n" +
                "Device: Pixel 8\n" +
                "Brand: google\n" +
                "Manufacturer: Google\n" +
                "\n" +
                "Additional information:\n" +
                "Screen: MainActivity\n" +
                "\n" +
                "Stack trace:\n" +
                "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.kt:1)\n" +
                "\n" +
                "Activity log:\n" +
                "created\nresumed"
        assertEquals(expected, CanonicalReport.build(config, provider, facts()))
    }

    @Test
    fun snapshotTextMatchesBuildAndSectionsConcat() {
        val provider = provider(activityLog = "created\nresumed")
        val config = config().apply { additionalReportInfo = "Screen: MainActivity" }
        val snapshot = CanonicalReport.buildSnapshot(config, provider, facts())
        assertEquals(CanonicalReport.build(config, provider, facts()), snapshot.text)
        assertEquals(snapshot.text, snapshot.sections.joinToString("") { it.text })
    }

    @Test
    fun snapshotSectionsFollowConfigFlags() {
        val config = config().apply {
            isIncludeCrashId = false
            isIncludeDeviceInfo = false
            isIncludeStackTrace = false
            isIncludeActivityLog = false
        }
        val snapshot = CanonicalReport.buildSnapshot(config, provider(), facts())
        assertEquals(listOf(ReportSection.Kind.HEADER), snapshot.sections.map { it.kind })
        val header = snapshot.sections.first().text
        assertFalse(header.contains("Crash ID:"))
        assertTrue(header.contains("Crash date:"))
        assertEquals(snapshot.text, snapshot.sections.joinToString("") { it.text })
    }

    @Test
    fun markerLikeMessageDoesNotCorruptSections() {
        val provider = provider(
            throwableMessage = "failed at Package: fake.app near Stack trace: marker",
        )
        val snapshot = CanonicalReport.buildSnapshot(config(), provider, facts())
        val kinds = snapshot.sections.map { it.kind }
        assertTrue(ReportSection.Kind.HEADER in kinds)
        assertTrue(ReportSection.Kind.DEVICE in kinds)
        assertTrue(ReportSection.Kind.STACK_TRACE in kinds)
        val device = snapshot.sections.first { it.kind == ReportSection.Kind.DEVICE }.text
        assertTrue(device.startsWith("Package: com.example.app\n"))
        assertFalse(device.contains("fake.app"))
        assertEquals(snapshot.text, snapshot.sections.joinToString("") { it.text })
    }
}
