package io.github.soufianodev.xtcrash.report.internal

import android.content.Context

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.config.CrashConfig

/**
 * Single authoritative builder of the canonical crash report text.
 *
 * <p>XML and Compose both consume the text produced here (via
 * [CrashActivity.getAllErrorDetailsFromIntent], which delegates to the same
 * field-assembly order), so report formatting is not duplicated. The canonical
 * report is built only from information actually available to XtCrash in the
 * error process: the captured intent extras plus live package/device facts.
 * Information truncated by `maxStackTraceSize` at capture time is already
 * discarded and is not restored here.</p>
 */
internal object CanonicalReport {

    /**
     * Builds the canonical report from a config and a [FieldProvider] that
     * reads captured extras. Kept separate from Android [Context] so the field
     * set is unit-testable; callers supply device/app facts through [facts].
     */
    fun build(
        config: CrashConfig,
        provider: FieldProvider,
        facts: DeviceFacts,
    ): String {
        val out = StringBuilder()

        if (config.isIncludeCrashId) {
            out.append("Crash ID: ").append(provider.crashId).append('\n')
        }
        out.append("Crash date: ").append(provider.crashDate).append('\n')
        out.append("Crash thread: ").append(provider.threadName).append('\n')
        out.append("Exception: ").append(provider.throwableClass).append('\n')

        val message = provider.throwableMessage
        if (!message.isNullOrBlank()) {
            out.append("Message: ").append(message).append('\n')
        }
        out.append('\n')

        if (config.isIncludeDeviceInfo) {
            out.append("Package: ").append(facts.packageName).append('\n')
            out.append("App version: ").append(facts.versionName).append(" (")
                .append(facts.versionCode).append(")\n")
            out.append("XtCrash version: ").append(CrashActivity.VERSION).append('\n')
            if (config.isIncludeBuildDate) {
                facts.buildDate?.let {
                    out.append("Build date: ").append(it).append('\n')
                }
            }
            out.append("Android: ").append(facts.androidRelease)
                .append(" (API ").append(facts.sdkInt).append(")\n")
            out.append("Device: ").append(facts.deviceModel).append('\n')
            out.append("Brand: ").append(facts.brand).append('\n')
            out.append("Manufacturer: ").append(facts.manufacturer).append('\n')
            out.append('\n')
        }

        val additionalInfo = config.additionalReportInfo
        if (additionalInfo != null) {
            out.append("Additional information:\n").append(additionalInfo).append("\n\n")
        }

        if (config.isIncludeStackTrace) {
            out.append("Stack trace:\n")
            out.append(provider.stackTrace ?: "No stack trace available.").append('\n')
        }

        val activityLog = provider.activityLog
        if (config.isIncludeActivityLog && !activityLog.isNullOrBlank()) {
            out.append("\nActivity log:\n").append(activityLog)
        }

        return out.toString()
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
