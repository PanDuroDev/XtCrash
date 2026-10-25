package io.github.soufianodev.xtcrash.compose

import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.ReportSnapshot

/**
 * Drop-in Jetpack Compose error screen for XtCrash.
 *
 * <p>Hosts [XtCrashErrorScreen] with the crash payload passed through the intent, so every
 * visual and behavioral option still comes from the [io.github.soufianodev.xtcrash.config.CrashConfig].
 * Point at it from any project with
 * `CrashConfig.Builder.create().errorActivity(ComposeErrorActivity::class.java).apply()` —
 * the manifest declaration (dedicated error process) ships with this module. For a fully
 * custom design, write your own crash activity instead (see README). Originally derived from
 * CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 *
 * <p>Like the library's XML screen, a repeat `singleTask` launch routes through
 * [onNewIntent]; the observable payload is replaced so the screen resets onto the new
 * crash's dispatcher instead of keeping the previous crash's state.</p>
 */
class ComposeErrorActivity : ComponentActivity() {

    private data class CrashPayload(
        val config: CrashConfig,
        val report: ReportSnapshot,
        val crashId: String,
    )

    private var payload by mutableStateOf<CrashPayload?>(null)

    private fun readPayload() {
        payload = CrashPayload(
                config = CrashActivity.getConfigFromIntent(intent),
                report = CrashActivity.getReportFromIntent(this, intent),
                crashId = CrashActivity.getCrashIdFromIntent(intent),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPayload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readPayload()

        setContent {
            payload?.let { crash ->
                XtCrashErrorScreen(
                        config = crash.config,
                        errorDetails = crash.report.text,
                        crashId = crash.crashId,
                        sections = crash.report.sections,
                )
            }
        }
    }
}
