package io.github.soufianodev.xtcrash.report.internal

import android.content.Context

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.ReportSection
import io.github.soufianodev.xtcrash.report.ReportSnapshot

/**
 * Single authoritative builder of the canonical crash report.
 *
 * <p>XML and Compose both consume the snapshot produced here (via
 * [CrashActivity.getReportFromIntent]), so report formatting is not
 * duplicated. The canonical report is built only from information actually
 * available to XtCrash in the error process: the captured intent extras plus
 * live package/device facts. Information truncated by `maxStackTraceSize` at
 * capture time is already discarded and is not restored here.</p>
 *
 * <p>The report is assembled as ordered [ReportSection]s whose concatenation
 * is the full text, so URL prefills project the same sections instead of
 * re-parsing text markers.</p>
 */
internal object CanonicalReport {

    /**
     * Builds the canonical report full text from a config and a
     * [FieldProvider] that reads captured extras. Equivalent to
     * [buildSnapshot]'s text.
     */
    fun build(
        config: CrashConfig,
        provider: FieldProvider,
        facts: DeviceFacts,
    ): String = buildSnapshot(config, provider, facts).text

    /**
     * Builds the canonical report as one immutable snapshot: the full text
     * plus ordered sections. [ReportSnapshot.text] is exactly the
     * concatenation of the section texts, in canonical order.
     */
    fun buildSnapshot(
        config: CrashConfig,
        provider: FieldProvider,
        facts: DeviceFacts,
    ): ReportSnapshot {
        val sections = mutableListOf<ReportSection>()

        val header = StringBuilder()
        if (config.isIncludeCrashId) {
            header.append("Crash ID: ").append(provider.crashId).append('\n')
        }
        header.append("Crash date: ").append(provider.crashDate).append('\n')
        header.append("Crash thread: ").append(provider.threadName).append('\n')
        header.append("Exception: ").append(provider.throwableClass).append('\n')

        val message = provider.throwableMessage
        if (!message.isNullOrBlank()) {
            header.append("Message: ").append(message).append('\n')
        }
        header.append('\n')
        sections.add(ReportSection(ReportSection.Kind.HEADER, header.toString()))

        if (config.isIncludeDeviceInfo) {
            val device = StringBuilder()
            device.append("Package: ").append(facts.packageName).append('\n')
            device.append("App version: ").append(facts.versionName).append(" (")
                .append(facts.versionCode).append(")\n")
            device.append("XtCrash version: ").append(CrashActivity.VERSION).append('\n')
            if (config.isIncludeBuildDate) {
                facts.buildDate?.let {
                    device.append("Build date: ").append(it).append('\n')
                }
            }
            device.append("Android: ").append(facts.androidRelease)
                .append(" (API ").append(facts.sdkInt).append(")\n")
            device.append("Device: ").append(facts.deviceModel).append('\n')
            device.append("Brand: ").append(facts.brand).append('\n')
            device.append("Manufacturer: ").append(facts.manufacturer).append('\n')
            device.append('\n')
            sections.add(ReportSection(ReportSection.Kind.DEVICE, device.toString()))
        }

        val additionalInfo = config.additionalReportInfo
        if (additionalInfo != null) {
            sections.add(ReportSection(
                ReportSection.Kind.ADDITIONAL_INFO,
                "Additional information:\n$additionalInfo\n\n",
            ))
        }

        if (config.isIncludeStackTrace) {
            val trace = provider.stackTrace ?: "No stack trace available."
            sections.add(ReportSection(ReportSection.Kind.STACK_TRACE, "Stack trace:\n$trace\n"))
        }

        val activityLog = provider.activityLog
        if (config.isIncludeActivityLog && !activityLog.isNullOrBlank()) {
            sections.add(ReportSection(ReportSection.Kind.ACTIVITY_LOG, "\nActivity log:\n$activityLog"))
        }

        return ReportSnapshot(sections.joinToString("") { it.text }, sections.toList())
    }

    /** Reads the captured crash extras. Backed by [CrashActivity] intent getters. */
    interface FieldProvider {
        val crashId: String
        val crashDate: String
        val threadName: String
        val throwableClass: String
        val throwableMessage: String?
        val stackTrace: String?
        val activityLog: String?
    }

    /** Live package and device facts resolved from a [Context]. */
    data class DeviceFacts(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val buildDate: String?,
        val androidRelease: String,
        val sdkInt: Int,
        val deviceModel: String,
        val brand: String,
        val manufacturer: String,
    )
}
