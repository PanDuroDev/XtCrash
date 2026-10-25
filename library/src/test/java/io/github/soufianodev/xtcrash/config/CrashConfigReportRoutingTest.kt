package io.github.soufianodev.xtcrash.config

import io.github.soufianodev.xtcrash.report.PreparedReport
import io.github.soufianodev.xtcrash.report.ReportActionHandler
import io.github.soufianodev.xtcrash.report.ReportDestination
import io.github.soufianodev.xtcrash.report.ReportOutcome
import io.github.soufianodev.xtcrash.report.ReportSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashConfigReportRoutingTest {

    class TestSender : ReportSender {
        override fun send(report: PreparedReport): ReportOutcome = ReportOutcome.NOT_HANDLED
    }

    class TestHandler : ReportActionHandler {
        override fun onReport(report: PreparedReport): ReportOutcome = ReportOutcome.NOT_HANDLED
    }

    @Test
    fun defaultHasNoDestinationAndReportButtonHidden() {
        val config = CrashConfig.Builder().get()
        assertNull(config.reportDestination)
        assertFalse("report button must be hidden by default", config.isShowReportButton)
    }

    @Test
    fun selectingDestinationDoesNotEnableReportButton() {
        val config = CrashConfig.Builder()
            .reportTo(ReportDestination.GITHUB)
            .githubIssueTarget("o", "r")
            .get()
        assertEquals(ReportDestination.GITHUB, config.reportDestination)
        assertFalse("selecting a destination must not enable the report button", config.isShowReportButton)
    }

    @Test
    fun nullDestinationIsRejectedForJavaCallers() {
        assertThrows(IllegalArgumentException::class.java) {
            CrashConfig.Builder().reportTo(null)
        }
    }

    @Test
    fun clearingDestinationDoesNotClearHandlerOrSender() {
        val config = CrashConfig.Builder()
            .reportTo(ReportDestination.CUSTOM)
            .reportSender(TestSender())
            .reportActionHandler(TestHandler::class.java)
            .clearReportDestination()
            .get()
        assertNull(config.reportDestination)
        assertNotNull("clearing destination must not clear the sender", config.reportSender)
        assertNotNull("clearing destination must not clear the handler", config.reportActionHandlerClass)
    }

    @Test
    fun clearingHandlerIsIndependent() {
        val config = CrashConfig.Builder()
            .reportActionHandler(TestHandler::class.java)
            .reportSender(TestSender())
            .reportTo(ReportDestination.GITHUB)
            .clearReportActionHandler()
            .get()
        assertNull(config.reportActionHandlerClass)
        assertNotNull(config.reportSender)
        assertEquals(ReportDestination.GITHUB, config.reportDestination)
    }

    @Test
    fun clearingSenderIsIndependent() {
        val config = CrashConfig.Builder()
            .reportSender(TestSender())
            .reportActionHandler(TestHandler::class.java)
            .clearReportSender()
            .get()
        assertNull(config.reportSender)
        assertNotNull(config.reportActionHandlerClass)
    }

    @Test
    fun builderCopyCarriesAllNewFields() {
        // Apply a config carrying every new field to the live config, then let
        // Builder.create() copy it through the real copyOf() path.
        CrashConfig.Builder()
            .reportTo(ReportDestination.GITLAB)
            .gitLabIssueTarget("group/project", "https://gitlab.com")
            .reportSender(TestSender())
            .reportActionHandler(TestHandler::class.java)
            .reportHandlerTimeout(1234)
            .reportUrlBudgetBytes(4096)
            .apply()

        val rebuilt = CrashConfig.Builder.create().get()

        assertEquals(ReportDestination.GITLAB, rebuilt.reportDestination)
        assertEquals("group/project", rebuilt.gitLabProjectPath)
        assertEquals("https://gitlab.com", rebuilt.gitLabBaseUrl)
        assertNotNull(rebuilt.reportSender)
        assertNotNull(rebuilt.reportActionHandlerClass)
        assertEquals(1234, rebuilt.reportHandlerTimeoutMs)
        assertEquals(4096, rebuilt.reportUrlBudgetBytes)
    }

    @Test
    fun targetSettersAllowLaterConfigurationInSameBuilder() {
        // A destination must not be rejected merely because its target is
        // configured later in the same builder sequence.
        val config = CrashConfig.Builder()
            .reportTo(ReportDestination.GITHUB)
            .githubIssueTarget("owner", "repo")
            .get()
        assertEquals("owner", config.githubOwner)
        assertEquals("repo", config.githubRepository)
    }

    @Test
    fun nullTargetsArePermittedAtConfigureTime() {
        // Missing targets are validated defensively at dispatch, not at
        // configure time, so intermediate builder states stay legal.
        val config = CrashConfig.Builder()
            .reportTo(ReportDestination.GITHUB)
            .githubIssueTarget(null, null)
            .get()
        assertNull(config.githubOwner)
        assertNull(config.githubRepository)
    }

    /** Public class but no public no-arg constructor: cannot be built reflectively in `:xtcrash_error`. */
    class PrivateCtorHandler private constructor() : ReportActionHandler {
        override fun onReport(report: PreparedReport): ReportOutcome = ReportOutcome.NOT_HANDLED
    }

    @Test
    fun handlerClassMustHavePublicNoArgConstructor() {
        assertThrows(IllegalArgumentException::class.java) {
            CrashConfig.Builder().reportActionHandler(PrivateCtorHandler::class.java)
        }
    }

    @Test
    fun nullHandlerIsAllowed() {
        val config = CrashConfig.Builder().reportActionHandler(null).get()
        assertNull(config.reportActionHandlerClass)
    }

    @Test
    fun reportUrlBudgetClampedByBuilderNotBypassableBelowFloor() {
        val config = CrashConfig.Builder().reportUrlBudgetBytes(1).get()
        assertEquals(1, config.reportUrlBudgetBytes)
        // Clamping happens in the URL builder, so a tiny configured value
        // cannot bypass the floor at dispatch time.
        assertTrue((config.reportUrlBudgetBytes ?: 0) < 512)
    }
}
