## [1.0.0] - 2026-10-22

### Added

- Opt-in logging (`loggingEnabled()`) for the library's own install and crash messages; optional `CrashLogListener` (`logListener()`) forwards the same events to the developer's own logging framework with standard `android.util.Log` priorities. The library is silent by default.
- Optional Jetpack Compose artifact (`xtcrash-compose`, resolving as `com.github.SoufianoDev.XtCrash:xtcrash-compose`) with `XtCrashErrorScreen`, a composable crash screen that mirrors the default one.
- `ComposeErrorActivity` in the `xtcrash-compose` artifact: a hosted, manifest-declared Compose error screen enabled with one config line (`errorActivity(ComposeErrorActivity::class.java)`), no consumer-side crash activity or manifest entry needed.
- Configure-time validation of custom error activities: when the configured activity is missing from the merged manifest or lacks its own `android:process`, the library logs a warning when the config is applied, instead of failing silently at crash time. All flavors (Java/Kotlin, XML/Compose) benefit.

### Changed

- Migrated library and sample sources from Java to Kotlin.
- The project was renamed to **XtCrash** (previously CrashX). The on-device crash-data/preferences file is now `xtcrash` (was `crashx`, renamed before the first release, so no stored data is lost by consumers), the log tag is `XtCrash`, and string resource keys are `xtcrash_*`.
- **Breaking:** new, professional package names following reverse-domain-device conventions — the core library and its sub-packages are now `io.github.soufianodev.xtcrash.*` (`config`, `activity`, `provider`), the Compose artifact is `io.github.soufianodev.xtcrash.compose`, and the sample app is `io.github.soufianodev.xtcrash.sample`. Intent payload keys and the init-provider authority (now `xtcrash-init-provider`) follow the new package. Update imports and, for custom crash activities, the manifest class references. The `crash_` drawable prefix and `customactivityoncrash_*` resource-key names are unchanged. Module directories are `library` (core; its published artifact stays `xtcrash` per its publish metadata — artifact id and directory are independent on JitPack), `xtcrash-compose`, and `xtcrash-sample`. Consumer coordinates follow the modules' publish metadata (`com.github.SoufianoDev.XtCrash:xtcrash` and `com.github.SoufianoDev.XtCrash:xtcrash-compose`); confirm the exact resolved names from JitPack's artifact list after the first tag. No release tag has been published yet, so nothing ships under the previous packages.
- The public API is unchanged; Java consumers compile against the same classes, getters and builder methods as before.
- AGP 9 built-in Kotlin support compiles the new sources, no extra Kotlin plugin or version pinning added.

# CrashX v7.0.1

CrashX v7.0.1 is a major transparency, maintenance, and modernization release.

## Added

- Clear attribution to CustomActivityOnCrash and Eduard Ereza Martínez.
- NOTICE file with derivative-work note.
- Crash ID generation.
- Optional Crash ID display on the default crash screen.
- Optional CrashX version display on the default crash screen.
- Separate Restart and Close buttons.
- Configurable close button visibility.
- Configurable title, message, and all button text.
- Configurable primary and secondary button colors.
- Configurable title, message, meta, and button text colors.
- Optional crash report/share button.
- Optional support email.
- Optional report subject and chooser title.
- Optional additional report information.
- Optional stack trace inclusion.
- Optional device info inclusion.
- Optional build date inclusion.
- Optional activity lifecycle log inclusion.
- Configurable activity lifecycle log size.
- Configurable stack trace trimming size.
- Robust null-safe config extraction from intents.
- Robust CrashX installation with preserved previous uncaught exception handler.
- Improved background/foreground tracking.
- Improved restart loop protection.
- Library manifest with isolated error activity process.
- Modern sample MainActivity for testing all major features.

## Changed

- Version updated to v7.0.1.
- SharedPreferences file name changed to `crashx`.
- Default error UI redesigned for a cleaner production-ready look.
- Activity log is now a bounded queue.
- Crash reports include CrashX version, crash thread, crash ID, crash date, exception class, and optional metadata.
- Source headers now clearly mention the original project and CrashX maintenance.

## Fixed

- Safer handling when config is missing from the error activity intent.
- Safer provider initialization return value.
- Safer lifecycle activity counter handling.
- Safer fallback when no restart activity is found.
