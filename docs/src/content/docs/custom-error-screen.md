---
title: Custom Error Screen
description: Register a custom error activity, keep it in a separate process, and read crash details from its intent.
---

A custom error screen requires an activity, a manifest entry in a dedicated process, and one configuration call.

## 1. Declare the activity

```xml
<activity
    android:name=".MyCrashActivity"
    android:process=":error_activity"
    android:exported="false" />
```

Use a process name distinct from your application's main process. XtCrash's built-in error activities use `:xtcrash_error`; a custom activity can use its own process name.

## 2. Configure the activity

```kotlin
CrashConfig.Builder.create()
    .errorActivity(MyCrashActivity::class.java)
    .apply()
```

XtCrash checks the merged manifest when the configuration is applied. A missing activity declaration or missing dedicated process is reported as a warning rather than throwing during application startup.

## 3. Read the crash payload

The payload is passed through intent extras because the error screen runs in a separate process. Use the public helpers instead of duplicating extra keys:

| Helper | Result |
| --- | --- |
| `getConfigFromIntent(intent)` | The serialized `CrashConfig`, falling back to current config if absent. |
| `getCrashIdFromIntent(intent)` | Crash ID, or `"unknown"` if absent. |
| `getStackTraceFromIntent(intent)` | Stack trace, or null if not included. |
| `getActivityLogFromIntent(intent)` | Activity log, or null if unavailable. |
| `getThreadNameFromIntent(intent)` | Crashing thread name, or `"unknown"`. |
| `getThrowableClassFromIntent(intent)` | Exception class, or `"unknown"`. |
| `getThrowableMessageFromIntent(intent)` | Exception message, or null. |
| `getCrashDateFromIntent(intent)` | Crash timestamp, with a generated fallback if absent. |
| `getAllErrorDetailsFromIntent(context, intent)` | Canonical formatted report text. |
| `getReportFromIntent(context, intent)` | Structured report snapshot for further processing. |

## Kotlin example

```kotlin
class MyCrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent)
        val details = CrashActivity.getAllErrorDetailsFromIntent(this, intent)
        setContentView(R.layout.my_error_screen)
        // Bind buttons to CrashActivity.restartApplication(this, config)
        // and CrashActivity.closeApplication(this, config).
    }
}
```

## Java interoperability

The public helpers and builder are Java-callable. A Java activity can call the same methods and use an XML layout; Kotlin is not required for a custom XML screen.

## Important constraints

- The error activity must be in a separate process so it can open after the crashing process is terminated.
- Treat the payload as serialized data; do not rely on live objects from the original process.
- Event listeners and custom reporting handlers are process-sensitive; see [Logging and Event Callbacks](../logging-and-events/) and [Crash Reports](../crash-reports/).
