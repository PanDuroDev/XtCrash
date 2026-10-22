package com.developer.crashx.config

import android.app.Activity

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef

import com.developer.crashx.CrashActivity

import java.io.Serializable
import java.lang.reflect.Modifier

/**
 * CrashX configuration.
 *
 * <p>Originally derived from CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 * <p>Modernized and maintained as CrashX by TutorialsAndroid.</p>
 */
class CrashConfig : Serializable {

    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    @IntDef(BACKGROUND_MODE_SILENT, BACKGROUND_MODE_SHOW_CUSTOM, BACKGROUND_MODE_CRASH)
    @Retention(AnnotationRetention.SOURCE)
    annotation class BackgroundMode

    companion object {
        private const val serialVersionUID = 7000000L

        /** Silently closes the app when a background crash happens. */
        const val BACKGROUND_MODE_SILENT = 0

        /** Shows the custom CrashX screen even if the app crashes in the background. */
        const val BACKGROUND_MODE_SHOW_CUSTOM = 1

        /** Lets the original/default uncaught exception handler handle background crashes. */
        const val BACKGROUND_MODE_CRASH = 2

        private const val DEFAULT_ERROR_TITLE = "Something went wrong"
        private const val DEFAULT_ERROR_MESSAGE = "The app ran into an unexpected problem. You can restart the app or send a crash report."
        private const val DEFAULT_REPORT_BUTTON_TEXT = "Send crash report"
        private const val DEFAULT_CRASH_ID_PREFIX = "CRASHX"

        private fun safeString(value: String?, fallback: String): String {
            return emptyToNull(value) ?: fallback
        }

        private fun emptyToNull(value: String?): String? {
            val trimmed = value?.trim()
            return trimmed?.takeIf { it.isNotEmpty() }
        }
    }

    @BackgroundMode
    var backgroundMode: Int = BACKGROUND_MODE_SHOW_CUSTOM

    var isEnabled: Boolean = true

    var isShowErrorDetails: Boolean = true

    var isShowRestartButton: Boolean = true

    var isShowCloseButton: Boolean = true

    var isLogErrorOnRestart: Boolean = true

    var isTrackActivities: Boolean = false

    var minTimeBetweenCrashesMs: Int = 3000
        set(value) {
            field = value.coerceAtLeast(0)
        }

    @DrawableRes
    var errorDrawable: Int? = null

    var errorActivityClass: Class<out Activity>? = null

    var restartActivityClass: Class<out Activity>? = null

    var eventListener: CrashActivity.EventListener? = null

    // CrashX v7 UI options
    var errorTitle: String? = DEFAULT_ERROR_TITLE
        get() = safeString(field, DEFAULT_ERROR_TITLE)
        set(value) {
            field = safeString(value, DEFAULT_ERROR_TITLE)
        }

    var errorMessage: String? = DEFAULT_ERROR_MESSAGE
        get() = safeString(field, DEFAULT_ERROR_MESSAGE)
        set(value) {
            field = safeString(value, DEFAULT_ERROR_MESSAGE)
        }

    var restartButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var closeButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var detailsButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var reportButtonText: String? = DEFAULT_REPORT_BUTTON_TEXT
        get() = safeString(field, DEFAULT_REPORT_BUTTON_TEXT)
        set(value) {
            field = safeString(value, DEFAULT_REPORT_BUTTON_TEXT)
        }

    var copyButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var supportEmail: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var reportSubject: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var reportChooserTitle: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var additionalReportInfo: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    var isShowReportButton: Boolean = false

    var isShowCopyButtonInDetails: Boolean = true

    var isShowCrashId: Boolean = true

    var isShowAppInfo: Boolean = true

    var isIncludeDeviceInfo: Boolean = true

    var isIncludeActivityLog: Boolean = true

    var isIncludeStackTrace: Boolean = true

    var isIncludeBuildDate: Boolean = true

    var isIncludeCrashId: Boolean = true

    var maxActivityLogEntries: Int = 50
        set(value) {
            field = value.coerceAtLeast(1)
        }

    var maxStackTraceSize: Int = 131071
        set(value) {
            field = value.coerceAtLeast(1024)
        }

    var crashIdPrefix: String? = DEFAULT_CRASH_ID_PREFIX
        get() = safeString(field, DEFAULT_CRASH_ID_PREFIX)
        set(value) {
            field = safeString(value, DEFAULT_CRASH_ID_PREFIX)
        }

    @ColorInt
    var backgroundColor: Int = 0xFFD50000.toInt()

    @ColorInt
    var cardBackgroundColor: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    var primaryButtonColor: Int = 0xFFD50000.toInt()

    @ColorInt
    var secondaryButtonColor: Int = 0xFFF3F4F6.toInt()

    @ColorInt
    var titleTextColor: Int = 0xFF111827.toInt()

    @ColorInt
    var messageTextColor: Int = 0xFF4B5563.toInt()

    @ColorInt
    var buttonTextColor: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    var secondaryButtonTextColor: Int = 0xFF111827.toInt()

    @ColorInt
    var metaTextColor: Int = 0xFF6B7280.toInt()

    /**
     * Fluent builder for CrashX configuration.
     */
    class Builder {

        private var config = CrashConfig()

        companion object {
            @JvmStatic
            fun create(): Builder {
                val builder = Builder()
                builder.config = copyOf(CrashActivity.getConfig())
                return builder
            }

            private fun copyOf(source: CrashConfig): CrashConfig {
                val target = CrashConfig()
                target.backgroundMode = source.backgroundMode
                target.isEnabled = source.isEnabled
                target.isShowErrorDetails = source.isShowErrorDetails
                target.isShowRestartButton = source.isShowRestartButton
                target.isShowCloseButton = source.isShowCloseButton
                target.isLogErrorOnRestart = source.isLogErrorOnRestart
                target.isTrackActivities = source.isTrackActivities
                target.minTimeBetweenCrashesMs = source.minTimeBetweenCrashesMs
                target.errorDrawable = source.errorDrawable
                target.errorActivityClass = source.errorActivityClass
                target.restartActivityClass = source.restartActivityClass
                target.eventListener = source.eventListener
                target.errorTitle = source.errorTitle
                target.errorMessage = source.errorMessage
                target.restartButtonText = source.restartButtonText
                target.closeButtonText = source.closeButtonText
                target.detailsButtonText = source.detailsButtonText
                target.reportButtonText = source.reportButtonText
                target.copyButtonText = source.copyButtonText
                target.supportEmail = source.supportEmail
                target.reportSubject = source.reportSubject
                target.reportChooserTitle = source.reportChooserTitle
                target.additionalReportInfo = source.additionalReportInfo
                target.isShowReportButton = source.isShowReportButton
                target.isShowCopyButtonInDetails = source.isShowCopyButtonInDetails
                target.isShowCrashId = source.isShowCrashId
                target.isShowAppInfo = source.isShowAppInfo
                target.isIncludeDeviceInfo = source.isIncludeDeviceInfo
                target.isIncludeActivityLog = source.isIncludeActivityLog
                target.isIncludeStackTrace = source.isIncludeStackTrace
                target.isIncludeBuildDate = source.isIncludeBuildDate
                target.isIncludeCrashId = source.isIncludeCrashId
                target.maxActivityLogEntries = source.maxActivityLogEntries
                target.maxStackTraceSize = source.maxStackTraceSize
                target.crashIdPrefix = source.crashIdPrefix
                target.backgroundColor = source.backgroundColor
                target.cardBackgroundColor = source.cardBackgroundColor
                target.primaryButtonColor = source.primaryButtonColor
                target.secondaryButtonColor = source.secondaryButtonColor
                target.titleTextColor = source.titleTextColor
                target.messageTextColor = source.messageTextColor
                target.buttonTextColor = source.buttonTextColor
                target.secondaryButtonTextColor = source.secondaryButtonTextColor
                target.metaTextColor = source.metaTextColor
                return target
            }
        }

        fun backgroundMode(backgroundMode: Int): Builder {
            config.backgroundMode = backgroundMode
            return this
        }

        fun enabled(enabled: Boolean): Builder {
            config.isEnabled = enabled
            return this
        }

        fun showErrorDetails(showErrorDetails: Boolean): Builder {
            config.isShowErrorDetails = showErrorDetails
            return this
        }

        fun showRestartButton(showRestartButton: Boolean): Builder {
            config.isShowRestartButton = showRestartButton
            return this
        }

        fun showCloseButton(showCloseButton: Boolean): Builder {
            config.isShowCloseButton = showCloseButton
            return this
        }

        fun logErrorOnRestart(logErrorOnRestart: Boolean): Builder {
            config.isLogErrorOnRestart = logErrorOnRestart
            return this
        }

        fun trackActivities(trackActivities: Boolean): Builder {
            config.isTrackActivities = trackActivities
            return this
        }

        fun minTimeBetweenCrashesMs(minTimeBetweenCrashesMs: Int): Builder {
            config.minTimeBetweenCrashesMs = minTimeBetweenCrashesMs
            return this
        }

        fun errorDrawable(errorDrawable: Int?): Builder {
            config.errorDrawable = errorDrawable
            return this
        }

        fun errorActivity(errorActivityClass: Class<out Activity>?): Builder {
            config.errorActivityClass = errorActivityClass
            return this
        }

        fun restartActivity(restartActivityClass: Class<out Activity>?): Builder {
            config.restartActivityClass = restartActivityClass
            return this
        }

        fun eventListener(eventListener: CrashActivity.EventListener?): Builder {
            if (eventListener != null
                    && eventListener.javaClass.enclosingClass != null
                    && !Modifier.isStatic(eventListener.javaClass.modifiers)) {
                throw IllegalArgumentException("The event listener cannot be an anonymous or non-static inner class because it must be serialized. Use a top-level class or static inner class.")
            }
            config.eventListener = eventListener
            return this
        }

        fun errorTitle(errorTitle: String?): Builder {
            config.errorTitle = errorTitle
            return this
        }

        fun errorMessage(errorMessage: String?): Builder {
            config.errorMessage = errorMessage
            return this
        }

        fun restartButtonText(restartButtonText: String?): Builder {
            config.restartButtonText = restartButtonText
            return this
        }

        fun closeButtonText(closeButtonText: String?): Builder {
            config.closeButtonText = closeButtonText
            return this
        }

        fun detailsButtonText(detailsButtonText: String?): Builder {
            config.detailsButtonText = detailsButtonText
            return this
        }

        fun reportButtonText(reportButtonText: String?): Builder {
            config.reportButtonText = reportButtonText
            return this
        }

        fun copyButtonText(copyButtonText: String?): Builder {
            config.copyButtonText = copyButtonText
            return this
        }

        fun supportEmail(supportEmail: String?): Builder {
            config.supportEmail = supportEmail
            return this
        }

        fun reportSubject(reportSubject: String?): Builder {
            config.reportSubject = reportSubject
            return this
        }

        fun reportChooserTitle(reportChooserTitle: String?): Builder {
            config.reportChooserTitle = reportChooserTitle
            return this
        }

        fun additionalReportInfo(additionalReportInfo: String?): Builder {
            config.additionalReportInfo = additionalReportInfo
            return this
        }

        fun showReportButton(showReportButton: Boolean): Builder {
            config.isShowReportButton = showReportButton
            return this
        }

        fun showCopyButtonInDetails(showCopyButtonInDetails: Boolean): Builder {
            config.isShowCopyButtonInDetails = showCopyButtonInDetails
            return this
        }

        fun showCrashId(showCrashId: Boolean): Builder {
            config.isShowCrashId = showCrashId
            return this
        }

        fun showAppInfo(showAppInfo: Boolean): Builder {
            config.isShowAppInfo = showAppInfo
            return this
        }

        fun includeDeviceInfo(includeDeviceInfo: Boolean): Builder {
            config.isIncludeDeviceInfo = includeDeviceInfo
            return this
        }

        fun includeActivityLog(includeActivityLog: Boolean): Builder {
            config.isIncludeActivityLog = includeActivityLog
            return this
        }

        fun includeStackTrace(includeStackTrace: Boolean): Builder {
            config.isIncludeStackTrace = includeStackTrace
            return this
        }

        fun includeBuildDate(includeBuildDate: Boolean): Builder {
            config.isIncludeBuildDate = includeBuildDate
            return this
        }

        fun includeCrashId(includeCrashId: Boolean): Builder {
            config.isIncludeCrashId = includeCrashId
            return this
        }

        fun maxActivityLogEntries(maxActivityLogEntries: Int): Builder {
            config.maxActivityLogEntries = maxActivityLogEntries
            return this
        }

        fun maxStackTraceSize(maxStackTraceSize: Int): Builder {
            config.maxStackTraceSize = maxStackTraceSize
            return this
        }

        fun crashIdPrefix(crashIdPrefix: String?): Builder {
            config.crashIdPrefix = crashIdPrefix
            return this
        }

        fun backgroundColor(backgroundColor: Int): Builder {
            config.backgroundColor = backgroundColor
            return this
        }

        fun cardBackgroundColor(cardBackgroundColor: Int): Builder {
            config.cardBackgroundColor = cardBackgroundColor
            return this
        }

        fun primaryButtonColor(primaryButtonColor: Int): Builder {
            config.primaryButtonColor = primaryButtonColor
            return this
        }

        fun secondaryButtonColor(secondaryButtonColor: Int): Builder {
            config.secondaryButtonColor = secondaryButtonColor
            return this
        }

        fun titleTextColor(titleTextColor: Int): Builder {
            config.titleTextColor = titleTextColor
            return this
        }

        fun messageTextColor(messageTextColor: Int): Builder {
            config.messageTextColor = messageTextColor
            return this
        }

        fun buttonTextColor(buttonTextColor: Int): Builder {
            config.buttonTextColor = buttonTextColor
            return this
        }

        fun secondaryButtonTextColor(secondaryButtonTextColor: Int): Builder {
            config.secondaryButtonTextColor = secondaryButtonTextColor
            return this
        }

        fun metaTextColor(metaTextColor: Int): Builder {
            config.metaTextColor = metaTextColor
            return this
        }

        fun get(): CrashConfig {
            return config
        }

        fun apply() {
            CrashActivity.setConfig(config)
        }
    }
}
