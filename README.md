# XtCrash

XtCrash is an Android library that replaces the system crash dialog with a configurable error screen in a separate process. Users can review technical details, copy them, restart or close the app, and explicitly share a crash report.

- **Core:** Kotlin Android library with a Java-callable API; Android API 23+.
- **Optional Compose module:** hosted Compose error activity and a configurable composable screen.
- **Privacy:** reports are prepared on-device. XtCrash does not upload crash data automatically.
- **Lineage:** based on [CrashX](https://github.com/TutorialsAndroid/crashx), which derives from [CustomActivityOnCrash](https://github.com/Ereza/CustomActivityOnCrash); see [NOTICE](NOTICE).

## Documentation

Read the [XtCrash documentation website](https://soufianodev.github.io/XtCrash/):

- [Quick Start](https://soufianodev.github.io/XtCrash/quickstart/)
- [Configuration](https://soufianodev.github.io/XtCrash/configuration/)
- [Custom Error Screen](https://soufianodev.github.io/XtCrash/custom-error-screen/)
- [Crash Reports](https://soufianodev.github.io/XtCrash/crash-reports/)
- [Jetpack Compose](https://soufianodev.github.io/XtCrash/compose/)
- [Logging and Event Callbacks](https://soufianodev.github.io/XtCrash/logging-and-events/)
- [API Reference](https://soufianodev.github.io/XtCrash/api-reference/)

## Requirements

- Android API 23 or newer for consumer applications.
- To build from source: JDK 21, Android SDK with compile SDK 37, and the repository's Gradle wrapper (Gradle 9.4.1) with Android Gradle Plugin 9.2.1.

## Installation status

**The published JitPack coordinates are pending release publication.** The intended version is `v1.0.0`, but do not assume these coordinates resolve until the corresponding tag/artifact is published and verified.

Core artifact:

```kotlin
implementation("com.github.SoufianoDev.XtCrash:xtcrash:v1.0.0")
```

Optional Compose artifact:

```kotlin
implementation("com.github.SoufianoDev.XtCrash:xtcrash-compose:v1.0.0")
```

Both use JitPack. Check the [XtCrash JitPack page](https://jitpack.io/#SoufianoDev/XtCrash) and release notes before adopting a published coordinate.

### Build the core AAR from source

Until publication is available, build the library locally:

```bash
git clone https://github.com/SoufianoDev/XtCrash.git
cd XtCrash
./gradlew :library:assembleRelease
```

The release AAR is produced under `library/build/outputs/aar/`. Use that locally built AAR in your Android project, or include the module in your own Gradle build if your project setup supports it. Build the optional Compose module with `./gradlew :xtcrash-compose:assembleRelease`.

## Minimal configuration

Configure XtCrash once in your application's `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashConfig.Builder.create()
            .enabled(true)
            .showErrorDetails(true)
            .apply()
    }
}
```

The core library manifest declares `CrashInitProvider`; a separate manual installation call is not required. See the [Quick Start](https://soufianodev.github.io/XtCrash/quickstart/) for imports and a deliberate test crash.

## Project layout

- `library`: distributable core artifact (`xtcrash`).
- `xtcrash-compose`: optional distributable Compose artifact (`xtcrash-compose`).
- `xtcrash-sample`: local demonstration app; not a distributable library.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow and pull-request guidance. Runtime behavior and API changes should be reviewed separately from documentation-only changes.

## License

XtCrash is distributed under the [Apache License 2.0](LICENSE).
