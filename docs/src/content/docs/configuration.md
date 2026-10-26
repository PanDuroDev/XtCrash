---
title: Configuration
description: Configure error-screen behavior, appearance, logging, and reporting using CrashConfig.Builder.
---

Configure XtCrash through `CrashConfig.Builder`. The builder's `create()` method starts from a copy of the current configuration; `apply()` installs that configuration for subsequent crash handling, while `get()` returns the configured object without applying it.

## Common options

| Builder call | Default | Purpose |
| --- | --- | --- |
| `enabled(true)` | `true` | Enables XtCrash crash handling. |
| `showErrorDetails(true)` | `true` | Shows the technical-details action. |
| `showRestartButton(true)` | `true` | Shows Restart when a target is available. |
| `showCloseButton(true)` | `true` | Shows the close action. |
| `trackActivities(true)` | `false` | Records a bounded activity lifecycle log. |
| `minTimeBetweenCrashesMs(3000)` | `3000` ms | Prevents repeated error screens during a crash loop. |
| `showReportButton(true)` | `false` | Enables report/share actions. |
| `showCrashId(true)` | `true` | Displays the generated crash ID. |
| `loggingEnabled(true)` | `false` | Writes XtCrash diagnostics to logcat. |

```kotlin
CrashConfig.Builder.create()
    .enabled(true)
    .showErrorDetails(true)
    .trackActivities(true)
    .errorTitle("Something went wrong")
    .errorMessage("You can restart the app or share a report.")
    .apply()
```

## Text and appearance

Override labels with `restartButtonText()`, `closeButtonText()`, `detailsButtonText()`, `reportButtonText()`, and `copyButtonText()`.

Colors are integer ARGB values: `backgroundColor()`, `cardBackgroundColor()`, `primaryButtonColor()`, `secondaryButtonColor()`, `titleTextColor()`, `messageTextColor()`, `buttonTextColor()`, `secondaryButtonTextColor()`, and `metaTextColor()`. Use `errorDrawable()` to supply a drawable resource.

For `errorTitle`, `errorMessage`, `reportButtonText`, and `crashIdPrefix`, null or blank input falls back to the configured default. Other optional text values such as `supportEmail`, `reportSubject`, and `additionalReportInfo` normalize blank input to null.

## Restart and custom activities

- `restartActivity(MyActivity::class.java)` selects an explicit restart target. If absent, XtCrash attempts to resolve a launcher activity from the manifest.
- `errorActivity(MyErrorActivity::class.java)` selects a custom error activity. It must be declared in the merged manifest and run in a separate process; see [Custom Error Screen](../custom-error-screen/).
- `backgroundMode()` controls behavior for background crashes: `BACKGROUND_MODE_SHOW_CUSTOM`, `BACKGROUND_MODE_SILENT`, or `BACKGROUND_MODE_CRASH`.

## Crash report options

Use `includeDeviceInfo()`, `includeActivityLog()`, `includeStackTrace()`, `includeBuildDate()`, and `includeCrashId()` to choose report fields. `maxStackTraceSize()` defaults to 131071 characters and clamps to at least 1024. `maxActivityLogEntries()` defaults to 50 and clamps to at least 1.

Destination routing, URL limits, confirmation, and custom report handlers are covered in [Crash Reports](../crash-reports/).

## Logging

Logging is opt-in. `loggingEnabled(true)` enables XtCrash messages in logcat; `logListener(...)` forwards diagnostic events to a developer-defined sink. The listener must be a serializable top-level or static class because configuration is transferred to the error-screen process. See [Logging and Event Callbacks](../logging-and-events/).

## Builder semantics

- `create()` copies the currently applied configuration; it does not reset all values to factory defaults.
- `get()` returns the builder's configuration without applying it globally.
- `apply()` installs the builder's configuration.
- Repeated `apply()` calls preserve earlier settings unless explicitly overwritten.
