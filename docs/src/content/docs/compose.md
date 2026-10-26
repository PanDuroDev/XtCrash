---
title: Jetpack Compose
description: Choose between the hosted Compose activity, the configuration-driven composable, and a fully custom screen.
---

The core `xtcrash` module does not depend on Compose. The optional `xtcrash-compose` module provides Compose UI for crash screens.

> **Publication status:** The `v1.0.0` JitPack coordinates are pending until the tag and artifact are published and verified. See the [JitPack project page](https://jitpack.io/#SoufianoDev/XtCrash).

```kotlin
dependencies {
    implementation("com.github.SoufianoDev.XtCrash:xtcrash-compose:v1.0.0")
}
```

## Option 1: Hosted activity

Use `ComposeErrorActivity` when the supplied Compose error screen is sufficient:

```kotlin
CrashConfig.Builder.create()
    .errorActivity(ComposeErrorActivity::class.java)
    .apply()
```

Its manifest declaration is contributed by the Compose artifact and runs in the dedicated `:xtcrash_error` process. No consumer manifest entry is needed for this supplied activity.

## Option 2: Configuration-driven composable

Use `XtCrashErrorScreen` inside your own `ComponentActivity` to keep the library's standard behavior while controlling the activity integration:

```kotlin
class ComposeCrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent) ?: run {
            finish()
            return
        }
        val details = CrashActivity.getAllErrorDetailsFromIntent(this, intent)
        val crashId = CrashActivity.getCrashIdFromIntent(intent)
        setContent {
            XtCrashErrorScreen(config = config, errorDetails = details, crashId = crashId)
        }
    }
}
```

The composable reads visual and behavioral options from `CrashConfig`, including colors, labels, details, clipboard copying, and report actions. It does not currently expose a `Modifier` parameter; that is a separate API-design consideration and is not changed in this documentation work.

## Option 3: Fully custom UI

For a fully custom layout, render your own composable and connect its buttons to the supported `CrashActivity` helpers:

```kotlin
class ComposeCrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent) ?: run {
            finish()
            return
        }
        setContent { MyCrashScreen(config) }
    }
}

@Composable
fun MyCrashScreen(config: CrashConfig) {
    val activity = LocalContext.current as? Activity ?: return
    Column {
        Button(onClick = { CrashActivity.restartApplication(activity, config) }) { Text("Restart") }
        Button(onClick = { CrashActivity.closeApplication(activity, config) }) { Text("Close") }
    }
}
```

## Choosing a path

| Path | Consumer code | Customization |
| --- | --- | --- |
| Hosted activity | One configuration call | Standard screen and behavior |
| Configuration-driven composable | Activity plus `setContent` | Standard screen driven by `CrashConfig` |
| Fully custom UI | Your screen and helper calls | Layout controlled by your app |

Compose is Kotlin-only, but Java consumers can select the hosted activity from Java. For configuration options, see [Configuration](../configuration/).
