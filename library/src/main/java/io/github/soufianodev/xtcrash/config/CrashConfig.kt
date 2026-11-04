package io.github.soufianodev.xtcrash.config

import android.app.Activity

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.log.CrashLogListener
import io.github.soufianodev.xtcrash.report.ReportActionHandler
import io.github.soufianodev.xtcrash.report.ReportDestination
import io.github.soufianodev.xtcrash.report.ReportSender

import java.io.Serializable
import java.lang.reflect.Modifier

/**
 * XtCrash configuration.
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

        /** Shows the custom XtCrash screen even if the app crashes in the background. */
        const val BACKGROUND_MODE_SHOW_CUSTOM = 1

        /** Lets the original/default uncaught exception handler handle background crashes. */
        const val BACKGROUND_MODE_CRASH = 2

        private const val DEFAULT_ERROR_TITLE = "Something went wrong"
        private const val DEFAULT_ERROR_MESSAGE = "The app ran into an unexpected problem. You can restart the app or send a crash report."
        private const val DEFAULT_REPORT_BUTTON_TEXT = "Send crash report"
        private const val DEFAULT_CRASH_ID_PREFIX = "XTCRASH"

        private fun safeString(value: String?, fallback: String): String {
            return emptyToNull(value) ?: fallback
        }

        private fun emptyToNull(value: String?): String? {
            val trimmed = value?.trim()
            return trimmed?.takeIf { it.isNotEmpty() }
        }
    }

    /** Controls behavior when a crash occurs while the app is in the background. Defaults to BACKGROUND_MODE_SHOW_CUSTOM. */
    @BackgroundMode
    var backgroundMode: Int = BACKGROUND_MODE_SHOW_CUSTOM

    /** Whether XtCrash handles uncaught exceptions. Defaults to true. */
    var isEnabled: Boolean = true

    /** Whether the screen offers technical error details. Defaults to true. */
    var isShowErrorDetails: Boolean = true

    /** Whether the screen offers Restart when a restart target is available. Defaults to true. */
    var isShowRestartButton: Boolean = true

    /** Whether the screen offers the close action. Defaults to true. */
    var isShowCloseButton: Boolean = true

    /** Whether the previous crash stack trace is emitted when the error screen opens. Defaults to true. */
    var isLogErrorOnRestart: Boolean = true

    /**
     * Whether XtCrash writes its own lifecycle and crash messages to the Android
     * logcat. Default: false, because logging is opt-in — developers who want
     * the library visible in logcat enable it explicitly, and everyone else gets
     * a silent library.
     *
     * Install-time events are logged only when the config is applied before the
     * init provider runs (configure in `Application.attachBaseContext()`);
     * later-applied configs still cover every crash-time and error-screen event.
     */
    var isLoggingEnabled: Boolean = false

    /**
     * Optional listener that receives the same diagnostic events that logcat
     * would receive, so they can enter the developer's own logging framework.
     * The library only forwards them; it never formats or stores them.
     *
     * Independent of [isLoggingEnabled]: both sinks are allowed simultaneously.
     * Must be a top-level or static class because it is serialized into the
     * error-screen process.
     */
    var logListener: CrashLogListener? = null

    /** Whether XtCrash records a bounded activity lifecycle log. Defaults to false. */
    var isTrackActivities: Boolean = false

    /** Minimum interval in milliseconds used by the crash-loop guard. Values are clamped to zero or greater; the default is 3000. */
    var minTimeBetweenCrashesMs: Int = 3000
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** Optional drawable resource ID for the error-screen icon; null uses the library default. */
    @DrawableRes
    var errorDrawable: Int? = null

    /** Optional custom error activity class. It must be declared in the merged manifest and run in a separate process. */
    var errorActivityClass: Class<out Activity>? = null

    /** Optional explicit activity used when restarting the app; null lets XtCrash resolve a launcher target. */
    var restartActivityClass: Class<out Activity>? = null

    /** Optional lifecycle callback listener. Implementations must be serializable and process-safe. */
    var eventListener: CrashActivity.EventListener? = null

    // XtCrash v7 UI options
    /** Title shown on the error screen. Null or blank values fall back to the built-in default. */
    var errorTitle: String? = DEFAULT_ERROR_TITLE
        get() = safeString(field, DEFAULT_ERROR_TITLE)
        set(value) {
            field = safeString(value, DEFAULT_ERROR_TITLE)
        }

    /** Message shown on the error screen. Null or blank values fall back to the built-in default. */
    var errorMessage: String? = DEFAULT_ERROR_MESSAGE
        get() = safeString(field, DEFAULT_ERROR_MESSAGE)
        set(value) {
            field = safeString(value, DEFAULT_ERROR_MESSAGE)
        }

    /** Optional Restart label; null or blank uses the localized default label. */
    var restartButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional Close label; null or blank uses the localized default label. */
    var closeButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional technical-details label; null or blank uses the localized default label. */
    var detailsButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Report action label. Null or blank values fall back to the built-in default. */
    var reportButtonText: String? = DEFAULT_REPORT_BUTTON_TEXT
        get() = safeString(field, DEFAULT_REPORT_BUTTON_TEXT)
        set(value) {
            field = safeString(value, DEFAULT_REPORT_BUTTON_TEXT)
        }

    /** Optional clipboard action label; null or blank uses the localized default label. */
    var copyButtonText: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional recipient suggested for the email report draft. Blank input is normalized to null. */
    var supportEmail: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional report subject override. When absent, XtCrash generates a subject based on app information and crash-ID settings. */
    var reportSubject: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional title for the Android chooser when applicable. Blank input is normalized to null. */
    var reportChooserTitle: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Optional developer-provided text appended to the crash report. Blank input is normalized to null. */
    var additionalReportInfo: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Whether the error screen displays its report/share action. Defaults to false. */
    var isShowReportButton: Boolean = false

    /** Whether technical details include a copy action. Defaults to true. */
    var isShowCopyButtonInDetails: Boolean = true

    /** Whether the generated crash ID is displayed on the screen. Defaults to true. */
    var isShowCrashId: Boolean = true

    /** Whether app/package information is displayed. Defaults to true. */
    var isShowAppInfo: Boolean = true

    /** Whether device information is included in the report. Defaults to true. */
    var isIncludeDeviceInfo: Boolean = true

    /** Whether the activity lifecycle log is included when available. Defaults to true. */
    var isIncludeActivityLog: Boolean = true

    /** Whether the stack trace is included when available. Defaults to true. */
    var isIncludeStackTrace: Boolean = true

    /** Whether build-date metadata is included when available. Defaults to true. */
    var isIncludeBuildDate: Boolean = true

    /** Whether the crash ID is included in the report. Defaults to true. */
    var isIncludeCrashId: Boolean = true

    /** Maximum number of activity-log entries retained. Values are clamped to at least one; the default is 50. */
    var maxActivityLogEntries: Int = 50
        set(value) {
            field = value.coerceAtLeast(1)
        }

    /** Maximum stack-trace character count. Values are clamped to at least 1024; the default is 131071. */
    var maxStackTraceSize: Int = 131071
        set(value) {
            field = value.coerceAtLeast(1024)
        }

    /** Prefix used for generated crash IDs. Null or blank values fall back to XTCRASH. */
    var crashIdPrefix: String? = DEFAULT_CRASH_ID_PREFIX
        get() = safeString(field, DEFAULT_CRASH_ID_PREFIX)
        set(value) {
            field = safeString(value, DEFAULT_CRASH_ID_PREFIX)
        }

    /** ARGB color for the error-screen background. */
    @ColorInt
    var backgroundColor: Int = 0xFFD50000.toInt()

    /** ARGB color for the error-screen card. */
    @ColorInt
    var cardBackgroundColor: Int = 0xFFFFFFFF.toInt()

    /** ARGB color for primary actions. */
    @ColorInt
    var primaryButtonColor: Int = 0xFFD50000.toInt()

    /** ARGB color for secondary actions. */
    @ColorInt
    var secondaryButtonColor: Int = 0xFFF3F4F6.toInt()

    /** ARGB color for the error-screen title text. */
    @ColorInt
    var titleTextColor: Int = 0xFF111827.toInt()

    /** ARGB color for the error-screen message text. */
    @ColorInt
    var messageTextColor: Int = 0xFF4B5563.toInt()

    /** ARGB color for primary button text. */
    @ColorInt
    var buttonTextColor: Int = 0xFFFFFFFF.toInt()

    /** ARGB color for secondary button text. */
    @ColorInt
    var secondaryButtonTextColor: Int = 0xFF111827.toInt()

    /** ARGB color for metadata such as the crash ID. */
    @ColorInt
    var metaTextColor: Int = 0xFF6B7280.toInt()

    /**
     * The selected report destination, or null for the generic Android
     * Sharesheet (the pre-existing default). Independent of the report-action
     * handler and the custom sender.
     */
    var reportDestination: ReportDestination? = null

    /** GitHub repository owner. Required when the destination is GITHUB. */
    var githubOwner: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** GitHub repository name. Required when the destination is GITHUB. */
    var githubRepository: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** GitLab instance base URL. Required when the destination is GITLAB. */
    var gitLabBaseUrl: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** GitLab namespace-qualified project path. Required for GITLAB. */
    var gitLabProjectPath: String? = null
        get() = emptyToNull(field)
        set(value) {
            field = emptyToNull(value)
        }

    /** Developer-defined sender for the CUSTOM destination. */
    var reportSender: ReportSender? = null

    /**
     * Optional handler class that may accept responsibility for a prepared
     * report. Stored as a class (not an instance) so it can be constructed
     * process-safely in the error screen process.
     */
    var reportActionHandlerClass: Class<out ReportActionHandler>? = null

    /**
     * Deadline in milliseconds for constructing and invoking the report-action
     * handler or custom sender on a worker thread.
     */
    var reportHandlerTimeoutMs: Int = 5000
        set(value) {
            field = value.coerceAtLeast(1)
        }

    /**
     * Optional override for the encoded-URL byte budget used when prefilling
     * GitHub/GitLab issue forms. Null uses the conservative default.
     */
    var reportUrlBudgetBytes: Int? = null

    /**
     * Fluent builder for XtCrash configuration.
     */
    class Builder {

        private var config = CrashConfig()

        companion object {
            /**
             * Best-effort guard so a listener cannot turn a crash into a lost error
             * screen: the listener travels inside the serialized config, so its
             * declared non-static, non-transient fields must have serializable
             * types. This catches the common failures at configure time; it cannot
             * prove everything, e.g. a field declared as Any/Object or as an
             * interface can still hold non-serializable runtime state. Only
             * serializable superclasses are walked: a non-serializable ancestor's
             * fields are initialized by its accessible no-argument constructor
             * instead of the stream, so that constructor must exist.
             */
            private fun checkListenerFields(listenerName: String, listener: Serializable) {
                var clazz: Class<*> = listener.javaClass
                while (true) {
                    for (field in clazz.declaredFields) {
                        if (Modifier.isStatic(field.modifiers)
                                || Modifier.isTransient(field.modifiers)
                                || field.type.isPrimitive) {
                            continue
                        }
                        if (!Serializable::class.java.isAssignableFrom(field.type)) {
                            throw IllegalArgumentException(
                                    "The " + listenerName + " field \"" + field.name + "\" ("
                                            + field.type.name + ") is not java.io.Serializable, but the listener is carried inside the serialized crash config. Make the field Serializable, mark it transient, or remove it.")
                        }
                    }
                    val superclass = clazz.superclass ?: break
                    if (!Serializable::class.java.isAssignableFrom(superclass)) {
                        // A non-serializable ancestor's state is not written to the
                        // stream; deserialization initializes it through the
                        // ancestor's no-argument constructor.
                        val constructor = try {
                            superclass.getDeclaredConstructor()
                        } catch (_: NoSuchMethodException) {
                            null
                        }
                        val listenerClass = listener.javaClass
                        val samePackage = listenerClass.classLoader === superclass.classLoader
                                && listenerClass.name.substringBeforeLast('.', "") ==
                                superclass.name.substringBeforeLast('.', "")
                        if (constructor == null || Modifier.isPrivate(constructor.modifiers)
                                || (!Modifier.isPublic(constructor.modifiers)
                                && !Modifier.isProtected(constructor.modifiers) && !samePackage)) {
                            throw IllegalArgumentException(
                                    "The " + listenerName + " superclass " + superclass.name
                                            + " is not java.io.Serializable and must have an accessible no-argument constructor so the listener can be deserialized.")
                        }
                        break
                    }
                    clazz = superclass
                }
            }

            /**
             * Validates that a handler class can be constructed in the error
             * screen process: it must be public with a public no-argument
             * constructor, so it can be instantiated by reflection there rather
             * than relying on an instance registered in the original process.
             */
            private fun validateHandlerClass(handlerClass: Class<*>) {
                if (!Modifier.isPublic(handlerClass.modifiers)) {
                    throw IllegalArgumentException("The report action handler " + handlerClass.name
                            + " must be public so it can be constructed in the error screen process.")
                }
                try {
                    val constructor = handlerClass.getDeclaredConstructor()
                    if (!Modifier.isPublic(constructor.modifiers)) {
                        throw IllegalArgumentException("The report action handler " + handlerClass.name
                                + " must have a public no-argument constructor so it can be constructed in the error screen process.")
                    }
                } catch (e: NoSuchMethodException) {
                    throw IllegalArgumentException("The report action handler " + handlerClass.name
                            + " must have a public no-argument constructor so it can be constructed in the error screen process.")
                }
            }

            /** Creates a builder initialized from a copy of the currently applied configuration. */
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
                target.isLoggingEnabled = source.isLoggingEnabled
                target.logListener = source.logListener
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
                target.reportDestination = source.reportDestination
                target.githubOwner = source.githubOwner
                target.githubRepository = source.githubRepository
                target.gitLabBaseUrl = source.gitLabBaseUrl
                target.gitLabProjectPath = source.gitLabProjectPath
                target.reportSender = source.reportSender
                target.reportActionHandlerClass = source.reportActionHandlerClass
                target.reportHandlerTimeoutMs = source.reportHandlerTimeoutMs
                target.reportUrlBudgetBytes = source.reportUrlBudgetBytes
                return target
            }
        }

        /** Sets the behavior used when a crash occurs while the app is in the background. */
        fun backgroundMode(backgroundMode: Int): Builder {
            config.backgroundMode = backgroundMode
            return this
        }

        /** Sets whether XtCrash handles uncaught exceptions. */
        fun enabled(enabled: Boolean): Builder {
            config.isEnabled = enabled
            return this
        }

        /** Sets whether the technical-details action is shown. */
        fun showErrorDetails(showErrorDetails: Boolean): Builder {
            config.isShowErrorDetails = showErrorDetails
            return this
        }

        /** Sets whether the Restart action is shown when a restart target is available. */
        fun showRestartButton(showRestartButton: Boolean): Builder {
            config.isShowRestartButton = showRestartButton
            return this
        }

        /** Sets whether the Close action is shown. */
        fun showCloseButton(showCloseButton: Boolean): Builder {
            config.isShowCloseButton = showCloseButton
            return this
        }

        /** Sets whether the previous crash stack trace is emitted when the error screen opens. */
        fun logErrorOnRestart(logErrorOnRestart: Boolean): Builder {
            config.isLogErrorOnRestart = logErrorOnRestart
            return this
        }

        /** Sets whether the activity lifecycle log is collected. */
        fun trackActivities(trackActivities: Boolean): Builder {
            config.isTrackActivities = trackActivities
            return this
        }

        /** Sets the minimum interval between displayed crash screens; values below zero are clamped to zero. */
        fun minTimeBetweenCrashesMs(minTimeBetweenCrashesMs: Int): Builder {
            config.minTimeBetweenCrashesMs = minTimeBetweenCrashesMs
            return this
        }

        /** Sets the optional drawable resource used as the error icon. */
        fun errorDrawable(errorDrawable: Int?): Builder {
            config.errorDrawable = errorDrawable
            return this
        }

        /** Sets the custom error activity class. The activity must be declared in a separate process. */
        fun errorActivity(errorActivityClass: Class<out Activity>?): Builder {
            config.errorActivityClass = errorActivityClass
            return this
        }

        /** Sets the explicit activity used to restart the app. */
        fun restartActivity(restartActivityClass: Class<out Activity>?): Builder {
            config.restartActivityClass = restartActivityClass
            return this
        }

        /** Sets the serializable error-screen lifecycle listener. */
        fun eventListener(eventListener: CrashActivity.EventListener?): Builder {
            if (eventListener != null) {
                if (eventListener.javaClass.enclosingClass != null
                        && !Modifier.isStatic(eventListener.javaClass.modifiers)) {
                    throw IllegalArgumentException("The event listener cannot be an anonymous or non-static inner class because it must be serialized. Use a top-level class or static inner class.")
                }
                checkListenerFields("event listener", eventListener)
            }
            config.eventListener = eventListener
            return this
        }

        /** Enables or disables XtCrash diagnostic output to logcat. */
        fun loggingEnabled(isLoggingEnabled: Boolean): Builder {
            config.isLoggingEnabled = isLoggingEnabled
            return this
        }

        /** Sets the optional serializable sink for diagnostic messages. */
        fun logListener(logListener: CrashLogListener?): Builder {
            if (logListener != null) {
                if (logListener.javaClass.enclosingClass != null
                        && !Modifier.isStatic(logListener.javaClass.modifiers)) {
                    throw IllegalArgumentException("The log listener cannot be an anonymous or non-static inner class because it must be serialized. Use a top-level class or static inner class.")
                }
                checkListenerFields("log listener", logListener)
            }
            config.logListener = logListener
            return this
        }

        /** Sets the error-screen title; null or blank values use the default. */
        fun errorTitle(errorTitle: String?): Builder {
            config.errorTitle = errorTitle
            return this
        }

        /** Sets the error-screen message; null or blank values use the default. */
        fun errorMessage(errorMessage: String?): Builder {
            config.errorMessage = errorMessage
            return this
        }

        /** Sets the Restart button label; null or blank values use the localized default. */
        fun restartButtonText(restartButtonText: String?): Builder {
            config.restartButtonText = restartButtonText
            return this
        }

        /** Sets the Close button label; null or blank values use the localized default. */
        fun closeButtonText(closeButtonText: String?): Builder {
            config.closeButtonText = closeButtonText
            return this
        }

        /** Sets the technical-details button label; null or blank values use the localized default. */
        fun detailsButtonText(detailsButtonText: String?): Builder {
            config.detailsButtonText = detailsButtonText
            return this
        }

        /** Sets the report button label; null or blank values use the default. */
        fun reportButtonText(reportButtonText: String?): Builder {
            config.reportButtonText = reportButtonText
            return this
        }

        /** Sets the copy button label; null or blank values use the localized default. */
        fun copyButtonText(copyButtonText: String?): Builder {
            config.copyButtonText = copyButtonText
            return this
        }

        /** Sets the optional email recipient suggested in the report draft. */
        fun supportEmail(supportEmail: String?): Builder {
            config.supportEmail = supportEmail
            return this
        }

        /** Sets an explicit report subject; null lets XtCrash generate one. */
        fun reportSubject(reportSubject: String?): Builder {
            config.reportSubject = reportSubject
            return this
        }

        /** Sets the chooser title used by applicable sharing flows. */
        fun reportChooserTitle(reportChooserTitle: String?): Builder {
            config.reportChooserTitle = reportChooserTitle
            return this
        }

        /** Sets extra developer-provided text included in the report. */
        fun additionalReportInfo(additionalReportInfo: String?): Builder {
            config.additionalReportInfo = additionalReportInfo
            return this
        }

        /** Sets whether the report/share action is displayed. */
        fun showReportButton(showReportButton: Boolean): Builder {
            config.isShowReportButton = showReportButton
            return this
        }

        /** Sets whether a copy action is shown in the details dialog. */
        fun showCopyButtonInDetails(showCopyButtonInDetails: Boolean): Builder {
            config.isShowCopyButtonInDetails = showCopyButtonInDetails
            return this
        }

        /** Sets whether the crash ID is displayed on the screen. */
        fun showCrashId(showCrashId: Boolean): Builder {
            config.isShowCrashId = showCrashId
            return this
        }

        /** Sets whether app information is shown on the error screen. */
        fun showAppInfo(showAppInfo: Boolean): Builder {
            config.isShowAppInfo = showAppInfo
            return this
        }

        /** Sets whether device information is included in reports. */
        fun includeDeviceInfo(includeDeviceInfo: Boolean): Builder {
            config.isIncludeDeviceInfo = includeDeviceInfo
            return this
        }

        /** Sets whether the activity log is included in reports. */
        fun includeActivityLog(includeActivityLog: Boolean): Builder {
            config.isIncludeActivityLog = includeActivityLog
            return this
        }

        /** Sets whether the stack trace is included in reports. */
        fun includeStackTrace(includeStackTrace: Boolean): Builder {
            config.isIncludeStackTrace = includeStackTrace
            return this
        }

        /** Sets whether build-date metadata is included in reports. */
        fun includeBuildDate(includeBuildDate: Boolean): Builder {
            config.isIncludeBuildDate = includeBuildDate
            return this
        }

        /** Sets whether the crash ID is included in reports. */
        fun includeCrashId(includeCrashId: Boolean): Builder {
            config.isIncludeCrashId = includeCrashId
            return this
        }

        /** Sets the maximum number of retained activity-log entries; the value is clamped to at least one. */
        fun maxActivityLogEntries(maxActivityLogEntries: Int): Builder {
            config.maxActivityLogEntries = maxActivityLogEntries
            return this
        }

        /** Sets the maximum stack-trace length; the value is clamped to at least 1024 characters. */
        fun maxStackTraceSize(maxStackTraceSize: Int): Builder {
            config.maxStackTraceSize = maxStackTraceSize
            return this
        }

        /** Sets the prefix for generated crash IDs; null or blank values use XTCRASH. */
        fun crashIdPrefix(crashIdPrefix: String?): Builder {
            config.crashIdPrefix = crashIdPrefix
            return this
        }

        /** Sets the ARGB background color. */
        fun backgroundColor(backgroundColor: Int): Builder {
            config.backgroundColor = backgroundColor
            return this
        }

        /** Sets the ARGB card color. */
        fun cardBackgroundColor(cardBackgroundColor: Int): Builder {
            config.cardBackgroundColor = cardBackgroundColor
            return this
        }

        /** Sets the ARGB primary-action color. */
        fun primaryButtonColor(primaryButtonColor: Int): Builder {
            config.primaryButtonColor = primaryButtonColor
            return this
        }

        /** Sets the ARGB secondary-action color. */
        fun secondaryButtonColor(secondaryButtonColor: Int): Builder {
            config.secondaryButtonColor = secondaryButtonColor
            return this
        }

        /** Sets the ARGB title-text color. */
        fun titleTextColor(titleTextColor: Int): Builder {
            config.titleTextColor = titleTextColor
            return this
        }

        /** Sets the ARGB message-text color. */
        fun messageTextColor(messageTextColor: Int): Builder {
            config.messageTextColor = messageTextColor
            return this
        }

        /** Sets the ARGB primary-button text color. */
        fun buttonTextColor(buttonTextColor: Int): Builder {
            config.buttonTextColor = buttonTextColor
            return this
        }

        /** Sets the ARGB secondary-button text color. */
        fun secondaryButtonTextColor(secondaryButtonTextColor: Int): Builder {
            config.secondaryButtonTextColor = secondaryButtonTextColor
            return this
        }

        /** Sets the ARGB metadata text color. */
        fun metaTextColor(metaTextColor: Int): Builder {
            config.metaTextColor = metaTextColor
            return this
        }

        /**
         * Selects the report destination for the "Send crash report" action.
         *
         * <p>A destination must be explicitly supplied; a null value from Java
         * is rejected immediately with an [IllegalArgumentException]. Clearing a
         * destination restores the generic Android Sharesheet and does not clear
         * the handler or custom sender. Selecting or clearing a destination never
         * enables the report button.</p>
         */
        fun reportTo(destination: ReportDestination?): Builder {
            if (destination == null) {
                throw IllegalArgumentException("reportTo(destination) requires a non-null ReportDestination. Use clearReportDestination() to remove a selected destination.")
            }
            config.reportDestination = destination
            return this
        }

        /** Configures the GitHub owner and repository target. */
        fun githubIssueTarget(owner: String?, repository: String?): Builder {
            config.githubOwner = owner
            config.githubRepository = repository
            return this
        }

        /** Configures the GitLab project path and instance base URL. */
        fun gitLabIssueTarget(projectPath: String?, baseUrl: String?): Builder {
            config.gitLabProjectPath = projectPath
            config.gitLabBaseUrl = baseUrl
            return this
        }

        /** Clears the selected destination, restoring the Sharesheet default. */
        fun clearReportDestination(): Builder {
            config.reportDestination = null
            return this
        }

        /**
         * Sets the developer-defined sender used for the CUSTOM destination.
         * Must be a top-level or static serializable class.
         */
        fun reportSender(sender: ReportSender?): Builder {
            if (sender != null) {
                if (sender.javaClass.enclosingClass != null
                        && !Modifier.isStatic(sender.javaClass.modifiers)) {
                    throw IllegalArgumentException("The report sender cannot be an anonymous or non-static inner class because it must be serialized. Use a top-level class or static inner class.")
                }
                checkListenerFields("report sender", sender)
            }
            config.reportSender = sender
            return this
        }

        /** Clears the custom sender independently of destination and handler. */
        fun clearReportSender(): Builder {
            config.reportSender = null
            return this
        }

        /**
         * Sets the optional report-action handler class. The class must be
         * public with a public no-argument constructor so it can be constructed
         * in the `:xtcrash_error` process. Clearing it is independent of the
         * destination and custom sender.
         */
        fun reportActionHandler(handlerClass: Class<out ReportActionHandler>?): Builder {
            if (handlerClass != null) {
                validateHandlerClass(handlerClass)
            }
            config.reportActionHandlerClass = handlerClass
            return this
        }

        /** Clears the report-action handler independently. */
        fun clearReportActionHandler(): Builder {
            config.reportActionHandlerClass = null
            return this
        }

        /** Sets the deadline for handler/sender construction and invocation. */
        fun reportHandlerTimeout(timeoutMs: Int): Builder {
            config.reportHandlerTimeoutMs = timeoutMs
            return this
        }

        /**
         * Overrides the encoded-URL byte budget for GitHub/GitLab prefills.
         * The value is clamped to a safe minimum; it cannot bypass known limits.
         */
        fun reportUrlBudgetBytes(budgetBytes: Int?): Builder {
            config.reportUrlBudgetBytes = budgetBytes
            return this
        }

        /** Returns this builder's configuration without applying it. */
        fun get(): CrashConfig {
            return config
        }

        /** Applies this builder's configuration to XtCrash. */
        fun apply() {
            CrashActivity.setConfig(config)
        }
    }
}
