package io.github.soufianodev.xtcrash

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log

import androidx.annotation.RestrictTo

import io.github.soufianodev.xtcrash.activity.DefaultErrorActivity
import io.github.soufianodev.xtcrash.config.CrashConfig

import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Deque
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

/**
 * XtCrash runtime crash handler.
 *
 * <p>Originally derived from CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 * <p>Modernized and maintained as CrashX by TutorialsAndroid.</p>
 */
object CrashActivity {

    const val VERSION = "1.0.0"

    private const val TAG = "XtCrash"

    private const val EXTRA_CONFIG = "io.github.soufianodev.xtcrash.EXTRA_CONFIG"
    private const val EXTRA_STACK_TRACE = "io.github.soufianodev.xtcrash.EXTRA_STACK_TRACE"
    private const val EXTRA_ACTIVITY_LOG = "io.github.soufianodev.xtcrash.EXTRA_ACTIVITY_LOG"
    private const val EXTRA_THREAD_NAME = "io.github.soufianodev.xtcrash.EXTRA_THREAD_NAME"
    private const val EXTRA_CRASH_ID = "io.github.soufianodev.xtcrash.EXTRA_CRASH_ID"
    private const val EXTRA_THROWABLE_CLASS = "io.github.soufianodev.xtcrash.EXTRA_THROWABLE_CLASS"
    private const val EXTRA_THROWABLE_MESSAGE = "io.github.soufianodev.xtcrash.EXTRA_THROWABLE_MESSAGE"
    private const val EXTRA_CRASH_DATE = "io.github.soufianodev.xtcrash.EXTRA_CRASH_DATE"

    private const val INTENT_ACTION_ERROR_ACTIVITY = "io.github.soufianodev.xtcrash.ERROR"
    private const val INTENT_ACTION_RESTART_ACTIVITY = "io.github.soufianodev.xtcrash.RESTART"
    private const val DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os"

    private const val SHARED_PREFERENCES_FILE = "xtcrash"
    private const val SHARED_PREFERENCES_FIELD_TIMESTAMP = "last_crash_timestamp"

    private var application: Application? = null
    private var config = CrashConfig()
    private val activityLog: Deque<String> = ArrayDeque()
    private var lastActivityCreated = WeakReference<Activity?>(null)
    private var isInBackground = true
    private var lifecycleCallbacksRegistered = false

    @JvmStatic
    @Synchronized
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun install(context: Context?) {
        try {
            if (context == null) {
                Log.e(TAG, "Install failed: context is null.")
                return
            }

            val app = context.applicationContext as Application
            application = app
            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

            if (oldHandler is XtCrashUncaughtExceptionHandler) {
                Log.i(TAG, "XtCrash is already installed.")
                return
            }

            if (oldHandler != null && !oldHandler.javaClass.name.startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
                Log.w(TAG, "Another UncaughtExceptionHandler is already installed. XtCrash will preserve it and call it when needed.")
            }

            Thread.setDefaultUncaughtExceptionHandler(XtCrashUncaughtExceptionHandler(oldHandler))

            if (!lifecycleCallbacksRegistered) {
                app.registerActivityLifecycleCallbacks(createLifecycleCallbacks())
                lifecycleCallbacksRegistered = true
            }

            Log.i(TAG, "XtCrash $VERSION has been installed.")
        } catch (t: Throwable) {
            Log.e(TAG, "An unknown error occurred while installing XtCrash.", t)
        }
    }

    private class XtCrashUncaughtExceptionHandler(
        private val oldHandler: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            handleUncaughtException(oldHandler, thread, error)
        }
    }

    private fun handleUncaughtException(oldHandler: Thread.UncaughtExceptionHandler?,
                                        thread: Thread,
                                        throwable: Throwable) {
        if (!config.isEnabled) {
            callOldHandlerOrKill(oldHandler, thread, throwable)
            return
        }

        Log.e(TAG, "App crashed. XtCrash is preparing the error screen.", throwable)

        try {
            val app = application
            if (app == null) {
                callOldHandlerOrKill(oldHandler, thread, throwable)
                return
            }

            if (hasCrashedInTheLastSeconds(app)) {
                Log.e(TAG, "Recent crash detected. Skipping XtCrash UI to avoid a restart loop.", throwable)
                callOldHandlerOrKill(oldHandler, thread, throwable)
                return
            }

            setLastCrashTimestamp(app, System.currentTimeMillis())

            var errorActivityClass: Class<out Activity>? = config.errorActivityClass
            if (errorActivityClass == null) {
                errorActivityClass = guessErrorActivityClass(app)
            }

            if (isStackTraceLikelyConflictive(throwable, errorActivityClass)) {
                Log.e(TAG, "The Application class or error activity crashed. XtCrash UI will not be launched.", throwable)
                callOldHandlerOrKill(oldHandler, thread, throwable)
                return
            }

            if (config.backgroundMode == CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM || !isInBackground) {
                launchErrorActivity(thread, throwable, errorActivityClass)
            } else if (config.backgroundMode == CrashConfig.BACKGROUND_MODE_CRASH) {
                callOldHandlerOrKill(oldHandler, thread, throwable)
                return
            }

            finishLastActivity()
            killCurrentProcess()
        } catch (xtCrashFailure: Throwable) {
            Log.e(TAG, "XtCrash failed while handling the crash.", xtCrashFailure)
            callOldHandlerOrKill(oldHandler, thread, throwable)
        }
    }

    private fun launchErrorActivity(thread: Thread,
                                    throwable: Throwable,
                                    errorActivityClass: Class<out Activity>) {
        val app = application!!
        val intent = Intent(app, errorActivityClass)

        val crashId = createCrashId()
        val crashDate = createDateFormat().format(Date())

        intent.putExtra(EXTRA_THREAD_NAME, thread.name)
        intent.putExtra(EXTRA_CRASH_ID, crashId)
        intent.putExtra(EXTRA_THROWABLE_CLASS, throwable.javaClass.name)
        intent.putExtra(EXTRA_THROWABLE_MESSAGE, throwable.message)
        intent.putExtra(EXTRA_CRASH_DATE, crashDate)

        if (config.isIncludeStackTrace) {
            intent.putExtra(EXTRA_STACK_TRACE, throwableToString(throwable, config.maxStackTraceSize))
        }

        if (config.isTrackActivities && config.isIncludeActivityLog) {
            intent.putExtra(EXTRA_ACTIVITY_LOG, snapshotActivityLog())
        }

        if (config.isShowRestartButton && config.restartActivityClass == null) {
            config.restartActivityClass = guessRestartActivityClass(app)
        }

        intent.putExtra(EXTRA_CONFIG, config)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        notifyLaunchErrorActivity()

        app.startActivity(intent)
    }

    private fun createLifecycleCallbacks(): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            var currentlyStartedActivities = 0
            val dateFormat: DateFormat = createDateFormat()

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!isXtCrashErrorActivity(activity)) {
                    lastActivityCreated = WeakReference(activity)
                }
                appendActivityLog(dateFormat.format(Date()) + ": " + activity.javaClass.simpleName + " created\n")
            }

            override fun onActivityStarted(activity: Activity) {
                currentlyStartedActivities++
                isInBackground = false
            }

            override fun onActivityResumed(activity: Activity) {
                appendActivityLog(dateFormat.format(Date()) + ": " + activity.javaClass.simpleName + " resumed\n")
            }

            override fun onActivityPaused(activity: Activity) {
                appendActivityLog(dateFormat.format(Date()) + ": " + activity.javaClass.simpleName + " paused\n")
            }

            override fun onActivityStopped(activity: Activity) {
                currentlyStartedActivities = (currentlyStartedActivities - 1).coerceAtLeast(0)
                isInBackground = currentlyStartedActivities == 0
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // No-op.
            }

            override fun onActivityDestroyed(activity: Activity) {
                appendActivityLog(dateFormat.format(Date()) + ": " + activity.javaClass.simpleName + " destroyed\n")
            }
        }
    }

    private fun isXtCrashErrorActivity(activity: Activity): Boolean {
        val configuredErrorActivity = config.errorActivityClass
        return activity.javaClass == DefaultErrorActivity::class.java
                || (configuredErrorActivity != null && activity.javaClass == configuredErrorActivity)
    }

    private fun appendActivityLog(line: String) {
        if (!config.isTrackActivities || !config.isIncludeActivityLog) {
            return
        }
        while (activityLog.size >= config.maxActivityLogEntries) {
            activityLog.pollFirst()
        }
        activityLog.offerLast(line)
    }

    private fun snapshotActivityLog(): String {
        return activityLog.joinToString("")
    }

    private fun throwableToString(throwable: Throwable, maxSize: Int): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        var stackTraceString = stringWriter.toString()

        if (stackTraceString.length > maxSize) {
            val disclaimer = "\n[stack trace trimmed by XtCrash]"
            val safeLength = (maxSize - disclaimer.length).coerceAtLeast(0)
            stackTraceString = stackTraceString.substring(0, safeLength) + disclaimer
        }
        return stackTraceString
    }

    @JvmStatic
    fun getStackTraceFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_STACK_TRACE)
    }

    @JvmStatic
    fun getActivityLogFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_ACTIVITY_LOG)
    }

    @JvmStatic
    fun getThreadNameFromIntent(intent: Intent): String {
        return intent.getStringExtra(EXTRA_THREAD_NAME) ?: "unknown"
    }

    @JvmStatic
    fun getCrashIdFromIntent(intent: Intent): String {
        return intent.getStringExtra(EXTRA_CRASH_ID) ?: "unknown"
    }

    @JvmStatic
    fun getThrowableClassFromIntent(intent: Intent): String {
        return intent.getStringExtra(EXTRA_THROWABLE_CLASS) ?: "unknown"
    }

    @JvmStatic
    fun getThrowableMessageFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_THROWABLE_MESSAGE)
    }

    @JvmStatic
    fun getCrashDateFromIntent(intent: Intent): String {
        return intent.getStringExtra(EXTRA_CRASH_DATE) ?: createDateFormat().format(Date())
    }

    @JvmStatic
    fun getConfigFromIntent(intent: Intent): CrashConfig {
        val fromIntent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getSerializableExtra(EXTRA_CONFIG, CrashConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_CONFIG) as? CrashConfig
        }
        val crashConfig = fromIntent ?: getConfig()

        if (crashConfig.isLogErrorOnRestart) {
            val stackTrace = getStackTraceFromIntent(intent)
            if (stackTrace != null) {
                Log.e(TAG, "The previous app process crashed. Stack trace:\n$stackTrace")
            }
        }

        return crashConfig
    }

    @JvmStatic
    fun getAllErrorDetailsFromIntent(context: Context, intent: Intent): String {
        val crashConfig = getConfigFromIntent(intent)
        val errorDetails = StringBuilder()

        if (crashConfig.isIncludeCrashId) {
            errorDetails.append("Crash ID: ").append(getCrashIdFromIntent(intent)).append("\n")
        }

        errorDetails.append("Crash date: ").append(getCrashDateFromIntent(intent)).append("\n")
        errorDetails.append("Crash thread: ").append(getThreadNameFromIntent(intent)).append("\n")
        errorDetails.append("Exception: ").append(getThrowableClassFromIntent(intent)).append("\n")

        val throwableMessage = getThrowableMessageFromIntent(intent)
        if (!throwableMessage.isNullOrBlank()) {
            errorDetails.append("Message: ").append(throwableMessage).append("\n")
        }

        errorDetails.append("\n")

        if (crashConfig.isIncludeDeviceInfo) {
            errorDetails.append("Package: ").append(context.packageName).append("\n")
            errorDetails.append("App version: ").append(getVersionName(context)).append(" (").append(getVersionCode(context)).append(")\n")
            errorDetails.append("XtCrash version: ").append(VERSION).append("\n")
            if (crashConfig.isIncludeBuildDate) {
                val buildDate = getBuildDateAsString(context, createDateFormat())
                if (buildDate != null) {
                    errorDetails.append("Build date: ").append(buildDate).append("\n")
                }
            }
            errorDetails.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            errorDetails.append("Device: ").append(getDeviceModelName()).append("\n")
            errorDetails.append("Brand: ").append(Build.BRAND).append("\n")
            errorDetails.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
            errorDetails.append("\n")
        }

        val additionalInfo = crashConfig.additionalReportInfo
        if (additionalInfo != null) {
            errorDetails.append("Additional information:\n")
            errorDetails.append(additionalInfo).append("\n\n")
        }

        if (crashConfig.isIncludeStackTrace) {
            errorDetails.append("Stack trace:\n")
            val stackTrace = getStackTraceFromIntent(intent)
            errorDetails.append(stackTrace ?: "No stack trace available.")
            errorDetails.append("\n")
        }

        val activityLogText = getActivityLogFromIntent(intent)
        if (crashConfig.isIncludeActivityLog && !activityLogText.isNullOrBlank()) {
            errorDetails.append("\nActivity log:\n")
            errorDetails.append(activityLogText)
        }

        return errorDetails.toString()
    }

    /**
     * Backward-compatible overload. Prefer [getAllErrorDetailsFromIntent].
     */
    @JvmStatic
    @Deprecated("")
    fun getAllErrorDetailsFromIntent(intent: Intent): String {
        val errorDetails = StringBuilder()
        errorDetails.append("Crash ID: ").append(getCrashIdFromIntent(intent)).append("\n")
        errorDetails.append("Crash date: ").append(getCrashDateFromIntent(intent)).append("\n")
        errorDetails.append("Crash thread: ").append(getThreadNameFromIntent(intent)).append("\n")
        errorDetails.append("Exception: ").append(getThrowableClassFromIntent(intent)).append("\n\n")
        errorDetails.append("Stack trace:\n")
        val stackTrace = getStackTraceFromIntent(intent)
        errorDetails.append(stackTrace ?: "No stack trace available.")
        return errorDetails.toString()
    }

    private fun restartApplicationWithIntentInternal(activity: Activity,
                                                     intent: Intent,
                                                     crashConfig: CrashConfig) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (intent.component != null) {
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
        }

        notifyRestartAppFromErrorActivity(crashConfig)

        activity.finish()
        activity.startActivity(intent)
        killCurrentProcess()
    }

    @JvmStatic
    fun restartApplication(activity: Activity, crashConfig: CrashConfig) {
        var restartActivityClass: Class<out Activity>? = crashConfig.restartActivityClass
        if (restartActivityClass == null) {
            restartActivityClass = guessRestartActivityClass(activity.applicationContext)
        }

        if (restartActivityClass == null) {
            closeApplication(activity, crashConfig)
            return
        }

        val intent = Intent(activity, restartActivityClass)
        restartApplicationWithIntentInternal(activity, intent, crashConfig)
    }

    @JvmStatic
    fun restartApplicationWithIntent(activity: Activity,
                                     intent: Intent,
                                     crashConfig: CrashConfig) {
        restartApplicationWithIntentInternal(activity, intent, crashConfig)
    }

    @JvmStatic
    fun closeApplication(activity: Activity, crashConfig: CrashConfig) {
        notifyCloseAppFromErrorActivity(crashConfig)
        activity.finish()
        killCurrentProcess()
    }

    @JvmStatic
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun getConfig(): CrashConfig {
        return config
    }

    @JvmStatic
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun setConfig(crashConfig: CrashConfig) {
        config = crashConfig
        val app = application
        val errorActivityClass = crashConfig.errorActivityClass
        if (app != null && errorActivityClass != null && errorActivityClass != DefaultErrorActivity::class.java) {
            validateCustomErrorActivity(app, errorActivityClass)
        }
    }

    /**
     * Flags custom error activities that cannot work: absent from the manifest, or running
     * in the main process (the crashing process is killed right after the screen launches).
     * Warnings only — throwing here during provider init would break app startup.
     */
    private fun validateCustomErrorActivity(app: Application, errorActivityClass: Class<out Activity>) {
        val mainProcessName = app.applicationInfo.processName ?: app.packageName
        try {
            val info = app.packageManager.getActivityInfo(ComponentName(app, errorActivityClass), 0)
            if (info.processName == mainProcessName) {
                Log.e(TAG, "XtCrash config warning: error activity " + errorActivityClass.name
                        + " runs in the main process. Add its own android:process=\"...\" attribute to the manifest entry; "
                        + "the crashing process is killed immediately after the screen launches, so an in-process screen dies before it is shown.")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "XtCrash config warning: error activity " + errorActivityClass.name
                    + " is not declared in the merged manifest. Register an <activity> entry for it with its own android:process so the screen can launch.")
        }
    }

    private fun isStackTraceLikelyConflictive(throwable: Throwable,
                                              activityClass: Class<out Activity>): Boolean {
        var currentThrowable: Throwable? = throwable
        while (currentThrowable != null) {
            for (element in currentThrowable.stackTrace) {
                val className = element.className
                if ((className == "android.app.ActivityThread"
                            && element.methodName == "handleBindApplication")
                    || className == activityClass.name
                ) {
                    return true
                }
            }
            currentThrowable = currentThrowable.cause
        }
        return false
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
        if (model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))) {
            return capitalize(model)
        }
        return capitalize(manufacturer) + " " + model
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

    private fun guessRestartActivityClass(context: Context): Class<out Activity>? {
        var resolvedActivityClass: Class<out Activity>? = getRestartActivityClassWithIntentFilter(context)
        if (resolvedActivityClass == null) {
            resolvedActivityClass = getLauncherActivity(context)
        }
        return resolvedActivityClass
    }

    private fun getRestartActivityClassWithIntentFilter(context: Context): Class<out Activity>? {
        val searchedIntent = Intent().setAction(INTENT_ACTION_RESTART_ACTIVITY).setPackage(context.packageName)
        val resolveInfos = context.packageManager.queryIntentActivities(
                searchedIntent,
                PackageManager.GET_RESOLVED_FILTER
        )

        val resolveInfo = resolveInfos.firstOrNull()
        if (resolveInfo != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(resolveInfo.activityInfo.name) as Class<out Activity>
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Failed to resolve restart activity class from intent filter.", e)
            }
        }
        return null
    }

    private fun getLauncherActivity(context: Context): Class<out Activity>? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = intent?.component
        if (intent != null && component != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(component.className) as Class<out Activity>
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Failed to resolve launcher activity class.", e)
            }
        }
        return null
    }

    private fun guessErrorActivityClass(context: Context): Class<out Activity> {
        return getErrorActivityClassWithIntentFilter(context) ?: DefaultErrorActivity::class.java
    }

    private fun getErrorActivityClassWithIntentFilter(context: Context): Class<out Activity>? {
        val searchedIntent = Intent().setAction(INTENT_ACTION_ERROR_ACTIVITY).setPackage(context.packageName)
        val resolveInfos = context.packageManager.queryIntentActivities(
                searchedIntent,
                PackageManager.GET_RESOLVED_FILTER
        )

        val resolveInfo = resolveInfos.firstOrNull()
        if (resolveInfo != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(resolveInfo.activityInfo.name) as Class<out Activity>
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Failed to resolve error activity class from intent filter.", e)
            }
        }
        return null
    }

    private fun finishLastActivity() {
        val lastActivity = lastActivityCreated.get()
        if (lastActivity != null) {
            lastActivity.finish()
            lastActivityCreated.clear()
        }
    }

    private fun callOldHandlerOrKill(oldHandler: Thread.UncaughtExceptionHandler?,
                                     thread: Thread,
                                     throwable: Throwable) {
        if (oldHandler != null) {
            oldHandler.uncaughtException(thread, throwable)
            return
        }
        killCurrentProcess()
    }

    private fun notifyLaunchErrorActivity() {
        try {
            config.eventListener?.onLaunchErrorActivity()
        } catch (listenerError: Throwable) {
            Log.e(TAG, "XtCrash event listener failed on launch.", listenerError)
        }
    }

    private fun notifyRestartAppFromErrorActivity(crashConfig: CrashConfig) {
        try {
            crashConfig.eventListener?.onRestartAppFromErrorActivity()
        } catch (listenerError: Throwable) {
            Log.e(TAG, "XtCrash event listener failed on restart.", listenerError)
        }
    }

    private fun notifyCloseAppFromErrorActivity(crashConfig: CrashConfig) {
        try {
            crashConfig.eventListener?.onCloseAppFromErrorActivity()
        } catch (listenerError: Throwable) {
            Log.e(TAG, "XtCrash event listener failed on close.", listenerError)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun setLastCrashTimestamp(context: Context, timestamp: Long) {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, timestamp)
                .commit()
    }

    private fun getLastCrashTimestamp(context: Context): Long {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
                .getLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, -1)
    }

    private fun hasCrashedInTheLastSeconds(context: Context): Boolean {
        val minTime = config.minTimeBetweenCrashesMs
        if (minTime <= 0) {
            return false
        }
        val lastTimestamp = getLastCrashTimestamp(context)
        val currentTimestamp = System.currentTimeMillis()
        return lastTimestamp <= currentTimestamp && currentTimestamp - lastTimestamp < minTime
    }

    private fun createCrashId(): String {
        val prefix = requireNotNull(config.crashIdPrefix)
        val shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 12).uppercase(Locale.US)
        return prefix + "-" + shortId
    }

    private fun createDateFormat(): DateFormat {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private fun killCurrentProcess() {
        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    interface EventListener : Serializable {
        fun onLaunchErrorActivity()

        fun onRestartAppFromErrorActivity()

        fun onCloseAppFromErrorActivity()
    }
}
