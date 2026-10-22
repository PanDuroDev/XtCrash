package io.github.soufianodev.xtcrash.compose

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

/**
 * Jetpack Compose crash screen for XtCrash.
 *
 * <p>Mirrors the library's default error screen: every visual and behavioral knob comes
 * from [CrashConfig], and all labels reuse the core string resources so translations carry
 * over. Renders from your own crash activity, pointed to via
 * `CrashConfig.Builder.errorActivity(...)`. Originally derived from
 * CustomActivityOnCrash by Eduard Ereza Martínez.</p>
 */
@Composable
fun XtCrashErrorScreen(
        config: CrashConfig,
        errorDetails: String,
        crashId: String = "",
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

    var showDetailsDialog by remember { mutableStateOf(false) }

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
                                text = stringResource(R.string.crashx_crash_id_format, crashId),
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
                                onClick = { context.shareCrashReport(config, errorDetails, crashId) },
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
                                text = stringResource(R.string.crashx_version_format, CrashActivity.VERSION),
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

private fun Context.copyErrorToClipboard(errorDetails: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

    clipboard.setPrimaryClip(
            ClipData.newPlainText(
                    getString(R.string.customactivityoncrash_error_activity_error_details_clipboard_label),
                    errorDetails,
            )
    )
    Toast.makeText(this, R.string.customactivityoncrash_error_activity_error_details_copied, Toast.LENGTH_SHORT).show()
}

private fun Context.shareCrashReport(config: CrashConfig, errorDetails: String, crashId: String) {
    val subject = config.reportSubject
            ?: getString(R.string.crashx_default_report_subject, packageName, crashId)
    val chooserTitle = requireNotNull(config.reportButtonText)

    val sendIntent = Intent(Intent.ACTION_SEND)
    sendIntent.type = "text/plain"
    sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
    sendIntent.putExtra(Intent.EXTRA_TEXT, errorDetails)

    val supportEmail = config.supportEmail
    if (supportEmail != null) {
        sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
    }

    try {
        startActivity(Intent.createChooser(sendIntent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        copyErrorToClipboard(errorDetails)
        Toast.makeText(this, R.string.crashx_no_share_app_found, Toast.LENGTH_SHORT).show()
    }
}

private fun toComposeColor(value: Int): Color = Color(value.toLong() and 0xFFFFFFFFL)
