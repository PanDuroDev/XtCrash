package io.github.soufianodev.xtcrash.compose

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import io.github.soufianodev.xtcrash.CrashActivity
import io.github.soufianodev.xtcrash.R
import io.github.soufianodev.xtcrash.config.CrashConfig
import io.github.soufianodev.xtcrash.report.PreparedReport
import io.github.soufianodev.xtcrash.report.ReportDestination
import io.github.soufianodev.xtcrash.report.ReportSection
import io.github.soufianodev.xtcrash.report.internal.ReportDispatcher

/**
 * Jetpack Compose crash screen for XtCrash.
 *
 * <p>Mirrors the library's default error screen: every visual and behavioral knob comes
 * from [CrashConfig], and all labels reuse the core string resources so translations carry
 * over. Renders from your own crash activity, pointed to via
 * `CrashConfig.Builder.errorActivity(...)`. Originally derived from
 * CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 *
 * <p>Reporting shares the same preparation and dispatch pipeline as the XML screen
 * ([ReportDispatcher]), so both interfaces stay behaviorally equivalent.</p>
 */
@Composable
fun XtCrashErrorScreen(
        config: CrashConfig,
        errorDetails: String,
        crashId: String = "",
        subject: String? = null,
        sections: List<ReportSection>? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    SideEffect {
        activity?.window?.let { window ->
            // shortcut: mirrors DefaultErrorActivity's deprecated statusBarColor tinting;
            // upgrade to the edge-to-edge insets API when the core screen does.
            window.statusBarColor = config.backgroundColor
            window.navigationBarColor = config.backgroundColor
        }
    }

    // Keyed on every captured crash input: a new payload (e.g. a repeat
    // singleTask launch via onNewIntent) rebuilds the dispatcher and resets
    // all dialog state, so no previous crash's report or URL survives.
    val dispatcher = remember(context, config, errorDetails, crashId, subject, sections) {
        ReportDispatcher(
                context = context,
                config = config,
                canonicalReport = errorDetails,
                crashId = crashId,
                subject = subject ?: CrashActivity.getReportSubject(context, config, crashId),
                sections = sections,
        )
    }
    var showDetailsDialog by remember(dispatcher) { mutableStateOf(false) }
    var reportState by remember(dispatcher) { mutableStateOf<ReportDispatcher.ReportState?>(null) }
    var pendingWeb by remember(dispatcher) { mutableStateOf<ReportDispatcher.ReportState?>(null) }

    androidx.compose.runtime.DisposableEffect(dispatcher) {
        val listener = object : ReportDispatcher.Listener {
            override fun onReportState(state: ReportDispatcher.ReportState) {
                // A message the dialogs render is never also toasted.
                if (!state.messageShownInDialog) {
                    state.message?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                if (state.phase == ReportDispatcher.Phase.WAITING_USER_ACTION) {
                    pendingWeb = state
                }
                reportState = state
            }
        }
        dispatcher.setListener(listener)
        onDispose {
            dispatcher.invalidate()
            dispatcher.setListener(null)
        }
    }

    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .background(toComposeColor(config.backgroundColor)),
            contentAlignment = Alignment.Center,
    ) {
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(all = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                    modifier = Modifier.widthIn(min = 560.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = toComposeColor(config.cardBackgroundColor)),
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 34.dp, end = 28.dp, bottom = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                            painter = painterResource(config.errorDrawable ?: R.drawable.crash_ic_bug_report),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                    )

                    Text(
                            text = requireNotNull(config.errorTitle),
                            textAlign = TextAlign.Center,
                            color = toComposeColor(config.titleTextColor),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 22.dp),
                    )

                    Text(
                            text = requireNotNull(config.errorMessage),
                            textAlign = TextAlign.Center,
                            color = toComposeColor(config.messageTextColor),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 10.dp),
                    )

                    if (config.isShowCrashId) {
                        Text(
                                text = stringResource(R.string.xtcrash_crash_id_format, crashId),
                                textAlign = TextAlign.Center,
                                color = toComposeColor(config.metaTextColor),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 14.dp),
                        )
                    }

                    if (config.isShowRestartButton && config.restartActivityClass != null) {
                        ActionButton(
                                text = config.restartButtonText
                                        ?: stringResource(R.string.customactivityoncrash_error_activity_restart_app),
                                containerColor = toComposeColor(config.primaryButtonColor),
                                contentColor = toComposeColor(config.buttonTextColor),
                                modifier = Modifier.padding(top = 26.dp),
                                onClick = { CrashActivity.restartApplication(requireNotNull(activity), config) },
                        )
                    }

                    if (config.isShowReportButton) {
                        ActionButton(
                                text = requireNotNull(config.reportButtonText),
                                containerColor = toComposeColor(config.primaryButtonColor),
                                contentColor = toComposeColor(config.buttonTextColor),
                                modifier = Modifier.padding(top = 10.dp),
                                onClick = { dispatcher.report() },
                        )
                    }

                    if (config.isShowErrorDetails) {
                        ActionButton(
                                text = config.detailsButtonText
                                        ?: stringResource(R.string.customactivityoncrash_error_activity_error_details),
                                containerColor = toComposeColor(config.secondaryButtonColor),
                                contentColor = toComposeColor(config.secondaryButtonTextColor),
                                modifier = Modifier.padding(top = 10.dp),
                                onClick = { showDetailsDialog = true },
                        )
                    }

                    if (config.isShowCloseButton) {
                        ActionButton(
                                text = config.closeButtonText
                                        ?: stringResource(R.string.customactivityoncrash_error_activity_close_app),
                                containerColor = toComposeColor(config.secondaryButtonColor),
                                contentColor = toComposeColor(config.secondaryButtonTextColor),
                                modifier = Modifier.padding(top = 10.dp),
                                onClick = { CrashActivity.closeApplication(requireNotNull(activity), config) },
                        )
                    }

                    if (config.isShowAppInfo) {
                        Text(
                                text = stringResource(R.string.xtcrash_version_format, CrashActivity.VERSION),
                                textAlign = TextAlign.Center,
                                color = toComposeColor(config.metaTextColor),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
            }
        }

        if (showDetailsDialog) {
            ErrorDetailsDialog(config, errorDetails, onDismiss = { showDetailsDialog = false })
        }

        pendingWeb?.let { state ->
            val prepared = state.prepared
            if (prepared != null) {
                PrivacyConfirmDialog(
                        prepared = prepared,
                        message = state.message ?: "",
                        onConfirm = {
                            // Guard on the staging state so a stale dialog cannot
                            // confirm a newer pending request; confirmWebUrl consumes
                            // the staged confirmation exactly once.
                            if (pendingWeb === state) {
                                pendingWeb = null
                                dispatcher.confirmWebUrl()
                            }
                        },
                        onDismiss = {
                            if (pendingWeb === state) {
                                pendingWeb = null
                                dispatcher.cancelPending()
                            }
                        },
                )
            }
        }

        reportState?.let { state ->
            val needsFallback = state.phase == ReportDispatcher.Phase.FALLBACK_AVAILABLE
                    || state.phase == ReportDispatcher.Phase.CONFIG_INVALID
                    || state.phase == ReportDispatcher.Phase.UNKNOWN
            if (needsFallback && state.fallbackAvailable) {
                ExplicitFallbackDialog(
                        config = config,
                        prepared = state.prepared,
                        message = state.message ?: "",
                        onCopy = { context.copyErrorToClipboard(errorDetails) },
                        onOpenIssue = {
                            if (reportState === state) {
                                reportState = null
                                dispatcher.requestWebUrlConfirmation()
                            }
                        },
                        onDismiss = { if (reportState === state) reportState = null },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
        text: String,
        containerColor: Color,
        contentColor: Color,
        modifier: Modifier,
        onClick: () -> Unit,
) {
    Button(
            onClick = onClick,
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
            modifier = modifier
                    .fillMaxWidth()
                    .height(50.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorDetailsDialog(config: CrashConfig, errorDetails: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.customactivityoncrash_error_activity_error_details_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)) {
                    Text(
                            text = errorDetails,
                            // shortcut: the XML dialog sizes its message through the
                            // crash_ dimen resource (12sp); a literal keeps this in sync manually.
                            fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.customactivityoncrash_error_activity_error_details_close))
                }
            },
            dismissButton = {
                if (config.isShowCopyButtonInDetails) {
                    TextButton(onClick = { context.copyErrorToClipboard(errorDetails) }) {
                        Text(
                                config.copyButtonText
                                        ?: stringResource(R.string.customactivityoncrash_error_activity_error_details_copy),
                        )
                    }
                }
            },
    )
}

/**
 * Pre-navigation privacy confirmation shown before opening a report-bearing web
 * URL. Report content in query parameters may be transmitted to the destination
 * before the user submits the issue, so this must appear before navigation.
 * [message] is the dispatcher-built dialog text, so XML and Compose cannot drift.
 */
@Composable
private fun PrivacyConfirmDialog(
        prepared: PreparedReport,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
) {
    val destinationLabel = if (prepared.destination == ReportDestination.GITHUB) "GitHub" else "GitLab"
    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.xtcrash_report_url_privacy_title, destinationLabel))
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)) {
                    Text(message)
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.xtcrash_report_url_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.xtcrash_report_url_cancel))
                }
            },
    )
}

/**
 * Explicit user-selected fallback dialog. Invoked only after an uncertain or
 * failed handoff, and it bypasses the report-action handler and custom sender.
 * [message] is the dispatcher-built text, so neither UI reconstructs
 * destination-specific claims; "Open issue page" only appears when the current
 * state carries a valid web URL and routes through the shared privacy
 * confirmation.
 */
@Composable
private fun ExplicitFallbackDialog(
        config: CrashConfig,
        prepared: PreparedReport?,
        message: String,
        onCopy: () -> Unit,
        onOpenIssue: () -> Unit,
        onDismiss: () -> Unit,
) {
    val destination = prepared?.destination ?: config.reportDestination
    val isWeb = destination == ReportDestination.GITHUB || destination == ReportDestination.GITLAB
    val hasWebUrl = isWeb && prepared?.url != null

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.customactivityoncrash_error_activity_error_details_title)) },
            text = { Text(message) },
            confirmButton = {
                if (hasWebUrl) {
                    TextButton(onClick = onOpenIssue) {
                        Text(stringResource(R.string.xtcrash_report_open_issue))
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.customactivityoncrash_error_activity_error_details_close))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.xtcrash_report_copy))
                }
            },
    )
}

private fun Context.copyErrorToClipboard(errorDetails: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        // Only report success after the clipboard operation actually succeeds.
        Toast.makeText(this, R.string.xtcrash_report_not_copied, Toast.LENGTH_SHORT).show()
        return
    }

    clipboard.setPrimaryClip(
            ClipData.newPlainText(
                    getString(R.string.customactivityoncrash_error_activity_error_details_clipboard_label),
                    errorDetails,
            )
    )
    Toast.makeText(this, R.string.customactivityoncrash_error_activity_error_details_copied, Toast.LENGTH_SHORT).show()
}

private fun toComposeColor(value: Int): Color = Color(value.toLong() and 0xFFFFFFFFL)
