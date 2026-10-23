# Contributing to XtCrash

Thanks for considering a contribution. This document covers the development
setup, the review process, and the conventions this repository follows.

Bug reports, documentation fixes, and pull requests are all welcome under the
[Apache License 2.0](LICENSE).

## Development Environment

| Requirement | Version |
|---|---|
| JDK | 21 |
| Android SDK | `compileSdk 37` |
| Gradle | 9.4.1 (bundled wrapper) |
| AGP | 9.2.1 (Kotlin support built in) |

Set `ANDROID_HOME` in the environment for every Gradle invocation; the checkout
carries no `local.properties`:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
```

## Build and Verify

```bash
# All modules
./gradlew clean :library:assembleDebug :xtcrash-compose:assembleDebug :xtcrash-sample:assembleDebug

# Publish locally to inspect the POM and coordinates
./gradlew :library:publishToMavenLocal :xtcrash-compose:publishToMavenLocal
```

Install `xtcrash-sample` on a device or emulator to exercise the crash
scenarios: test crashes, restart behavior, activity logs, report sharing, and
the enable/disable switch in `MainActivity`.

Run the full module build before opening a pull request. A change that compiles
one module but breaks another is caught by the three-module build.

## Repository Layout

| Path | Purpose |
|---|---|
| `library/` | Core artifact: handler, config, default error screen, intent helpers |
| `xtcrash-compose/` | Optional Compose artifact: `ComposeErrorActivity`, `XtCrashErrorScreen` |
| `xtcrash-sample/` | Demo app (not published) |
| `docs/` | MDX documentation pages |
| `scripts/` | Publish scripts |

## Pull Request Process

1. Create a feature branch from `master`: `feat/<topic>`, `fix/<topic>`, or
   `docs/<topic>`.
2. Keep one concern per pull request. Mixed refactors and feature work are hard
   to review against the diff.
3. Commit in imperative mood (`fix: hold config during process rebuild`).
   Squash-friendly.
4. Open the pull request against `master` and update it by pushing to the
   branch.

### Review

The repository runs two automated review passes:

- **CodeRabbit** reviews every pull request and re-reviews each push. Address
  its inline comments or reply in-thread to explain a decision. Useful
  commands as issue comments on a PR: `@coderabbitai review`,
  `@coderabbitai summary`.
- An inactive **Copilot code review** ruleset additionally requests Copilot
  review per push once a Copilot license is attached to the account.

A human maintainer approves the merge; automated reviews advise and never gate.

## Code Conventions

- Kotlin, idiomatic but Java-callable public API: no Kotlin-only types in
  public signatures of `CrashConfig.Builder` and `CrashActivity` statics.
- Publishable modules: metadata comes from the `PUBLISH_*` block in each
  module's `build.gradle`. Artifact ids (`xtcrash`, `xtcrash-compose`) follow
  the published coordinates, independent of directory names.
- String resources live in the core module; the Compose screen reuses them so
  translations keep applying. Do not duplicate a string into
  `xtcrash-compose`.
- Drawable resource names keep the `crash_` prefix, and
  `customactivityoncrash_*` string keys stay as-is — both are stable names
  carried over from the heritage line.
- Everything handed to the error activity through intent extras must be
  `Serializable`. `EventListener` implementations must be top-level or static
  inner classes.

## Attribution and History

XtCrash is a continuation of
[CrashX](https://github.com/TutorialsAndroid/crashx) and
[CustomActivityOnCrash](https://github.com/Ereza/CustomActivityOnCrash).
Changes must preserve the legacy attributions:

- The `NOTICE` file and the README Attribution section record the
  derivative-work chain.
- Source-file heritage headers that credit the lineage lines are not removed.
- The `CHANGELOG.md` historical section documents earlier versions under the
  original names; it is not rewritten.

## Changelog

Add an entry to the running `CHANGELOG.md` release (Keep a Changelog format:
Added / Changed / Fixed / Breaking). Every user-visible change gets a line in
the pending release section; internal-only changes may skip it.

## Reporting Issues

Include:

1. The **crash ID** as shown on the error screen.
2. Device model, manufacturer, Android version, and API level.
3. The config options you applied.
4. Steps to reproduce; a minimal snippet is best.

Security-sensitive reports go by email instead of the public tracker:
`contact@crashx.dev`.

## Versioning and Releases

- Versions follow Semantic Versioning. The current release line is `1.0.0`
  (no tag published yet).
- Releases are GitHub tags: pushing `vX.Y.Z` triggers a JitPack build.
  Coordinates resolve as `com.github.SoufianoDev.XtCrash:<artifact>:vX.Y.Z`
  once the tag exists; confirm resolved names on the JitPack artifact list
  after tagging.
