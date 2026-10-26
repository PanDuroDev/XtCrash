---
title: Crash Reports
description: Configure report destinations, privacy confirmation, URL limits, and custom report handlers.
---

XtCrash prepares report content on-device and opens a user-controlled share or reporting flow. It does not upload crash reports automatically.

## Default sharing

Set `showReportButton(true)` to display the action. With no destination selected, XtCrash uses the Android Sharesheet. `supportEmail(...)` prepares an email draft; the user still reviews and sends it.

Reports can include crash ID, package and app version, Android version, device details, crash time, thread name, exception class/message, stack trace, build date, activity log, and custom text. Use the `include*()` builder options to control report fields.

## Destinations

| Destination | Configuration | Behavior |
| --- | --- | --- |
| Sharesheet | No destination selected | Opens Android's generic share UI. |
| `EMAIL` | `reportTo(ReportDestination.EMAIL)`, optional `supportEmail(...)` | Opens an email draft via `ACTION_SENDTO`; XtCrash does not send it. |
| `GITHUB` | `reportTo(ReportDestination.GITHUB)` plus `githubIssueTarget(owner, repository)` | Opens a prefilled new-issue form. |
| `GITLAB` | `reportTo(ReportDestination.GITLAB)` plus `gitLabIssueTarget(projectPath, baseUrl)` | Opens a prefilled new-issue form. |
| `CUSTOM` | `reportTo(ReportDestination.CUSTOM)` plus `reportSender(sender)` | Invokes your `ReportSender`. |

```kotlin
CrashConfig.Builder.create()
    .showReportButton(true)
    .reportTo(ReportDestination.GITHUB)
    .githubIssueTarget("your-org", "your-repository")
    .apply()
```

The library does not infer a GitHub repository or GitLab project from your package name. Configure explicit targets. Self-hosted GitLab uses the instance root URL, not a URL containing a project path.

## Privacy confirmation

Before opening GitHub or GitLab URLs that contain report text, XtCrash presents a confirmation step showing the destination and content that will be placed in the URL. Report content in a URL may be transmitted to the destination before the user submits the issue, so this confirmation is intentional.

Email opens a draft for the user to review. The library does not send it automatically.

## URL length and clipboard fallback

The implementation's default encoded URL budget is 8000 bytes; configured values below the supported floor are clamped. When a full report does not fit in a GitHub/GitLab prefill, XtCrash shortens only the prefill, not the canonical report. It attempts to copy the complete report to the clipboard and reports whether that copy succeeded. The user can paste the remainder into the issue form.

## Custom sender

A `ReportSender` receives an immutable `PreparedReport` and returns a `ReportOutcome`:

```kotlin
class MyReportSender : ReportSender {
    override fun send(report: PreparedReport): ReportOutcome {
        // Process report.reportText, report.subject, report.url, and report.crashId.
        return ReportOutcome.ACCEPTED
    }
}
```

Custom senders and report-action handlers run with a configured deadline. The default `reportHandlerTimeoutMs` is 5000 ms.

## Report action handler

An optional `ReportActionHandler` can accept responsibility for a prepared report before the configured destination's normal action. It is configured as a class so it can be constructed in the error-screen process:

```kotlin
CrashConfig.Builder.create()
    .reportActionHandler(MyReportHandler::class.java)
    .apply()
```

The handler class must be public and have a public no-argument constructor. Its outcome controls whether normal destination handling proceeds; consult the API reference for the exact contract.

## Uncertain outcomes

A timeout or exception after a sender has begun may occur after a remote service has accepted the report. In that case, `ReportOutcome.UNKNOWN` means the outcome is not known; it is not proof of failure. XtCrash does not automatically retry an uncertain report, because retrying could create duplicates.

Do not describe a report as uploaded or submitted unless your integration has confirmed that result. For API details, see the generated [API Reference](../api-reference/).
