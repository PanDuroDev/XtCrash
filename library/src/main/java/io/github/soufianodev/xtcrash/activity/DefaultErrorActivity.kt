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

/**
 * Default XtCrash error screen.
 *
 * <p>Originally derived from CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 * <p>Modernized and maintained as CrashX by TutorialsAndroid.</p>
 */
class DefaultErrorActivity : AppCompatActivity() {

    private lateinit var config: CrashConfig

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

        renderCrash()
    }

    /**
     * Re-delivers a crash into a live instance. With `launchMode="singleTask"`
     * the platform routes a repeat launch of this screen through
     * [onNewIntent] instead of a new instance; without this hook the old
     * screen would keep showing the previous crash and the restart-time
     * trace would not be logged again.
     */
    // shortcut: an Android 16 device was also seen re-presenting the cached
    // task with NO callback at all, which this hook cannot catch - that case
    // keeps showing the previous crash's data. Ceiling: platform intent
    // delivery; upgrade path if devices reproduce it is taskAffinity or
    // launch-flag hardening, then a device matrix re-test.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        config = CrashActivity.getConfigFromIntent(intent)
        renderCrash()
    }

    private fun renderCrash() {
        setupWindow()
        setupTextContent()
        setupMetaContent()
        setupImage()
        setupButtons()
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
        val drawableId: Int? = config.errorDrawable
        val errorImageView: ImageView? = findViewById(R.id.crash_error_activity_image)

        if (errorImageView != null && drawableId != null) {
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
            reportButton.setOnClickListener { shareCrashReport() }
        } else {
            reportButton.visibility = View.GONE
        }
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
        val builder = AlertDialog.Builder(this@DefaultErrorActivity)
                .setTitle(R.string.customactivityoncrash_error_activity_error_details_title)
                .setMessage(CrashActivity.getAllErrorDetailsFromIntent(this@DefaultErrorActivity, intent))
                .setPositiveButton(R.string.customactivityoncrash_error_activity_error_details_close, null)

        if (config.isShowCopyButtonInDetails) {
            val copyText = config.copyButtonText
                    ?: getString(R.string.customactivityoncrash_error_activity_error_details_copy)
            builder.setNeutralButton(copyText) { _, _ -> copyErrorToClipboard() }
        }

        val dialog = builder.show()
        val textView: TextView? = dialog.findViewById(android.R.id.message)
        if (textView != null) {
            textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.customactivityoncrash_error_activity_error_details_text_size)
            )
        }
    }

    private fun copyErrorToClipboard() {
        val errorInformation = CrashActivity.getAllErrorDetailsFromIntent(this@DefaultErrorActivity, intent)
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
        }
    }

    private fun shareCrashReport() {
        val errorInformation = CrashActivity.getAllErrorDetailsFromIntent(this@DefaultErrorActivity, intent)
        val subject = config.reportSubject
                ?: getString(R.string.xtcrash_default_report_subject, packageName, CrashActivity.getCrashIdFromIntent(intent))
        val chooserTitle = config.reportChooserTitle
                ?: requireNotNull(config.reportButtonText)

        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "text/plain"
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        sendIntent.putExtra(Intent.EXTRA_TEXT, errorInformation)

        val supportEmail = config.supportEmail
        if (supportEmail != null) {
            sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
        }

        try {
            startActivity(Intent.createChooser(sendIntent, chooserTitle))
        } catch (e: ActivityNotFoundException) {
            copyErrorToClipboard()
            Toast.makeText(this, R.string.xtcrash_no_share_app_found, Toast.LENGTH_SHORT).show()
        }
    }
}
