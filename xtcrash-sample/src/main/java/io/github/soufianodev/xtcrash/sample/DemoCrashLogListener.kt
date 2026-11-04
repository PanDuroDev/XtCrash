package io.github.soufianodev.xtcrash.sample

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log

import io.github.soufianodev.xtcrash.log.CrashLogListener

/**
 * Demo sink for the on-device cross-process check. Prints every forwarded
 * event with the delivering process's PID under its own tag, so logcat makes
 * it obvious which process produced each line: the main process or
 * `:xtcrash_error`.
 *
 * Configured in MainActivity's default testing config alongside
 * `loggingEnabled(true)`, so both sinks of the logging feature show up at
 * once: the library's own `XtCrash` lines and this listener's `XtCrashDemo`
 * lines. The listener is serialized with the config, so events arriving
 * inside the error-screen process carry that process's PID.
 */
class DemoCrashLogListener : CrashLogListener {

    override fun onCrashLog(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val origin = StringBuilder("pid=").append(Process.myPid())
        if (Build.VERSION.SDK_INT >= 28) {
            origin.append(" process=").append(Application.getProcessName())
        }
        val text = if (throwable != null) {
            message + " [" + throwable.javaClass.name + "]"
        } else {
            message
        }
        Log.println(priority, DEMO_TAG, origin.toString() + " > " + text)
    }

    companion object {
        const val DEMO_TAG = "XtCrashDemo"
    }
}
