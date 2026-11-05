package io.github.soufianodev.xtcrash.activity

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.R
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.PreparedReport
import io.github.soufianodev.xtcrash.report.ReportSnapshot
import io.github.soufianodev.xtcrash.report.internal.ReportDispatcher

/**
 * Default XtCrash error screen.
 *
 * <p>Originally derived from CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 * <p>Modernized and maintained as CrashX by TutorialsAndroid.</p>
 */
class DefaultErrorActivity : AppCompatActivity() {

    private lateinit var config: CrashConfig
    private var reportSnapshot: ReportSnapshot? = null
    private var detailsDialog: AlertDialog? = null
    private var dispatcher: ReportDispatcher? = null

    /** Privacy/fallback dialog plus the state that staged it, for callback guards. */
    private var reportDialog: AlertDialog? = null
    private var reportDialogState: ReportDispatcher.ReportState? = null

    @SuppressLint("PrivateResource")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        super.onCreate(savedInstanceState)

        val typedArray: TypedArray = obtainStyledAttributes(androidx.appcompat.R.styleable.AppCompatTheme)
        if (!typedArray.hasValue(androidx.appcompat.R.styleable.AppCompatTheme_windowActionBar)) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        }
        typedArray.recycle()

        config = CrashActivity.getConfigFromIntent(intent)

        setContentView(R.layout.crash_default_error_activity)

        dispatcher = createDispatcher()

        renderCrash()
    }

    /**
     * Builds a dispatcher for the current intent's crash from one report
     * snapshot, so display and dispatch share identical content. The listener
     * is attached here; every replacement path invalidates and detaches the
     * previous dispatcher first.
     */
    private fun createDispatcher(): ReportDispatcher {
        val snapshot = CrashActivity.getReportFromIntent(this, intent)
        reportSnapshot = snapshot
        val created = ReportDispatcher(
                context = this,
                config = config,
                canonicalReport = snapshot.text,
                crashId = CrashActivity.getCrashIdFromIntent(intent),
                subject = CrashActivity.getReportSubject(this, config, CrashActivity.getCrashIdFromIntent(intent)),
                sections = snapshot.sections,
        )
        created.setListener(object : ReportDispatcher.Listener {
            override fun onReportState(state: ReportDispatcher.ReportState) {
                renderReportState(state)
            }
        })
        return created
    }

    /**
     * Re-delivers a crash into a live instance. With `launchMode="singleTask"`
     * the platform routes a repeat launch of this screen through
     * [onNewIntent] instead of a new instance; without this hook the old
     * screen would keep showing the previous crash and the restart-time
     * trace would not be logged again. The previous crash's dispatcher,
     * listener, and open report dialogs are discarded first so no stale
     * report, URL, or confirmation can survive the replacement.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reportDialog?.dismiss()
        reportDialog = null
        reportDialogState = null
        dispatcher?.invalidate()
        dispatcher?.setListener(null)
        config = CrashActivity.getConfigFromIntent(intent)
        dispatcher = createDispatcher()
        renderCrash()
    }

    private fun renderCrash() {
        reportDialog?.dismiss()
        reportDialog = null
        reportDialogState = null
        detailsDialog?.dismiss()
        detailsDialog = null
        setupWindow()
        setupTextContent()
        setupMetaContent()
        setupImage()
        setupButtons()
    }

    override fun onDestroy() {
        dispatcher?.invalidate()
        dispatcher?.setListener(null)
        reportDialog?.dismiss()
        reportDialog = null
        reportDialogState = null
        detailsDialog?.dismiss()
        super.onDestroy()
    }

    private fun setupWindow() {
        val root: View? = findViewById(R.id.crash_error_activity_root)
        val card: CardView? = findViewById(R.id.crash_error_activity_card)

        root?.setBackgroundColor(config.backgroundColor)

        card?.setCardBackgroundColor(config.cardBackgroundColor)

        val window: Window? = window
        if (window != null) {
            window.statusBarColor = config.backgroundColor
            window.navigationBarColor = config.backgroundColor
        }
    }

    private fun setupTextContent() {
        val titleView: TextView? = findViewById(R.id.crash_error_activity_title)
        val messageView: TextView? = findViewById(R.id.crash_error_activity_message)

        if (titleView != null) {
            titleView.text = requireNotNull(config.errorTitle)
            titleView.setTextColor(config.titleTextColor)
        }

        if (messageView != null) {
            messageView.text = requireNotNull(config.errorMessage)
            messageView.setTextColor(config.messageTextColor)
        }
    }

    private fun setupMetaContent() {
        val crashIdView: TextView? = findViewById(R.id.crash_error_activity_crash_id)
        val appInfoView: TextView? = findViewById(R.id.crash_error_activity_app_info)

        if (crashIdView != null) {
            crashIdView.setTextColor(config.metaTextColor)
            if (config.isShowCrashId) {
                crashIdView.visibility = View.VISIBLE
                crashIdView.text = getString(R.string.xtcrash_crash_id_format, CrashActivity.getCrashIdFromIntent(intent))
            } else {
                crashIdView.visibility = View.GONE
            }
        }

        if (appInfoView != null) {
            appInfoView.setTextColor(config.metaTextColor)
            if (config.isShowAppInfo) {
                appInfoView.visibility = View.VISIBLE
                appInfoView.text = getString(R.string.xtcrash_version_format, CrashActivity.VERSION)
            } else {
                appInfoView.visibility = View.GONE
            }
        }
    }

    private fun setupImage() {
        val drawableId = config.errorDrawable ?: R.drawable.crash_ic_bug_report
        val errorImageView: ImageView? = findViewById(R.id.crash_error_activity_image)

        if (errorImageView != null) {
            errorImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, drawableId, theme))
        }
    }

    private fun setupButtons() {
        val restartButton: Button? = findViewById(R.id.crash_error_activity_restart_button)
        val closeButton: Button? = findViewById(R.id.crash_error_activity_close_button)
        val detailsButton: Button? = findViewById(R.id.crash_error_activity_more_info_button)
        val reportButton: Button? = findViewById(R.id.crash_error_activity_report_button)

        setupRestartButton(restartButton)
        setupCloseButton(closeButton)
        setupDetailsButton(detailsButton)
        setupReportButton(reportButton)
    }

    private fun setupRestartButton(restartButton: Button?) {
        if (restartButton == null) {
            return
        }

        tintPrimaryButton(restartButton)

        if (config.isShowRestartButton && config.restartActivityClass != null) {
            restartButton.visibility = View.VISIBLE
            restartButton.text = config.restartButtonText
                    ?: getString(R.string.customactivityoncrash_error_activity_restart_app)
            restartButton.setOnClickListener { CrashActivity.restartApplication(this@DefaultErrorActivity, config) }
        } else {
            restartButton.visibility = View.GONE
        }
    }

    private fun setupCloseButton(closeButton: Button?) {
        if (closeButton == null) {
            return
        }

        tintSecondaryButton(closeButton)

        if (config.isShowCloseButton) {
            closeButton.visibility = View.VISIBLE
            closeButton.text = config.closeButtonText
                    ?: getString(R.string.customactivityoncrash_error_activity_close_app)
            closeButton.setOnClickListener { CrashActivity.closeApplication(this@DefaultErrorActivity, config) }
        } else {
            closeButton.visibility = View.GONE
        }
    }

    private fun setupDetailsButton(detailsButton: Button?) {
        if (detailsButton == null) {
            return
        }

        tintSecondaryButton(detailsButton)

        if (config.isShowErrorDetails) {
            detailsButton.visibility = View.VISIBLE
            detailsButton.text = config.detailsButtonText
                    ?: getString(R.string.customactivityoncrash_error_activity_error_details)
            detailsButton.setOnClickListener { showErrorDetailsDialog() }
        } else {
            detailsButton.visibility = View.GONE
        }
    }

    private fun setupReportButton(reportButton: Button?) {
        if (reportButton == null) {
            return
        }

        tintPrimaryButton(reportButton)

        if (config.isShowReportButton) {
            reportButton.visibility = View.VISIBLE
            reportButton.text = requireNotNull(config.reportButtonText)
            reportButton.setOnClickListener { dispatcher?.report() }
        } else {
            reportButton.visibility = View.GONE
        }
    }

    /**
     * Renders the shared reporting state from [ReportDispatcher]. XML and Compose
     * consume the same state machine, so their destination behavior, progress
     * and completion states, configuration errors, clipboard feedback, handler
     * failure feedback, uncertain-handoff messaging, and explicit fallback
     * actions stay equivalent. Dialog text comes verbatim from the dispatcher;
     * a message a dialog displays is never also toasted.
     */
    private fun renderReportState(state: ReportDispatcher.ReportState) {
        if (!state.messageShownInDialog) {
            state.message?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        when {
            state.phase == ReportDispatcher.Phase.WAITING_USER_ACTION -> {
                val prepared = state.prepared ?: return
                showReportDialog(state, prepared)
            }
            (state.phase == ReportDispatcher.Phase.FALLBACK_AVAILABLE
                    || state.phase == ReportDispatcher.Phase.CONFIG_INVALID
                    || state.phase == ReportDispatcher.Phase.UNKNOWN) && state.fallbackAvailable -> {
                offerExplicitFallback(state)
            }
            else -> Unit
        }
    }

    /**
     * Pre-navigation privacy confirmation. Callbacks are bound to the creating
     * dispatcher and guard on the staging state, so a stale dialog can neither
     * confirm nor cancel a newer pending request; [ReportDispatcher.confirmWebUrl]
     * additionally consumes the staged confirmation exactly once.
     */
    private fun showReportDialog(state: ReportDispatcher.ReportState, prepared: PreparedReport) {
        val creatingDispatcher = dispatcher ?: return
        val destinationLabel = if (prepared.destination == io.github.soufianodev.xtcrash.report.ReportDestination.GITHUB) "GitHub" else "GitLab"
        reportDialog?.dismiss()
        reportDialogState = state
        reportDialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.xtcrash_report_url_privacy_title, destinationLabel))
                .setMessage(state.message ?: "")
                .setPositiveButton(R.string.xtcrash_report_url_continue) { _, _ ->
                    if (reportDialogState === state) {
                        reportDialogState = null
                        creatingDispatcher.confirmWebUrl()
                    }
                }
                .setNegativeButton(R.string.xtcrash_report_url_cancel) { _, _ ->
                    if (reportDialogState === state) {
                        reportDialogState = null
                        creatingDispatcher.cancelPending()
                    }
                }
                .setOnCancelListener {
                    if (reportDialogState === state) {
                        reportDialogState = null
                        creatingDispatcher.cancelPending()
                    }
                }
                .show()
    }

    /**
     * Explicit user-selected fallback: bypasses the report-action handler and
     * custom sender, using the safe destination-specific action instead.
     * Buttons and text derive from the fallback's own state — never from a
     * previous request — and "Open issue page" routes through the shared
     * privacy confirmation, gated on the current state carrying a valid URL.
     */
    private fun offerExplicitFallback(state: ReportDispatcher.ReportState) {
        val creatingDispatcher = dispatcher ?: return
        val prepared = state.prepared
        val destination = prepared?.destination ?: config.reportDestination
        val builder = AlertDialog.Builder(this)
                .setTitle(R.string.customactivityoncrash_error_activity_error_details_title)
                .setMessage(state.message ?: "")

        val hasWebUrl = (destination == io.github.soufianodev.xtcrash.report.ReportDestination.GITHUB
                || destination == io.github.soufianodev.xtcrash.report.ReportDestination.GITLAB)
                && prepared?.url != null
        if (hasWebUrl) {
            builder.setPositiveButton(R.string.xtcrash_report_open_issue) { _, _ ->
                if (reportDialogState === state) {
                    reportDialogState = null
                    creatingDispatcher.requestWebUrlConfirmation()
                }
            }
        }
        builder.setNeutralButton(R.string.xtcrash_report_copy) { _, _ ->
            if (reportDialogState === state) {
                reportDialogState = null
                creatingDispatcher.copyReportForFallback()
            }
        }
        builder.setNegativeButton(R.string.customactivityoncrash_error_activity_error_details_close, null)

        reportDialog?.dismiss()
        reportDialogState = state
        reportDialog = builder.show()
    }

    private fun tintPrimaryButton(button: Button) {
        button.setTextColor(config.buttonTextColor)
        button.backgroundTintList = ColorStateList.valueOf(config.primaryButtonColor)
    }

    private fun tintSecondaryButton(button: Button) {
        button.setTextColor(config.secondaryButtonTextColor)
        button.backgroundTintList = ColorStateList.valueOf(config.secondaryButtonColor)
    }

    private fun showErrorDetailsDialog() {
        val errorInformation = reportSnapshot?.text
                ?: CrashActivity.getAllErrorDetailsFromIntent(this@DefaultErrorActivity, intent)
        val builder = AlertDialog.Builder(this@DefaultErrorActivity)
                .setTitle(R.string.customactivityoncrash_error_activity_error_details_title)
                .setMessage(errorInformation)
                .setPositiveButton(R.string.customactivityoncrash_error_activity_error_details_close, null)

        if (config.isShowCopyButtonInDetails) {
            val copyText = config.copyButtonText
                    ?: getString(R.string.customactivityoncrash_error_activity_error_details_copy)
            builder.setNeutralButton(copyText) { _, _ -> copyErrorToClipboard(errorInformation) }
        }

        val dialog = builder.show()
        detailsDialog = dialog
        val textView: TextView? = dialog.findViewById(android.R.id.message)
        if (textView != null) {
            textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.customactivityoncrash_error_activity_error_details_text_size)
            )
        }
    }

    private fun copyErrorToClipboard(errorInformation: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?

        if (clipboard != null) {
            val clip = ClipData.newPlainText(
                    getString(R.string.customactivityoncrash_error_activity_error_details_clipboard_label),
                    errorInformation
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                    this@DefaultErrorActivity,
                    R.string.customactivityoncrash_error_activity_error_details_copied,
                    Toast.LENGTH_SHORT
            ).show()
        } else {
            // Only report success after the clipboard operation actually succeeds.
            Toast.makeText(this, R.string.xtcrash_report_not_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
