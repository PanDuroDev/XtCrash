package io.github.soufianodev.xtcrash.compose

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import io.github.soufianodev.xtcrash.CrashActivity

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
 */
class ComposeErrorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = CrashActivity.getConfigFromIntent(intent)
        val errorDetails = CrashActivity.getAllErrorDetailsFromIntent(this, intent)

        setContent {
            XtCrashErrorScreen(
                    config = config,
                    errorDetails = errorDetails,
                    crashId = CrashActivity.getCrashIdFromIntent(intent),
            )
        }
    }
}
