package io.github.soufianodev.xtcrash.report.internal

import android.content.Context
import android.os.Build

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Live package and device facts, plus the shared date-format factory.
 *
 * <p>These previously lived as private helpers inside [io.github.soufianodev.xtcrash.CrashActivity];
 * they are shared now so the canonical report and the crash-id/date generation
 * use one implementation.</p>
 */
internal object DeviceInfo {

    const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun createDateFormat(): DateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US)

    fun formatDate(date: Date = Date()): String = createDateFormat().format(date)

    fun buildFacts(context: Context, includeBuildDate: Boolean): CanonicalReport.DeviceFacts {
        return CanonicalReport.DeviceFacts(
            packageName = context.packageName,
            versionName = getVersionName(context),
            versionCode = getVersionCode(context),
            buildDate = if (includeBuildDate) getBuildDateAsString(context, createDateFormat()) else null,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = getDeviceModelName(),
            brand = Build.BRAND,
            manufacturer = Build.MANUFACTURER,
        )
    }

    private fun getBuildDateAsString(context: Context, dateFormat: DateFormat): String? {
        try {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            ZipFile(applicationInfo.sourceDir).use { zipFile ->
                val zipEntry = zipFile.getEntry("classes.dex") ?: return null
                val buildDate = zipEntry.time
                if (buildDate > 312764400000L) {
                    return dateFormat.format(Date(buildDate))
                }
            }
        } catch (ignored: Exception) {
            return null
        }
        return null
    }

    private fun getVersionName(context: Context): String {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(context: Context): Long {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.longVersionCode
            }
            return packageInfo.versionCode.toLong()
        } catch (e: Exception) {
            return -1
        }
    }

    private fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        return if (model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        val first = value[0]
        if (Character.isUpperCase(first)) {
            return value
        }
        return first.uppercaseChar() + value.substring(1)
    }
}
