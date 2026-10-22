package com.developer.crashx.crash.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity

import com.developer.crashx.CrashActivity
import com.developer.crashx.config.CrashConfig
import com.developer.crashx.crash.R

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentMode: TextView
    private lateinit var btnApplyDebugConfig: Button
    private lateinit var btnApplyProductionConfig: Button
    private lateinit var btnApplyCustomUiConfig: Button
    private lateinit var btnSimpleCrash: Button
    private lateinit var btnDelayedCrash: Button
    private lateinit var btnNullPointerCrash: Button
    private lateinit var btnArrayCrash: Button
    private lateinit var btnCrashWithActivityLog: Button
    private lateinit var btnDisableCrashX: Button
    private lateinit var btnEnableCrashX: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setClickListeners()
        applyDefaultTestingConfig()
    }

    private fun initializeViews() {
        tvCurrentMode = findViewById(R.id.tvCurrentMode)
        btnApplyDebugConfig = findViewById(R.id.btnApplyDebugConfig)
        btnApplyProductionConfig = findViewById(R.id.btnApplyProductionConfig)
        btnApplyCustomUiConfig = findViewById(R.id.btnApplyCustomUiConfig)
        btnSimpleCrash = findViewById(R.id.btnSimpleCrash)
        btnDelayedCrash = findViewById(R.id.btnDelayedCrash)
        btnNullPointerCrash = findViewById(R.id.btnNullPointerCrash)
        btnArrayCrash = findViewById(R.id.btnArrayCrash)
        btnCrashWithActivityLog = findViewById(R.id.btnCrashWithActivityLog)
        btnDisableCrashX = findViewById(R.id.btnDisableCrashX)
        btnEnableCrashX = findViewById(R.id.btnEnableCrashX)
    }

    private fun setClickListeners() {
        btnApplyDebugConfig.setOnClickListener { applyDebugConfig() }
        btnApplyProductionConfig.setOnClickListener { applyProductionConfig() }
        btnApplyCustomUiConfig.setOnClickListener { applyCustomUiConfig() }

        btnSimpleCrash.setOnClickListener {
            throw RuntimeException("CrashX v7 test crash: simple RuntimeException from MainActivity")
        }

        btnDelayedCrash.setOnClickListener {
            Toast.makeText(this, "App will crash in 3 seconds...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                throw RuntimeException("CrashX v7 delayed crash test after 3 seconds")
            }, 3000)
        }

        btnNullPointerCrash.setOnClickListener {
            val testValue: String? = null
            val length = testValue!!.length // Deliberate NPE crash test
            Toast.makeText(this, "Length: $length", Toast.LENGTH_SHORT).show()
        }

        btnArrayCrash.setOnClickListener {
            val numbers = intArrayOf(1, 2, 3)
            val value = numbers[10] // Deliberate out-of-bounds crash test
            Toast.makeText(this, "Value: $value", Toast.LENGTH_SHORT).show()
        }

        btnCrashWithActivityLog.setOnClickListener {
            CrashConfig.Builder.create()
                    .enabled(true)
                    .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                    .showErrorDetails(true)
                    .showRestartButton(true)
                    .showCloseButton(true)
                    .logErrorOnRestart(true)
                    .trackActivities(true)
                    .includeDeviceInfo(true)
                    .includeActivityLog(true)
                    .includeStackTrace(true)
                    .includeCrashId(true)
                    .showCrashId(true)
                    .showAppInfo(true)
                    .maxActivityLogEntries(50)
                    .maxStackTraceSize(128 * 1024)
                    .crashIdPrefix("SAMPLE")
                    .errorTitle("Crash with Activity Log")
                    .errorMessage("This crash was triggered with activity tracking enabled. Open details to see lifecycle logs.")
                    .restartButtonText("Restart app")
                    .closeButtonText("Close app")
                    .detailsButtonText("View crash details")
                    .showReportButton(true)
                    .reportButtonText("Send report")
                    .supportEmail("support@example.com")
                    .reportSubject("CrashX sample crash report")
                    .additionalReportInfo("Screen: MainActivity\nMode: Activity log test")
                    .restartActivity(MainActivity::class.java)
                    .eventListener(CrashXTestEventListener())
                    .apply()

            Toast.makeText(this, "Activity log enabled. Crashing now...", Toast.LENGTH_SHORT).show()
            throw IllegalStateException("CrashX v7 test crash with activity lifecycle tracking enabled")
        }

        btnDisableCrashX.setOnClickListener {
            CrashConfig.Builder.create()
                    .enabled(false)
                    .apply()
            tvCurrentMode.text = "Current mode: CrashX disabled"
            Toast.makeText(this, "CrashX disabled. Next crash will use normal Android crash behavior.", Toast.LENGTH_LONG).show()
        }

        btnEnableCrashX.setOnClickListener {
            applyDefaultTestingConfig()
            Toast.makeText(this, "CrashX enabled again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyDefaultTestingConfig() {
        CrashConfig.Builder.create()
                .enabled(true)
                .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .showErrorDetails(true)
                .showRestartButton(true)
                .showCloseButton(true)
                .logErrorOnRestart(true)
                .trackActivities(true)
                .includeDeviceInfo(true)
                .includeActivityLog(true)
                .includeStackTrace(true)
                .includeCrashId(true)
                .includeBuildDate(true)
                .showCrashId(true)
                .showAppInfo(true)
                .maxActivityLogEntries(50)
                .maxStackTraceSize(128 * 1024)
                .crashIdPrefix("CRASHX")
                .errorTitle("Oops! The app crashed")
                .errorMessage("Something unexpected happened. Please restart the app or send a crash report to help us fix it.")
                .restartButtonText("Restart app")
                .closeButtonText("Close app")
                .detailsButtonText("View technical details")
                .copyButtonText("Copy details")
                .showReportButton(true)
                .reportButtonText("Send crash report")
                .supportEmail("support@example.com")
                .reportSubject("CrashX sample crash report")
                .reportChooserTitle("Share crash report")
                .additionalReportInfo("Environment: Sample app\nBuild type: Testing")
                .backgroundColor(0xFFB91C1C.toInt())
                .cardBackgroundColor(0xFFFFFFFF.toInt())
                .primaryButtonColor(0xFFB91C1C.toInt())
                .secondaryButtonColor(0xFFF3F4F6.toInt())
                .titleTextColor(0xFF111827.toInt())
                .messageTextColor(0xFF4B5563.toInt())
                .buttonTextColor(0xFFFFFFFF.toInt())
                .secondaryButtonTextColor(0xFF111827.toInt())
                .metaTextColor(0xFF6B7280.toInt())
                .restartActivity(MainActivity::class.java)
                .eventListener(CrashXTestEventListener())
                .apply()

        tvCurrentMode.text = "Current mode: Default v7 testing config"
    }

    private fun applyDebugConfig() {
        CrashConfig.Builder.create()
                .enabled(true)
                .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .showErrorDetails(true)
                .showRestartButton(true)
                .showCloseButton(true)
                .logErrorOnRestart(true)
                .trackActivities(true)
                .includeDeviceInfo(true)
                .includeActivityLog(true)
                .includeStackTrace(true)
                .showReportButton(true)
                .showCrashId(true)
                .showAppInfo(true)
                .crashIdPrefix("DEBUG")
                .errorTitle("Debug Crash")
                .errorMessage("CrashX debug mode is enabled. Error details, device information, and activity logs are visible.")
                .restartButtonText("Restart debug app")
                .closeButtonText("Close")
                .detailsButtonText("Open stack trace")
                .reportButtonText("Share debug report")
                .supportEmail("developer@example.com")
                .backgroundColor(0xFF1F2937.toInt())
                .cardBackgroundColor(0xFFFFFFFF.toInt())
                .primaryButtonColor(0xFF2563EB.toInt())
                .secondaryButtonColor(0xFFE5E7EB.toInt())
                .titleTextColor(0xFF111827.toInt())
                .messageTextColor(0xFF4B5563.toInt())
                .buttonTextColor(0xFFFFFFFF.toInt())
                .secondaryButtonTextColor(0xFF111827.toInt())
                .restartActivity(MainActivity::class.java)
                .eventListener(CrashXTestEventListener())
                .apply()

        tvCurrentMode.text = "Current mode: Debug config"
        Toast.makeText(this, "Debug CrashX config applied.", Toast.LENGTH_SHORT).show()
    }

    private fun applyProductionConfig() {
        CrashConfig.Builder.create()
                .enabled(true)
                .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .showErrorDetails(false)
                .showRestartButton(true)
                .showCloseButton(true)
                .logErrorOnRestart(false)
                .trackActivities(false)
                .includeDeviceInfo(true)
                .includeActivityLog(false)
                .includeStackTrace(true)
                .showReportButton(true)
                .showCrashId(true)
                .showAppInfo(false)
                .crashIdPrefix("PROD")
                .maxActivityLogEntries(20)
                .maxStackTraceSize(64 * 1024)
                .errorTitle("Something went wrong")
                .errorMessage("The app ran into an unexpected problem. Please restart the app or report the issue.")
                .restartButtonText("Restart app")
                .closeButtonText("Close app")
                .detailsButtonText("Details")
                .reportButtonText("Report issue")
                .supportEmail("support@example.com")
                .backgroundColor(0xFF991B1B.toInt())
                .cardBackgroundColor(0xFFFFFFFF.toInt())
                .primaryButtonColor(0xFF991B1B.toInt())
                .secondaryButtonColor(0xFFF3F4F6.toInt())
                .titleTextColor(0xFF111827.toInt())
                .messageTextColor(0xFF4B5563.toInt())
                .buttonTextColor(0xFFFFFFFF.toInt())
                .secondaryButtonTextColor(0xFF111827.toInt())
                .restartActivity(MainActivity::class.java)
                .eventListener(CrashXTestEventListener())
                .apply()

        tvCurrentMode.text = "Current mode: Production config"
        Toast.makeText(this, "Production CrashX config applied.", Toast.LENGTH_SHORT).show()
    }

    private fun applyCustomUiConfig() {
        CrashConfig.Builder.create()
                .enabled(true)
                .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .showErrorDetails(true)
                .showRestartButton(true)
                .showCloseButton(true)
                .logErrorOnRestart(true)
                .trackActivities(true)
                .includeDeviceInfo(true)
                .includeActivityLog(true)
                .includeStackTrace(true)
                .showReportButton(true)
                .showCrashId(true)
                .showAppInfo(true)
                .crashIdPrefix("PURPLE")
                .maxActivityLogEntries(50)
                .maxStackTraceSize(128 * 1024)
                .errorTitle("CrashX Protected App")
                .errorMessage("No worries. CrashX caught the crash and gave your app a clean recovery screen.")
                .restartButtonText("Launch again")
                .closeButtonText("Exit app")
                .detailsButtonText("Technical info")
                .reportButtonText("Email report")
                .supportEmail("support@example.com")
                .backgroundColor(0xFF0F172A.toInt())
                .cardBackgroundColor(0xFFFFFFFF.toInt())
                .primaryButtonColor(0xFF7C3AED.toInt())
                .secondaryButtonColor(0xFFF1F5F9.toInt())
                .titleTextColor(0xFF111827.toInt())
                .messageTextColor(0xFF475569.toInt())
                .buttonTextColor(0xFFFFFFFF.toInt())
                .secondaryButtonTextColor(0xFF111827.toInt())
                .metaTextColor(0xFF64748B.toInt())
                .restartActivity(MainActivity::class.java)
                .eventListener(CrashXTestEventListener())
                .apply()

        tvCurrentMode.text = "Current mode: Custom UI config"
        Toast.makeText(this, "Custom UI CrashX config applied.", Toast.LENGTH_SHORT).show()
    }

    class CrashXTestEventListener : CrashActivity.EventListener {
        override fun onLaunchErrorActivity() {
            Log.d("CrashXSample", "CrashX error activity launched")
        }

        override fun onRestartAppFromErrorActivity() {
            Log.d("CrashXSample", "App restarted from CrashX error activity")
        }

        override fun onCloseAppFromErrorActivity() {
            Log.d("CrashXSample", "App closed from CrashX error activity")
        }
    }
}
