---
title: Logging and Event Callbacks
description: Distinguish diagnostic log messages from error-screen lifecycle callbacks and configure each safely.
---

XtCrash exposes two observability mechanisms: diagnostic messages through `CrashLogListener` and lifecycle callbacks through `CrashActivity.EventListener`. They serve different purposes.

## Diagnostic logging

Logging is disabled by default. Enable logcat output explicitly:

```kotlin
CrashConfig.Builder.create()
    .loggingEnabled(true)
    .apply()
```

The library uses the `XtCrash` tag and standard Android log priorities. To forward the same diagnostic events into your own logging system, configure a `CrashLogListener`:

```kotlin
class ForwardingLogListener : CrashLogListener {
    override fun onCrashLog(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        // Forward to your logger; XtCrash does not store or upload the event.
    }
}
```

Configure it with `logListener(ForwardingLogListener())`. The listener must be a serializable top-level or static class because `CrashConfig` is transferred to the error-screen process. The builder checks declared listener fields for common non-serializable state, but cannot guarantee that every runtime object held behind an interface is serializable.

The listener receives diagnostics independently of the logcat toggle; the two sinks can be used together. A failing listener is caught so it does not escape into crash handling.

To capture install-time diagnostics, apply the configuration before the init provider runs, such as in `Application.attachBaseContext()`. Later configuration still applies to subsequent crash-time and error-screen events.

## Error-screen lifecycle callbacks

`CrashActivity.EventListener` reports these events:

| Callback | Meaning |
| --- | --- |
| `onLaunchErrorActivity()` | Called when XtCrash is about to launch the configured error activity. |
| `onRestartAppFromErrorActivity()` | Called as the error screen initiates a restart. |
| `onCloseAppFromErrorActivity()` | Called as the error screen initiates close behavior. |

Use a serializable top-level or static implementation:

```kotlin
class MyCrashEvents : CrashActivity.EventListener {
    override fun onLaunchErrorActivity() {
        // Record that the crash screen is being launched.
    }

    override fun onRestartAppFromErrorActivity() {
        // Record the restart action.
    }

    override fun onCloseAppFromErrorActivity() {
        // Record the close action.
    }
}
```

Configure it using `eventListener(MyCrashEvents())`. These callbacks are lifecycle signals, not substitutes for a diagnostic log stream or a guaranteed delivery system. Do not assume they execute on a particular thread or can access live objects from the crashing process; configuration crosses a process boundary through serialization.

See [Configuration](../configuration/) for the builder and [Crash Reports](../crash-reports/) for reporting callbacks and outcomes.
