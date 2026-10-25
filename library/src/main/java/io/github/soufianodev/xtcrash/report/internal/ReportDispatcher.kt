package io.github.soufianodev.xtcrash.report.internal

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

import io.github.soufianodev.xtcrash.R
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.PreparedReport
import io.github.soufianodev.xtcrash.report.ReportActionHandler
import io.github.soufianodev.xtcrash.report.ReportDestination
import io.github.soufianodev.xtcrash.report.ReportOutcome
import io.github.soufianodev.xtcrash.report.ReportSender

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Single shared report-preparation and dispatch path used by both the XML and
 * Compose error screens. Destination selection, canonical-report preparation,
 * custom sending, and optional report-action handling are kept as distinct
 * responsibilities inside one state machine so neither UI duplicates them.
 *
 * <p>Public only so the separate `xtcrash-compose` module can drive the same
 * pipeline; it is not part of the documented consumer API.</p>
 */
class ReportDispatcher(
    private val context: Context,
    private val config: CrashConfig,
    private val canonicalReport: String,
    private val crashId: String,
    private val subject: String,
) {
    /** Listener for user-visible reporting state changes. Always invoked on the main thread. */
    interface Listener {
        fun onReportState(state: ReportState)
    }

    /**
     * Observable reporting state exposed to the UI.
     * [message] is phase-specific detail text (localised where it originates
     * from a string resource); for [Phase.WAITING_USER_ACTION] it is the full
     * confirmation-dialog text the UI displays verbatim.
     */
    data class ReportState(
        val phase: Phase,
        val message: String? = null,
        val fallbackAvailable: Boolean = false,
        val prepared: PreparedReport? = null,
    )

    enum class Phase {
        IDLE,
        PREPARED,
        HANDLER_RUNNING,
        ACCEPTED,
        WAITING_USER_ACTION,
        HANDOFF_ATTEMPTED,
        UNKNOWN,
        CONFIG_INVALID,
        FALLBACK_AVAILABLE,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeRequestId = AtomicReference<String?>(null)
    private val inFlight = AtomicBoolean(false)
    private var listener: Listener? = null

    /** Decoded prefill body of the currently prepared web URL, for the privacy dialog. */
    @Volatile
    private var prefillBody: String = ""

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /**
     * Starts a fresh reporting request. Generates a new request ID (distinct
     * from the crash ID, so one crash can have several legitimate requests)
     * and runs the shared dispatch sequence. Repeated taps while a request is
     * in flight are ignored so no uncontrolled concurrent attempts start.
     */
    fun report() {
        if (!inFlight.compareAndSet(false, true)) {
            return
        }
        val requestId = UUID.randomUUID().toString()
        activeRequestId.set(requestId)
        dispatch(requestId)
    }

    /**
     * Releases the in-flight guard after the user dismisses a confirmation or
     * fallback dialog, so a later explicit tap can start a new request.
     */
    fun cancelPending() {
        inFlight.set(false)
    }

    private fun emit(state: ReportState) {
        // Terminal phases end the request; WAITING_USER_ACTION keeps it open
        // until the user confirms or cancels the dialog.
        when (state.phase) {
            Phase.ACCEPTED, Phase.HANDOFF_ATTEMPTED, Phase.UNKNOWN,
            Phase.CONFIG_INVALID, Phase.FALLBACK_AVAILABLE -> inFlight.set(false)
            else -> Unit
        }
        mainHandler.post {
            listener?.onReportState(state)
        }
    }

    private fun dispatch(requestId: String) {
        // 1-2. Validate the selected destination.
        val destination = config.reportDestination
        if (destination != null) {
            val validationError = validateDestination(destination)
            if (validationError != null) {
                emit(ReportState(Phase.CONFIG_INVALID, validationError, fallbackAvailable = true))
                return
            }
        }

        // 3-6. Prepare the canonical report and destination-specific content.
        val prepared = prepare(requestId, destination)
        emit(ReportState(Phase.PREPARED, prepared = prepared))

        // 8. Invoke the optional report-action handler when configured.
        val handlerClass = config.reportActionHandlerClass
        if (handlerClass != null) {
            invokeHandler(requestId, handlerClass, prepared)
            return
        }

        // No handler: run the normal action for the selected destination.
        performDestinationAction(requestId, prepared)
    }

    private fun validateDestination(destination: ReportDestination): String? {
        return when (destination) {
            ReportDestination.GITHUB -> {
                val owner = config.githubOwner
                val repo = config.githubRepository
                if (owner.isNullOrEmpty() || repo.isNullOrEmpty()) {
                    context.getString(R.string.xtcrash_report_config_invalid)
                } else {
                    null
                }
            }
            ReportDestination.GITLAB -> {
                val path = config.gitLabProjectPath
                val baseUrl = config.gitLabBaseUrl
                if (path.isNullOrEmpty() || baseUrl.isNullOrEmpty()) {
                    context.getString(R.string.xtcrash_report_config_invalid)
                } else if (!IssueUrlBuilder.isSupportedBaseUrl(baseUrl)) {
                    context.getString(R.string.xtcrash_report_config_invalid)
                } else {
                    null
                }
            }
            ReportDestination.EMAIL, ReportDestination.CUSTOM -> null
        }
    }

    private fun prepare(requestId: String, destination: ReportDestination?): PreparedReport {
        val prepared = PreparedReport()
        prepared.crashId = crashId
        prepared.requestId = requestId
        prepared.destination = destination
        prepared.reportText = canonicalReport
        prepared.subject = subject

        when (destination) {
            ReportDestination.GITHUB -> {
                val built = IssueUrlBuilder.github(
                    owner = requireNotNull(config.githubOwner),
                    repository = requireNotNull(config.githubRepository),
                    title = subject,
                    blocks = issueBlocks(),
                    budgetBytes = IssueUrlBuilder.clampBudget(config.reportUrlBudgetBytes),
                )
                prepared.url = built.url
                prepared.prefillOmitsReport = built.shortened
                prefillBody = built.body
            }
            ReportDestination.GITLAB -> {
                val built = IssueUrlBuilder.gitlab(
                    baseUrl = requireNotNull(config.gitLabBaseUrl),
                    projectPath = requireNotNull(config.gitLabProjectPath),
                    title = subject,
                    blocks = issueBlocks(),
                    budgetBytes = IssueUrlBuilder.clampBudget(config.reportUrlBudgetBytes),
                )
                prepared.url = built.url
                prepared.prefillOmitsReport = built.shortened
                prefillBody = built.body
            }
            else -> Unit
        }
        return prepared
    }

    /**
     * Prioritized report blocks for web prefills, highest priority first.
     * Projections of the canonical report; the canonical text is never mutated.
     */
    private fun issueBlocks(): List<PrefillShortener.Block> {
        val blocks = mutableListOf<PrefillShortener.Block>()
        blocks.add(PrefillShortener.Block("Crash ID: $crashId\nSubject: $subject\n"))

        config.additionalReportInfo?.let {
            blocks.add(PrefillShortener.Block("Additional information:\n$it\n\n"))
        }

        if (config.isIncludeStackTrace) {
            extractStackTrace(canonicalReport)?.takeIf { it.isNotBlank() }?.let {
                blocks.add(PrefillShortener.Block("Stack trace:\n$it\n"))
            }
        }

        if (config.isIncludeDeviceInfo) {
            extractSection(canonicalReport, "Package:", "Stack trace:").takeIf { it.isNotBlank() }?.let {
                blocks.add(PrefillShortener.Block(it))
            }
        }

        if (config.isIncludeActivityLog) {
            extractSection(canonicalReport, "Activity log:", null).takeIf { it.isNotBlank() }?.let {
                blocks.add(PrefillShortener.Block(it))
            }
        }

        return blocks
    }

    private fun extractStackTrace(report: String): String? {
        val marker = "Stack trace:\n"
        val start = report.indexOf(marker)
        if (start < 0) return null
        val body = report.substring(start + marker.length)
        val end = body.indexOf("\nActivity log:")
        return if (end >= 0) body.substring(0, end) else body
    }

    private fun extractSection(report: String, startMarker: String, endMarker: String?): String {
        val start = report.indexOf(startMarker)
        if (start < 0) return ""
        val body = if (endMarker == null) {
            report.substring(start)
        } else {
            val end = report.indexOf(endMarker, start)
            if (end >= 0) report.substring(start, end) else report.substring(start)
        }
        return body.trim()
    }

    private fun invokeHandler(requestId: String, handlerClass: Class<out ReportActionHandler>, prepared: PreparedReport) {
        emit(ReportState(Phase.HANDLER_RUNNING, prepared = prepared))
        val timeoutMs = config.reportHandlerTimeoutMs.toLong()

        Thread {
            val result = runHandlerAttempt(handlerClass, prepared, timeoutMs)
            mainHandler.post {
                if (activeRequestId.get() != requestId) {
                    // A newer request superseded this one; drop the stale result
                    // so it cannot overwrite the current crash's UI.
                    return@post
                }
                handleOutcome(requestId, result, prepared)
            }
        }.start()
    }

    /**
     * Constructs and invokes the handler on a worker thread with a bounded
     * deadline. Construction failure is [ReportOutcome.FAILED] (nothing ran, so
     * acceptance is impossible). A timeout or an exception thrown by the
     * extension itself is [ReportOutcome.UNKNOWN], because acceptance may have
     * happened before the failure and cannot be disproved. The worker cannot be
     * forcibly terminated; the deadline only stops waiting for it.
     */
    private fun runHandlerAttempt(
        handlerClass: Class<out ReportActionHandler>,
        prepared: PreparedReport,
        timeoutMs: Long,
    ): ReportOutcome {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference(ReportOutcome.UNKNOWN)
        val constructed = AtomicBoolean(false)

        val worker = Thread {
            try {
                val handler = handlerClass.getDeclaredConstructor().newInstance()
                constructed.set(true)
                outcome.set(handler.onReport(prepared))
            } catch (t: Throwable) {
                outcome.set(if (constructed.get()) ReportOutcome.UNKNOWN else ReportOutcome.FAILED)
            } finally {
                latch.countDown()
            }
        }
        worker.isDaemon = true
        worker.start()

        val completed = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (ie: InterruptedException) {
            false
        }

        if (!completed) {
            worker.interrupt()
            return ReportOutcome.UNKNOWN
        }
        return outcome.get()
    }

    /**
     * Interprets an extension outcome. ACCEPTED means only durable acceptance
     * of responsibility — never a submitted issue or sent email — and never
     * starts another flow. UNKNOWN never triggers an automatic retry.
     */
    private fun handleOutcome(requestId: String, outcome: ReportOutcome, prepared: PreparedReport) {
        when (outcome) {
            ReportOutcome.ACCEPTED -> {
                emit(ReportState(Phase.ACCEPTED, message = context.getString(R.string.xtcrash_report_accepted), prepared = prepared))
            }
            ReportOutcome.NOT_HANDLED -> {
                performDestinationAction(requestId, prepared)
            }
            ReportOutcome.FAILED -> {
                emit(ReportState(
                    Phase.FALLBACK_AVAILABLE,
                    message = context.getString(R.string.xtcrash_report_failed),
                    fallbackAvailable = true,
                    prepared = prepared,
                ))
            }
            ReportOutcome.UNKNOWN -> {
                emit(ReportState(
                    Phase.UNKNOWN,
                    message = context.getString(R.string.xtcrash_report_unknown),
                    fallbackAvailable = true,
                    prepared = prepared,
                ))
            }
        }
    }

    /** Normal action for the selected destination. Also the fallback executor. */
    private fun performDestinationAction(requestId: String, prepared: PreparedReport) {
        when (prepared.destination) {
            null -> performSharesheetAction(prepared)
            ReportDestination.EMAIL -> performEmailAction(prepared)
            ReportDestination.GITHUB, ReportDestination.GITLAB -> performWebAction(prepared)
            ReportDestination.CUSTOM -> performCustomAction(requestId, prepared)
        }
    }

    /**
     * Pre-existing generic Android Sharesheet, unchanged: used whenever no
     * destination is configured. Also the crash-report share fallback.
     */
    private fun performSharesheetAction(prepared: PreparedReport) {
        val chooserTitle = config.reportChooserTitle ?: requireNotNull(config.reportButtonText)
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, subject)
        send.putExtra(Intent.EXTRA_TEXT, canonicalReport)
        config.supportEmail?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        try {
            context.startActivity(Intent.createChooser(send, chooserTitle))
            emit(ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            val copied = copyToClipboard(canonicalReport)
            emit(ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = if (copied) {
                    context.getString(R.string.xtcrash_no_share_app_found)
                } else {
                    context.getString(R.string.xtcrash_report_not_copied)
                },
                fallbackAvailable = true,
                prepared = prepared,
            ))
        }
    }

    /**
     * Email-only flow: ACTION_SENDTO with a mailto: URI so only email clients
     * are offered. The suggested recipient comes from `supportEmail()` when
     * configured; without one the user picks or enters a recipient. Nothing is
     * ever sent automatically. Resolution relies on startActivity +
     * ActivityNotFoundException instead of package queries, which avoids
     * package-visibility gaps on API 30+.
     */
    private fun performEmailAction(prepared: PreparedReport) {
        val email = config.supportEmail
        val sendTo = Intent(Intent.ACTION_SENDTO)
        sendTo.data = Uri.parse("mailto:" + (email ?: ""))
        sendTo.putExtra(Intent.EXTRA_SUBJECT, subject)
        sendTo.putExtra(Intent.EXTRA_TEXT, canonicalReport)

        try {
            context.startActivity(sendTo)
            emit(ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            val copied = copyToClipboard(canonicalReport)
            val message = when {
                copied && email == null -> context.getString(R.string.xtcrash_report_email_no_client)
                copied -> context.getString(R.string.xtcrash_report_email_launch_failed)
                else -> context.getString(R.string.xtcrash_report_email_not_copied)
            }
            emit(ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = message,
                fallbackAvailable = true,
                prepared = prepared,
            ))
        }
    }

    /**
     * Web destinations: prepare the URL, copy the full report when the prefill
     * had to be shortened, then wait for the privacy confirmation that must
     * appear before any report-bearing URL is opened.
     */
    private fun performWebAction(prepared: PreparedReport) {
        val url = prepared.url
        if (url == null) {
            emit(ReportState(
                Phase.CONFIG_INVALID,
                message = context.getString(R.string.xtcrash_report_config_invalid),
                fallbackAvailable = true,
                prepared = prepared,
            ))
            return
        }

        if (prepared.prefillOmitsReport) {
            // The shortened prefill cannot claim to hold the whole report, so
            // offer the complete canonical text on the clipboard.
            prepared.reportCopiedToClipboard = copyToClipboard(canonicalReport)
        }

        emit(ReportState(
            Phase.WAITING_USER_ACTION,
            message = privacyDialogMessage(prepared),
            fallbackAvailable = true,
            prepared = prepared,
        ))
    }

    /**
     * Full confirmation-dialog text shown before a report-bearing URL is
     * opened. Built here once so the XML and Compose screens cannot drift; it
     * only claims a clipboard copy when one actually succeeded.
     */
    private fun privacyDialogMessage(prepared: PreparedReport): String {
        val label = if (prepared.destination == ReportDestination.GITHUB) "GitHub" else "GitLab"
        val message = context.getString(R.string.xtcrash_report_url_privacy_message, label, prefillBody)
        if (!prepared.prefillOmitsReport) return message
        val note = if (prepared.reportCopiedToClipboard) {
            context.getString(R.string.xtcrash_report_manual_paste)
        } else {
            context.getString(R.string.xtcrash_report_manual_paste_not_copied)
        }
        return message + "\n\n" + note
    }

    /** Opens the prepared web URL after the user confirmed. */
    fun openConfirmedWebUrl(prepared: PreparedReport) {
        val url = prepared.url
        if (url == null) {
            emit(ReportState(
                Phase.CONFIG_INVALID,
                message = context.getString(R.string.xtcrash_report_config_invalid),
                fallbackAvailable = true,
                prepared = prepared,
            ))
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            emit(ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            emit(ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = context.getString(R.string.xtcrash_report_not_copied),
                fallbackAvailable = true,
                prepared = prepared,
            ))
        }
    }

    private fun performCustomAction(requestId: String, prepared: PreparedReport) {
        val sender = config.reportSender
        if (sender == null) {
            emit(ReportState(
                Phase.CONFIG_INVALID,
                message = context.getString(R.string.xtcrash_report_config_invalid),
                fallbackAvailable = true,
                prepared = prepared,
            ))
            return
        }

        emit(ReportState(Phase.HANDLER_RUNNING, prepared = prepared))
        val timeoutMs = config.reportHandlerTimeoutMs.toLong()

        Thread {
            val outcome = runSenderAttempt(sender, prepared, timeoutMs)
            mainHandler.post {
                if (activeRequestId.get() != requestId) {
                    return@post
                }
                handleOutcome(requestId, outcome, prepared)
            }
        }.start()
    }

    /** Same bounded-execution contract as [runHandlerAttempt]. */
    private fun runSenderAttempt(sender: ReportSender, prepared: PreparedReport, timeoutMs: Long): ReportOutcome {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference(ReportOutcome.UNKNOWN)

        val worker = Thread {
            try {
                outcome.set(sender.send(prepared))
            } catch (t: Throwable) {
                // The extension failed mid-flight: acceptance may have happened
                // before the throw, so this is UNKNOWN, not FAILED.
                outcome.set(ReportOutcome.UNKNOWN)
            } finally {
                latch.countDown()
            }
        }
        worker.isDaemon = true
        worker.start()

        val completed = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (ie: InterruptedException) {
            false
        }

        if (!completed) {
            worker.interrupt()
            return ReportOutcome.UNKNOWN
        }
        return outcome.get()
    }

    private fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                false
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("XtCrash report", text))
                true
            }
        } catch (t: Throwable) {
            false
        }
    }
}
