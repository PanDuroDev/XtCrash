<p align="center">
  <img src="images/xtcrash-banner.svg" alt="XtCrash - modern crash recovery for Android" />
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/SoufianoDev/XtCrash?style=for-the-badge&logo=github&logoColor=white&labelColor=0D1526" alt="License: Apache-2.0" /></a>
  <a href="https://jitpack.io/#SoufianoDev/XtCrash"><img src="https://img.shields.io/github/v/tag/SoufianoDev/XtCrash?style=for-the-badge&logo=github&logoColor=white&labelColor=0D1526" alt="Latest version tag" /></a>
  <img src="https://img.shields.io/github/languages/top/SoufianoDev/XtCrash?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=0D1526" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/API-23%2B-8B5CF6?style=for-the-badge&logo=android&logoColor=white&labelColor=0D1526" alt="Minimum API 23" />
  <img src="https://img.shields.io/github/last-commit/SoufianoDev/XtCrash/master?style=for-the-badge&logo=github&logoColor=white&labelColor=0D1526" alt="Last commit" />
</p>

An Android library that replaces the default crash dialog with a configurable error screen: restart, close, technical details, copy to clipboard, crash report sharing, and per-crash IDs. The sources are Kotlin; the public API is Java-callable without changes.

XtCrash is built on top of [CrashX](https://github.com/TutorialsAndroid/crashx), which in turn is built on top of [CustomActivityOnCrash](https://github.com/Ereza/CustomActivityOnCrash). See [Attribution](#attribution).

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Custom Error Screen](#custom-error-screen)
- [Jetpack Compose](#jetpack-compose)
- [Crash Reports](#crash-reports)
- [Event Listener](#event-listener)
- [R8 Minification](#r8-minification)
- [Documentation](#documentation)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [Attribution](#attribution)
- [License](#license)

## Requirements

- Android API 23 or newer (`minSdk 23`)
- Any Android consumer setup that resolves the AAR; the Kotlin standard library ships transitively in the POM, so Java-only projects need no Kotlin plugin
- To build from source: JDK 21, Android SDK with `compileSdk 37`, the bundled Gradle 9.4.1 wrapper, AGP 9.2.1 (Kotlin support is built in, no extra plugin)

## Installation

### JitPack

Resolve coordinates `com.github.SoufianoDev.XtCrash:xtcrash:v1.0.0`. JitPack builds this once the `v1.0.0` tag exists on the repository; no tag has been published yet, so treat the coordinates as ready once tagging happens. Sub-module artifacts follow each module's publish metadata (`xtcrash`, `xtcrash-compose`), independent of directory names.

On JitPack the group mirrors the repository path (`com.github.USER.REPO:MODULE`), which is why the repo name appears in the group next to each artifact id. After the first tag, confirm the exact resolved names on [JitPack's artifact list](https://jitpack.io/#com.github.SoufianoDev/XtCrash/1.0.0).

Add the repository in your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency in your app module:

```kotlin
dependencies {
    implementation("com.github.SoufianoDev.XtCrash:xtcrash:v1.0.0")
}
```

Groovy DSL equivalent:

```gradle
dependencies {
    implementation 'com.github.SoufianoDev.XtCrash:xtcrash:v1.0.0'
}
```

### Build from source

```bash
git clone https://github.com/SoufianoDev/XtCrash.git
cd XtCrash
./gradlew :library:assembleRelease
```

The AAR is written to `library/build/outputs/aar/`.

## Quick Start

The library installs itself. `CrashInitProvider`, declared in the library manifest, runs as a content provider at app startup and installs an uncaught-exception handler before `Application.onCreate()`.

Configure once in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashConfig.Builder.create()
            .enabled(true)
            .showErrorDetails(true)
            .trackActivities(true)
            .showReportButton(true)
            .supportEmail("support@example.com")
            .apply()
    }
}
```

The same configuration from Java:

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashConfig.Builder.create()
                .enabled(true)
                .showErrorDetails(true)
                .trackActivities(true)
                .showReportButton(true)
                .supportEmail("support@example.com")
                .apply();
    }
}
```

Imports: `io.github.soufianodev.xtcrash.CrashActivity` and `io.github.soufianodev.xtcrash.config.CrashConfig`.

Force a crash to see the error screen:

```java
throw new RuntimeException("Test crash");
```

## Configuration

Core options and their defaults as defined in `CrashConfig`:

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | Boolean | `true` | Master switch; disable in debug builds or experiments when needed |
| `showErrorDetails` | Boolean | `true` | Shows the technical-details button and dialog |
| `showRestartButton` | Boolean | `true` | Restart button; restart target is looked up from the manifest when not configured |
| `showCloseButton` | Boolean | `true` | Close button that exits the app |
| `backgroundMode` | Int | `BACKGROUND_MODE_SHOW_CUSTOM` | `SHOW_CUSTOM` shows the screen on background crashes, `SILENT` closes the app, `CRASH` delegates to the previous handler |
| `trackActivities` | Boolean | `false` | Records an activity lifecycle log before the crash, capped at `maxActivityLogEntries` |
| `minTimeBetweenCrashesMs` | Int | `3000` | Crash-loop guard: repeated crashes inside this window are not shown again |
| `logErrorOnRestart` | Boolean | `true` | Logs the previous stack trace again when the error screen opens, so logcat sees it from the error process |
| `errorTitle` | String | `"Something went wrong"` | Screen title |
| `errorMessage` | String | `"The app ran into an unexpected problem. You can restart the app or send a crash report."` | Screen message |
| `showReportButton` | Boolean | `false` | Report/share button; combine with `supportEmail()` |
| `showCrashId` | Boolean | `true` | Displays the generated crash ID on the screen |
| `crashIdPrefix` | String | `"XTCRASH"` | Prefix for generated crash IDs |

Advanced options, all set through the same builder: custom labels for every button (`restartButtonText()`, `closeButtonText()`, `detailsButtonText()`, `reportButtonText()`, `copyButtonText()`), nine theme colors (`backgroundColor()`, `cardBackgroundColor()`, `primaryButtonColor()`, `secondaryButtonColor()`, `titleTextColor()`, `messageTextColor()`, `buttonTextColor()`, `secondaryButtonTextColor()`, `metaTextColor()`), a custom error icon (`errorDrawable()`), a custom restart target (`restartActivity()`), screen detail toggles (`showCopyButtonInDetails()`, `showAppInfo()`), report metadata toggles (`includeDeviceInfo()`, `includeActivityLog()`, `includeStackTrace()`, `includeBuildDate()`, `includeCrashId()`), report size limits (`maxStackTraceSize()`, default `131071`, and `maxActivityLogEntries()`, default `50`), and extra report text (`additionalReportInfo()`).

Every option reads back for building a UI from the current state: `CrashConfig.Builder.create()` starts from a copy of the current config, so sequential `apply()` calls keep previously applied settings unless overwritten.

## Custom Error Screen

Ship your own crash screen by subclassing and declaring the activity in the manifest on a separate process:

```xml
<activity
    android:name=".MyCrashActivity"
    android:process=":error_activity"
    android:exported="false" />
```

```kotlin
CrashConfig.Builder.create()
    .errorActivity(MyCrashActivity::class.java)
    .apply()
```

Static helpers on `CrashActivity` unpack the crash payload from the intent:

| Method | Returns |
|---|---|
| `CrashActivity.getConfigFromIntent(Intent)` | The serializable `CrashConfig` |
| `CrashActivity.getCrashIdFromIntent(Intent)` | Crash ID |
| `CrashActivity.getStackTraceFromIntent(Intent)` | Stack trace |
| `CrashActivity.getActivityLogFromIntent(Intent)` | Activity lifecycle log |
| `CrashActivity.getThreadNameFromIntent(Intent)` | Crashing thread name |
| `CrashActivity.getThrowableClassFromIntent(Intent)` | Exception class name |
| `CrashActivity.getThrowableMessageFromIntent(Intent)` | Exception message |
| `CrashActivity.getCrashDateFromIntent(Intent)` | Crash timestamp |
| `CrashActivity.getAllErrorDetailsFromIntent(Context, Intent)` | Combined details string |
| `CrashActivity.restartApplication(Context, CrashConfig)` | Restart the app |
| `CrashActivity.restartApplicationWithIntent(Context, Intent, CrashConfig)` | Restart to a specific target |
| `CrashActivity.closeApplication(Context, CrashConfig)` | Exit the app |

### Any project setup

The custom screen needs only three things: the manifest entry above, one `errorActivity(...)` line, and your own activity — whatever the stack. Every `CrashActivity` intent helper and the whole `CrashConfig` builder are static and Java-callable, so no Kotlin is required in the consumer, and the manifest entry doesn't get any code either — it is plain XML.

| Project setup | Custom error screen |
|---|---|
| Java + XML | Java activity, your XML layout (snippet below) |
| Kotlin + XML | Same, written in Kotlin |
| Java + Kotlin + XML | Same; plain setup, no extra wiring |
| Java + Kotlin + Compose + XML | Drop-in `ComposeErrorActivity` (see Jetpack Compose), or your own Compose activity |
| Java + Compose | Drop-in `ComposeErrorActivity` from Java; a fully custom Compose design needs one Kotlin file (Compose is Kotlin-only) |

Custom screen from pure Java + XML:

```java
public class MyErrorActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CrashConfig config = CrashActivity.getConfigFromIntent(getIntent());
        String errorDetails = CrashActivity.getAllErrorDetailsFromIntent(this, getIntent());

        setContentView(R.layout.my_error_screen); // your XML, your design
        // Bind any action to the library helpers:
        // CrashActivity.restartApplication(this, config);
        // CrashActivity.closeApplication(this, config);
    }
}
```

Misconfigurations are flagged at configure time: when the configured activity is missing from the merged manifest, or lacks its own `android:process`, the library logs a warning when the config is applied instead of failing silently during a crash.

## Jetpack Compose

Compose apps are supported out of the box: install, crash catching, tracking and the default screen work unchanged, and the core library carries no Compose dependency.

For a crash screen rendered in Compose, the optional `xtcrash-compose` artifact provides two levels of use:

**Drop-in (no code beyond one config line).** The artifact ships `ComposeErrorActivity`, a hosted crash activity in its own dedicated process, ready for any project:

```kotlin
dependencies {
    implementation("com.github.SoufianoDev.XtCrash:xtcrash-compose:1.0.0") // resolves with the same tag as the core
}
```

```kotlin
CrashConfig.Builder.create()
    .errorActivity(ComposeErrorActivity::class.java)
    .apply()
```

No manifest entry and no crash-activity code needed — the declaration ships inside the artifact and merges into your manifest.

**Config-driven composable.** `XtCrashErrorScreen` mirrors the default screen. It is driven entirely by `CrashConfig` — colors, labels, crash ID, the error details dialog, copy to clipboard and report sharing behave exactly like the XML screen — and it reuses the core string resources, so translations apply.

Point `errorActivity()` at your own `ComponentActivity`, then render the screen from the crash intent:

```kotlin
class ComposeCrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent) ?: run { finish(); return }
        setContent {
            XtCrashErrorScreen(
                config = config,
                errorDetails = CrashActivity.getAllErrorDetailsFromIntent(this, intent),
                crashId = CrashActivity.getCrashIdFromIntent(intent) ?: "",
            )
        }
    }
}
```

Restart and close buttons call `CrashActivity.restartApplication(...)` and `closeApplication(...)` internally; you only need the screen in `setContent`.

### Build your own screen

`XtCrashErrorScreen` mirrors the default look. For a fully custom design, skip it and render any composable from the crash activity — the screen is an ordinary `ComponentActivity`:

```kotlin
class ComposeCrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent) ?: run { finish(); return }
        setContent { MyAppCrashScreen(config) }
    }
}

@Composable
fun MyAppCrashScreen(config: CrashConfig) {
    val activity = LocalContext.current as? Activity ?: return
    // Your UI, your styling — config colors and labels are optional. Restart and close
    // stay library calls so the config's restart intent and close policy are applied:
    Column {
        Button(onClick = { CrashActivity.restartApplication(activity, config) }) { Text("Restart") }
        Button(onClick = { CrashActivity.closeApplication(activity, config) }) { Text("Close") }
    }
}
```

## Crash Reports

When `showReportButton(true)` is applied, the screen shows a share action. With `supportEmail()` set, the share intent is prepared as an email using `reportSubject()` and `reportChooserTitle()`; without one, the share sheet offers all available targets. Attach extra context with `additionalReportInfo()`.

The report can include: crash ID, package name, app version, library version (`CrashActivity.VERSION`), Android version, API level, device model, brand, manufacturer, crash date, crashing thread, exception class and message, stack trace, build date, activity lifecycle log, and the custom `additionalReportInfo()` text. Each item is controlled by its `include*()` toggle.

Reports are assembled on device and shared through Android share intents. The library sends nothing on its own.

## Event Listener

Track error-screen flows by implementing `CrashActivity.EventListener`: `onLaunchErrorActivity()`, `onRestartAppFromErrorActivity()`, and `onCloseAppFromErrorActivity()`. Set it with `eventListener()`.

The listener is serialized with the crash config, so anonymous and non-static inner classes are rejected by the builder with an explanation. Use a top-level class or a static inner class.

## R8 Minification

The AAR ships `library/consumer-rules.pro`, so consuming apps get the keep rules automatically. No app-side configuration is needed for standard usage.

Custom features are serialized through intents (config, event listener). If your app runs aggressive shrinking and you use a custom error activity or event listener, keep those classes in your own rules as well.

## Documentation

Extended documentation lives in `docs/` as Docusaurus-ready MDX pages:

| Page | Covers |
|---|---|
| [Introduction](docs/introduction.mdx) | What the library does, artifact map, non-goals |
| [Quick Start](docs/quickstart.mdx) | Install, configure, first test crash |
| [Configuration](docs/configuration.mdx) | Complete `CrashConfig` reference |
| [Custom Error Screen](docs/custom-error-screen.mdx) | Subclass, manifest entry, intent helpers |
| [Compose](docs/compose.mdx) | Drop-in activity, composable screen, custom designs |
| [Material 3 Theming](docs/material-3-theming.mdx) | Config colors mapped to M3 color roles |

## Architecture

```
library/src/main/java/io/github/soufianodev/xtcrash/
├── CrashActivity.kt              core: install(), config holder, intent helpers, statics
├── config/CrashConfig.kt         serializable config + fluent builder
├── activity/DefaultErrorActivity.kt   the default crash screen UI
└── provider/CrashInitProvider.kt      manifest-declared provider, installs the handler at startup
```

Flow: the provider installs an uncaught-exception handler, preserving any previously installed one (initialize XtCrash first, then Firebase Crashlytics, ACRA, or another handler, so the chain stays intact). A crash builds the serializable payload, then either launches the error activity or delegates by `backgroundMode`. The error activity runs in a dedicated `:xtcrash_error` process and survives the main process dying. `minTimeBetweenCrashesMs` prevents restart loops.

## Contributing

Development setup, build verification, review process, and code conventions: [CONTRIBUTING](CONTRIBUTING.md). Pull requests get an automated CodeRabbit review on open and on each new push; a human maintainer closes the merge. Security issues go by email to `contact@crashx.dev` rather than the public tracker.

## Attribution

XtCrash is built on top of **CrashX**, which in turn is built on top of **CustomActivityOnCrash**.

- [CustomActivityOnCrash](https://github.com/Ereza/CustomActivityOnCrash): the original library by **Eduard Ereza Martínez**, Apache License 2.0
- [CrashX](https://github.com/TutorialsAndroid/crashx): a maintained, modernized continuation of CustomActivityOnCrash
- XtCrash: this repository, a standalone continuation of the CrashX line

The [`NOTICE`](NOTICE) file records the derivative-work chain and the [`LICENSE`](LICENSE) file carries the Apache License 2.0 text. Both stay in the repository at distribution time.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
