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
import io.github.soufianodev.xtcrash.report.ReportSection
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
 * <p>Web navigation is two-phase: [requestWebUrlConfirmation] stages the
 * privacy confirmation, and [confirmWebUrl] consumes it exactly once before
 * opening the URL. Stale, cancelled, replaced, or invalidated confirmations
 * are no-ops, so no fallback action can bypass the confirmation or reuse a
 * previous crash's URL. [invalidate] discards a replaced or destroyed
 * dispatcher's pending work without implying that an external handler's
 * acceptance was reversed.</p>
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
    private val sections: List<ReportSection>?,
) {
    /** Listener for user-visible reporting state changes. Always invoked on the main thread. */
    interface Listener {
        fun onReportState(state: ReportState)
    }

    /**
     * Observable reporting state exposed to the UI. [message] is the complete
     * user-visible text for the phase (localised where it originates from a
     * string resource); for [Phase.WAITING_USER_ACTION] it is the full
     * confirmation-dialog text, and for fallback phases it is the full
     * fallback-dialog text built from the operation's actual outcome.
     */
    data class ReportState(
        val phase: Phase,
        val message: String? = null,
        val fallbackAvailable: Boolean = false,
        val prepared: PreparedReport? = null,
    ) {
        /**
         * True when a dialog renders [message]; the UI must not also toast it.
         * Dialog rendering itself is gated the same way (a prepared report for
         * the confirmation, an available fallback otherwise), so a message
         * that no dialog shows is still surfaced by the toast.
         */
        val messageShownInDialog: Boolean
            get() = when (phase) {
                Phase.WAITING_USER_ACTION -> prepared != null
                Phase.FALLBACK_AVAILABLE, Phase.CONFIG_INVALID, Phase.UNKNOWN -> fallbackAvailable
                else -> false
            }
    }

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
    private val ownership = ReportRequestOwnership()
    private var listener: Listener? = null

    /** Decoded prefill body of the currently prepared web URL, for the privacy dialog. */
    @Volatile
    private var prefillBody: String = ""

    /** True while the current web prefill has no body because sections were unusable. */
    @Volatile
    private var prefillOmitsUnusableSections: Boolean = false

    /** Prepared report of the most recent request, for [requestWebUrlConfirmation]. */
    @Volatile
    private var currentPrepared: PreparedReport? = null

    /** Request id of the most recent request, passed to every emission it causes. */
    @Volatile
    private var currentRequestId: String? = null

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
        if (!ownership.tryBegin()) {
            return
        }
        val requestId = UUID.randomUUID().toString()
        ownership.beginRequest(requestId)
        currentRequestId = requestId
        dispatch(requestId)
    }

    /**
     * Releases the in-flight guard and any staged web confirmation after the
     * user dismisses a confirmation or fallback dialog, so a later explicit
     * tap can start a new request.
     */
    fun cancelPending() {
        ownership.releaseInFlight()
        ownership.clearConfirmation()
    }

    /**
     * Silently discards this dispatcher's pending request: late worker
     * results, queued listener notifications, destination side effects, and
     * staged confirmations all become no-ops. Called when the activity is
     * replaced or destroyed. This only drops local effects — it never implies
     * that an external handler's acceptance was cancelled or rolled back.
     */
    fun invalidate() {
        currentRequestId = null
        currentPrepared = null
        ownership.invalidate()
    }

    /**
     * Requests the privacy confirmation for the current prepared web URL. The
     * single entry point for both the normal web action and the explicit
     * fallback, so every route to opening a report-bearing URL passes through
     * the confirmation state. Takes no arguments so the UI cannot pass a stale
     * or foreign prepared report. A no-op once this dispatcher has been
     * invalidated or superseded, so it cannot copy a discarded report.
     */
    fun requestWebUrlConfirmation() {
        val requestId = currentRequestId ?: return
        if (!ownership.owns(requestId)) {
            return
        }
        val prepared = currentPrepared ?: return
        performWebAction(requestId, prepared)
    }

    /**
     * Opens the URL of the pending confirmation, consuming it. Only a live,
     * unconsumed confirmation owned by the current request navigates; stale,
     * cancelled, superseded, or invalidated confirms are no-ops.
     */
    fun confirmWebUrl() {
        val requestId = currentRequestId ?: return
        val prepared = ownership.consumeConfirmation(requestId) ?: return
        openUrl(requestId, prepared)
    }

    private fun emit(requestId: String, state: ReportState) {
        // Terminal phases end the request; WAITING_USER_ACTION keeps it open
        // until the user confirms or cancels the dialog, and stages the one
        // confirmation confirmWebUrl may consume.
        when (state.phase) {
            Phase.ACCEPTED, Phase.HANDOFF_ATTEMPTED, Phase.UNKNOWN,
            Phase.CONFIG_INVALID, Phase.FALLBACK_AVAILABLE -> ownership.releaseInFlight()
            else -> Unit
        }
        if (state.phase == Phase.WAITING_USER_ACTION) {
            state.prepared?.let { ownership.stageConfirmation(requestId, it) }
        }
        mainHandler.post {
            // Check ownership again inside the callback: a newer request can
            // start between the emission and this post running, and a stale
            // state must not update the UI or drive a dialog action.
            if (!ownership.owns(requestId)) {
                return@post
            }
            listener?.onReportState(state)
        }
    }

    private fun dispatch(requestId: String) {
        // 1-2. Validate the selected destination.
        val destination = config.reportDestination
        if (destination != null) {
            val validationError = validateDestination(destination)
            if (validationError != null) {
                // currentPrepared stays unset so a later fallback "Open issue
                // page" cannot act on a report this request never prepared.
                emit(requestId, ReportState(Phase.CONFIG_INVALID, validationError, fallbackAvailable = true))
                return
            }
        }

        // 3-6. Prepare the canonical report and destination-specific content.
        val prepared = prepare(requestId, destination)
        currentPrepared = prepared
        emit(requestId, ReportState(Phase.PREPARED, prepared = prepared))

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
                if (owner.isNullOrEmpty() || repo.isNullOrEmpty()
                        || !IssueUrlBuilder.isSafePathComponent(owner)
                        || !IssueUrlBuilder.isSafePathComponent(repo)) {
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
                    blocks = prefillBlocks(),
                    budgetBytes = IssueUrlBuilder.clampBudget(config.reportUrlBudgetBytes),
                )
                applyBuiltUrl(prepared, built)
            }
            ReportDestination.GITLAB -> {
                val built = IssueUrlBuilder.gitlab(
                    baseUrl = requireNotNull(config.gitLabBaseUrl),
                    projectPath = requireNotNull(config.gitLabProjectPath),
                    title = subject,
                    blocks = prefillBlocks(),
                    budgetBytes = IssueUrlBuilder.clampBudget(config.reportUrlBudgetBytes),
                )
                applyBuiltUrl(prepared, built)
            }
            else -> Unit
        }
        return prepared
    }

    /**
     * Prioritized prefill blocks projected from the canonical report's
     * sections. The canonical text is never mutated, and when the supplied
     * sections are absent or inconsistent with the report there is no prefill
     * body at all — a non-empty report then always counts as omitted so the
     * clipboard fallback stays truthful.
     */
    private fun prefillBlocks(): List<PrefillShortener.Block> {
        val plan = ReportPrefill.plan(sections, canonicalReport)
        if (plan.unusableSections) {
            prefillBody = ""
            prefillOmitsUnusableSections = canonicalReport.isNotEmpty()
        } else {
            prefillOmitsUnusableSections = false
        }
        return plan.blocks
    }

    private fun applyBuiltUrl(prepared: PreparedReport, built: BuiltUrl) {
        // An empty or structurally invalid url (e.g. an oversized title, or a
        // target that escaped its path) stays unset so the UIs offer Copy
        // only instead of opening a malformed target.
        val url = built.url.ifEmpty { null }?.takeIf { IssueUrlBuilder.isValidIssueUrl(it) }
        prepared.url = url
        prepared.prefillOmitsReport = built.shortened || prefillOmitsUnusableSections
        prefillBody = built.body
    }

    private fun invokeHandler(requestId: String, handlerClass: Class<out ReportActionHandler>, prepared: PreparedReport) {
        emit(requestId, ReportState(Phase.HANDLER_RUNNING, prepared = prepared))
        val timeoutMs = config.reportHandlerTimeoutMs.toLong()

        Thread {
            val result = runHandlerAttempt(handlerClass, prepared, timeoutMs)
            mainHandler.post {
                if (!ownership.owns(requestId)) {
                    // A newer request or an invalidation superseded this one;
                    // drop the stale result so it cannot act on the current
                    // crash or a replaced dispatcher.
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
                emit(requestId, ReportState(Phase.ACCEPTED, message = context.getString(R.string.xtcrash_report_accepted), prepared = prepared))
            }
            ReportOutcome.NOT_HANDLED -> {
                performDestinationAction(requestId, prepared)
            }
            ReportOutcome.FAILED -> {
                emit(requestId, ReportState(
                    Phase.FALLBACK_AVAILABLE,
                    message = fallbackMessage(context.getString(R.string.xtcrash_report_failed), prepared),
                    fallbackAvailable = true,
                    prepared = prepared,
                ))
            }
            ReportOutcome.UNKNOWN -> {
                emit(requestId, ReportState(
                    Phase.UNKNOWN,
                    message = fallbackMessage(context.getString(R.string.xtcrash_report_unknown), prepared),
                    fallbackAvailable = true,
                    prepared = prepared,
                ))
            }
        }
    }

    /** Normal action for the selected destination. Also the fallback executor. */
    private fun performDestinationAction(requestId: String, prepared: PreparedReport) {
        when (prepared.destination) {
            null -> performSharesheetAction(requestId, prepared)
            ReportDestination.EMAIL -> performEmailAction(requestId, prepared)
            ReportDestination.GITHUB, ReportDestination.GITLAB -> performWebAction(requestId, prepared)
            ReportDestination.CUSTOM -> performCustomAction(requestId, prepared)
        }
    }

    /**
     * Pre-existing generic Android Sharesheet, unchanged: used whenever no
     * destination is configured. Also the crash-report share fallback.
     */
    private fun performSharesheetAction(requestId: String, prepared: PreparedReport) {
        val chooserTitle = config.reportChooserTitle ?: requireNotNull(config.reportButtonText)
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, subject)
        send.putExtra(Intent.EXTRA_TEXT, canonicalReport)
        config.supportEmail?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        try {
            context.startActivity(Intent.createChooser(send, chooserTitle))
            emit(requestId, ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            prepared.reportCopiedToClipboard = copyToClipboard(canonicalReport)
            emit(requestId, ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = if (prepared.reportCopiedToClipboard) {
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
    private fun performEmailAction(requestId: String, prepared: PreparedReport) {
        val email = config.supportEmail
        val sendTo = Intent(Intent.ACTION_SENDTO)
        sendTo.data = Uri.parse("mailto:" + (email ?: ""))
        sendTo.putExtra(Intent.EXTRA_SUBJECT, subject)
        sendTo.putExtra(Intent.EXTRA_TEXT, canonicalReport)

        try {
            context.startActivity(sendTo)
            emit(requestId, ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            prepared.reportCopiedToClipboard = copyToClipboard(canonicalReport)
            val status = context.getString(R.string.xtcrash_report_no_email_client)
            emit(requestId, ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = fallbackMessage(status, prepared),
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
    private fun performWebAction(requestId: String, prepared: PreparedReport) {
        val url = prepared.url
        if (url == null) {
            emit(requestId, ReportState(
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

        emit(requestId, ReportState(
            Phase.WAITING_USER_ACTION,
            message = privacyDialogMessage(prepared),
            fallbackAvailable = true,
            prepared = prepared,
        ))
    }

    /**
     * Copies the canonical report for the fallback dialog's explicit "Copy
     * report" action, recording the result on the current prepared report so a
     * later message or browser failure states the clipboard truth. Emits the
     * same fallback state again with refreshed text. A no-op once this
     * dispatcher has been invalidated or superseded.
     */
    fun copyReportForFallback() {
        val requestId = currentRequestId ?: return
        if (!ownership.owns(requestId)) {
            return
        }
        val prepared = currentPrepared
        val copied = copyToClipboard(canonicalReport)
        if (prepared != null) {
            prepared.reportCopiedToClipboard = copied
        }
        val status = if (copied) {
            context.getString(R.string.xtcrash_report_copied)
        } else {
            context.getString(R.string.xtcrash_report_not_copied)
        }
        // Validation failures leave currentPrepared null, but the instruction
        // must still reflect this copy and the configured destination —
        // never a fresh PreparedReport whose clipboard flag says "not copied".
        val message = status + "\n\n" + pasteInstruction(
                copied,
                prepared?.destination ?: config.reportDestination,
        )
        emit(requestId, ReportState(
            Phase.FALLBACK_AVAILABLE,
            message = message,
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
        val url = prepared.url
            ?: return context.getString(R.string.xtcrash_report_url_privacy_invalid_url)
        val preview = IssueUrlPrivacyPreviewBuilder.build(
            url,
            prepared.destination ?: return context.getString(R.string.xtcrash_report_url_privacy_invalid_url),
        ) ?: return context.getString(R.string.xtcrash_report_url_privacy_invalid_url)
        val isGitHub = prepared.destination == ReportDestination.GITHUB
        val destinationLabel = context.getString(
            if (isGitHub) R.string.xtcrash_report_destination_github
            else R.string.xtcrash_report_destination_gitlab,
        )
        val fields = mutableListOf<String>()
        preview.title?.let {
            fields += context.getString(R.string.xtcrash_report_url_privacy_title_field, it)
        }
        preview.description?.let {
            fields += context.getString(R.string.xtcrash_report_url_privacy_description_field, it)
        }
        val details = if (fields.isEmpty()) {
            context.getString(R.string.xtcrash_report_url_privacy_no_prefill)
        } else {
            context.getString(
                R.string.xtcrash_report_url_privacy_fields_present,
                fields.joinToString("\n"),
            )
        }
        val destinationUrl = preview.destinationUrl
        var message = context.getString(
            R.string.xtcrash_report_url_privacy_destination,
            destinationLabel,
            destinationUrl,
            details,
        )
        if (prepared.prefillOmitsReport) {
            val note = if (prepared.reportCopiedToClipboard) {
                context.getString(R.string.xtcrash_report_manual_paste)
            } else {
                context.getString(R.string.xtcrash_report_manual_paste_not_copied)
            }
            message += "\n\n" + note
        }
        return message
    }

    /**
     * Full fallback-dialog text: the factual status of the operation that
     * failed plus a paste instruction matching the actual clipboard state.
     * Built here once so neither UI reconstructs destination-specific claims.
     */
    private fun fallbackMessage(status: String, prepared: PreparedReport): String {
        return status + "\n\n" + pasteInstruction(
                prepared.reportCopiedToClipboard,
                prepared.destination,
        )
    }

    private fun pasteInstruction(copied: Boolean, destination: ReportDestination?): String =
        when (PasteInstruction.select(copied, destination)) {
            PasteInstruction.Kind.COPY_THEN_PASTE -> context.getString(R.string.xtcrash_report_paste_instruction)
            PasteInstruction.Kind.EMAIL_COPIED -> context.getString(R.string.xtcrash_report_email_copied)
            PasteInstruction.Kind.ISSUE_FORM -> context.getString(R.string.xtcrash_report_manual_paste)
        }

    /** Opens the confirmed web URL. Only reachable via [confirmWebUrl]. */
    private fun openUrl(requestId: String, prepared: PreparedReport) {
        val url = prepared.url
        if (url == null) {
            emit(requestId, ReportState(
                Phase.CONFIG_INVALID,
                message = context.getString(R.string.xtcrash_report_config_invalid),
                fallbackAvailable = true,
                prepared = prepared,
            ))
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            emit(requestId, ReportState(Phase.HANDOFF_ATTEMPTED, prepared = prepared))
        } catch (e: ActivityNotFoundException) {
            emit(requestId, ReportState(
                Phase.FALLBACK_AVAILABLE,
                message = fallbackMessage(context.getString(R.string.xtcrash_report_no_browser), prepared),
                fallbackAvailable = true,
                prepared = prepared,
            ))
        }
    }

    private fun performCustomAction(requestId: String, prepared: PreparedReport) {
        val sender = config.reportSender
        if (sender == null) {
            // currentPrepared was never set for this request, so a later
            // fallback cannot act on a report the sender never took.
            emit(requestId, ReportState(
                Phase.CONFIG_INVALID,
                message = context.getString(R.string.xtcrash_report_config_invalid),
                fallbackAvailable = true,
                prepared = prepared,
            ))
            return
        }

        emit(requestId, ReportState(Phase.HANDLER_RUNNING, prepared = prepared))
        val timeoutMs = config.reportHandlerTimeoutMs.toLong()

        Thread {
            val outcome = runSenderAttempt(sender, prepared, timeoutMs)
            mainHandler.post {
                if (!ownership.owns(requestId)) {
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
