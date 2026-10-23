package io.github.soufianodev.xtcrash.log

import java.io.Serializable

/**
 * Optional sink that receives XtCrash's own diagnostic messages: the install
 * and crash-handling events that the library would otherwise route to the
 * Android logcat.
 *
 * The library does not decide where these end up - it forwards the raw event
 * and leaves the output to the developer's logging stack (a file logger, a
 * crash tracker, or anything else).
 *
 * Implementations must be [Serializable] and declared as a top-level class or
 * a static inner class, because the listener travels with the
 * [io.github.soufianodev.xtcrash.config.CrashConfig] into the error-screen
 * process.
 *
 * `priority` values follow the standard Android log priorities
 * [android.util.Log.INFO], [android.util.Log.WARN], and [android.util.Log.ERROR].
 *
 * @see io.github.soufianodev.xtcrash.config.CrashConfig.Builder.logListener
 */
interface CrashLogListener : Serializable {

    /**
     * Called for every diagnostic event the library emits.
     *
     * @param priority one of [android.util.Log.INFO], [android.util.Log.WARN], or [android.util.Log.ERROR]
     * @param tag the log tag used by the library (`XtCrash`)
     * @param message the human-readable message
     * @param throwable the attached throwable, or null when the event has none
     */
    fun onCrashLog(priority: Int, tag: String, message: String, throwable: Throwable?)
}
