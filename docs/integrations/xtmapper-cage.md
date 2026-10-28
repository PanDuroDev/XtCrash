# XtMapper / cage-xtmapper integration gate

Status: **not end-to-end verified**. This document is a source-backed integration plan for the fork branch `integration/xtmapper-compatibility-audit`; it does not claim that the three projects are already compatible at runtime.

## Source baselines checked

- XtCrash: upstream master at `656055f202b9f416b881bb6f710138f21bfe4875` (includes report routing and the HTTP-URL warning).
- XtMapper: draft PR #5, head `b8f102a7ab1e6202c423c0c021b18053ac02495b`, “WIP: Reconcile upstream dev with XtLogger integration”.
- cage-xtmapper: `migrate-to-c-golang-no-submodule` at `42271bbb533ee00fb46c9ea9146821a34cf3a4c3`.

The XtMapper PR changes a broad surface area, including app startup, manifest/build configuration, service/session lifecycle, logging, native C/C++, and tests. Its PR description has not been kept in sync with CI: **the exact head commit `b8f102a7ab1e6202c423c0c021b18053ac02495b` did pass GitHub Actions run [40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818)** on 2026-10-28. The workflow ran `:app:testDebugUnitTest` and `:app:assembleDebug`; the log confirms NDK builds for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, and a debug APK artifact was uploaded. This closes the first compile/JVM-test gate for that exact commit, but it does **not** establish Android-device or Waydroid runtime correctness, and the PR remains a Draft. Treat the commit as a *CI-tested checkpoint*, not as a fully validated integration target.

## Confirmed XtCrash contract

The core module exposes the pieces an integration needs:

- `CrashConfig.Builder` can select `ReportDestination.GITHUB` or `GITLAB`, configure an explicit issue target, and register a class-based `ReportActionHandler`.
- `PreparedReport` exposes the canonical `reportText`, a per-request `requestId`, the crash ID, selected destination, and XtCrash-generated destination `url`.
- `ReportOutcome.ACCEPTED` means durable acceptance by the integration only. It does not mean a browser opened, an issue was created, or a report was submitted.
- The handler is instantiated in `:xtcrash_error`; it must have a public no-argument constructor and must not depend on in-memory objects from the app process.
- Core XtCrash supports API 23+, while the current XtMapper draft declares minSdk 25, so the minimum SDKs are compatible. The optional Compose artifact is not needed for the XML/default error screen.

**Do not rebuild the issue URL in XtMapper.** Use the exact `PreparedReport.url` supplied by XtCrash. Configure the destination owner/project explicitly; never infer it from the Android package name.

## Safety fix in this branch

Before this branch, `ReportDispatcher` invoked a configured report-action handler before displaying the URL privacy confirmation. A handler could therefore receive a report-bearing GitHub/GitLab URL before the user saw the title/description disclosure. This branch changes the web-destination flow to:

1. Prepare the canonical report and the exact URL in XtCrash.
2. Show the existing privacy confirmation.
3. Invoke the configured handler only after the user confirms.
4. If the handler returns `NOT_HANDLED`, open the already-confirmed URL without asking the same confirmation twice.
5. If the handler returns `FAILED` or `UNKNOWN`, an explicit later “Open issue page” fallback gets its own confirmation and opens the URL directly; it must not invoke the handler a second time. `UNKNOWN` never triggers an automatic duplicate attempt.
6. If the handler returns `ACCEPTED`, stop automatic fallback.

The change does not authorize background uploads or silently submit an issue. The user still confirms the report-bearing web URL before the handler can receive it.

## Current cross-project blockers

### XtMapper

- The checked draft's `app/build.gradle` at `b8f102a7ab1e6202c423c0c021b18053ac02495b` does not declare XtCrash. Its `minSdk 25` remains compatible with XtCrash core's API 23+ baseline, but this is only a platform-floor comparison, not integration proof.
- Its manifest does not yet show the merged XtCrash provider/error activity, because the dependency is not integrated.
- The current `App.onCreate()` initializes XtLogger; XtCrash configuration must be installed early enough for the provider/error process lifecycle. Verify the chosen initialization point with the actual merged manifest and a forced-crash test.
- The crash-reporting design document in that draft still describes report routing as unimplemented and was audited against an older XtCrash commit. Update it only after this branch's API and tests are verified.
- Preserve XtLogger as the application logging subsystem. XtCrash's `CrashLogListener` may forward diagnostics, but must not create a second competing persistent log pipeline.

### cage-xtmapper

- The checked launcher starts `RemoteServiceShell` and forwards the compositor stdout stream to the Android process. No production crash-report request queue, request ID correlation, acknowledgment, or browser handoff was found in the inspected source.
- The intended Android-to-host request/ack protocol is therefore a proof of concept, not an implemented feature. Do not send protocol messages through the compositor data stream unless its framing and separation from input events are proven.
- Host-side code must validate message type/size, avoid shell interpolation, apply bounded timeouts, and report browser handoff separately from issue submission or page load.
- Diagnostic export and URL handoff must have independent statuses. Exported logs need size limits and a review/redaction policy for sensitive data.

## Required integration sequence

1. **Freeze a tested XtMapper commit.** Compile and JVM-test the large draft independently; record the exact commit and preserve the rollback ref. The compile/JVM gate now passes for `b8f102a7ab1e6202c423c0c021b18053ac02495b` in [run 40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818), and the existing rollback ref is named in PR #5. Before integration, verify that this remains the intended head and preserve a tested checkpoint in the integration owner's own fork. Do not target an unbuilt moving branch.
2. **Compile-time integration.** Add only the core XtCrash artifact to a dedicated integration branch of XtMapper; verify the actual artifact coordinates, merged manifest, R8 rules, and compilation against the public API. The planned `v1.0.0` coordinate is not usable until a tag, JitPack build, and external consumer are verified.
3. **Android crash path.** Configure XtCrash before its initialization provider runs where required. Test a deliberate uncaught exception in the main process and confirm the error UI appears in `:xtcrash_error`. Verify that normal no-handler behavior and user-driven fallback remain unchanged.
4. **Handler boundary.** Add a public no-argument handler class in XtMapper. Test all outcomes (`ACCEPTED`, `NOT_HANDLED`, `FAILED`, `UNKNOWN`), timeout, process recreation, and R8/minification. Confirm that a GitHub/GitLab handler is not invoked before the URL privacy confirmation.
5. **Host transport proof of concept.** Agree on a versioned message schema and discovery mechanism before coding. Demonstrate request creation, host receipt, acknowledgment, correlation, timeout/interruption, permissions, and bounded payload handling on a real Waydroid setup.
6. **Browser handoff.** Have the host open only the exact URL supplied by XtCrash, using a safe process API and no shell-built command. Report only “handed to default browser” when that action is observed; never claim an issue was created.
7. **Diagnostics.** Add XtLogger export only after explicit policy and retention rules are agreed. Keep export failure independent from URL handoff and redact secrets/personal data.
8. **Release gate.** Run the full XtCrash test/build matrix, XtMapper build and JVM tests, native build, emulator crash test, and real Waydroid end-to-end test. Record exact commits and outputs for all three projects before publishing XtCrash.

## Acceptance matrix

| Area | Evidence required | Current status |
|---|---|---|
| XtCrash API and core build | Compile against public API and build core artifact | **PASS** on fork commit `7023a85d8d420448a9350e3d92692b6e0d3e0401`: `:library:compileDebugKotlin`, `:xtcrash-compose:compileDebugKotlin`, `:library:assembleDebug`, `:xtcrash-compose:assembleDebug`, and `:xtcrash-sample:assembleDebug` succeeded in [CI run 40735898417](https://github.com/PanDuroDev/XtCrash/actions/runs/40735898417) |
| Privacy confirmation | Handler cannot receive GitHub/GitLab URL before explicit confirmation | Policy/state unit tests and build/lint **PASS** in CI run 40735898417; no Android UI/device-level consent test yet |
| XtMapper standalone build/JVM tests | `:app:testDebugUnitTest` + `:app:assembleDebug`, including NDK builds for four ABIs | **PASS** on exact PR head `b8f102a7ab1e6202c423c0c021b18053ac02495b` in [CI run 40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818); debug APK artifact uploaded |
| XtMapper integration | Dependency, merged manifest, R8, startup/crash regression | Not integrated |
| XtLogger boundary | Existing logging/session behavior preserved; no duplicate pipeline | Source design only |
| Host transport | Real request/ack with request IDs, timeouts and permissions | Not implemented |
| Browser handoff | Exact XtCrash URL opened by host; accurate status only | Not implemented |
| Diagnostics export | Bounded export and redaction/retention checks | Not implemented |
| Full end-to-end | Tested XtCrash → XtMapper → cage-xtmapper on recorded commits | Not verified |
| JitPack release | Tag, successful artifact build, external Gradle consumer | Not verified |

## Latest verification evidence

- **XtMapper phase 1 compile/JVM gate:** [CI run 40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818) completed successfully on exact PR head `b8f102a7ab1e6202c423c0c021b18053ac02495b`. The job ran `:app:testDebugUnitTest` and `:app:assembleDebug`; logs show successful NDK build tasks for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Artifact [xtmapper-debug-6d4e676f1ae1b7f9ee8773d4e593a4d35cffbe2f](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818/artifacts/12827954007) was uploaded (expires 2026-11-04). The run used JDK 21 and Gradle 9.5.1. It emitted existing deprecation warnings, but ended `BUILD SUCCESSFUL`.
- This evidence is from `SoufianoDev/XtMapper` PR #5, not from a `PanDuroDev/XtMapper` fork. The latter repository path returned 404 when checked, so no branch or code was written there. Do not treat this as a checkpoint committed into the integration owner's own XtMapper fork.
- Fork branch commit tested: `7023a85d8d420448a9350e3d92692b6e0d3e0401`.
- GitHub Actions run [40735898417](https://github.com/PanDuroDev/XtCrash/actions/runs/40735898417) completed successfully on 2026-10-28. Both the unit-test/compile step and lint/assemble step passed. Gradle reported deprecation warnings for features incompatible with Gradle 10; these warnings did not fail the run.
- The documentation-only follow-up commit `f713b86b275c3b6df12925d268db862681966b6d` was also checked by [CI run 40739156373](https://github.com/PanDuroDev/XtCrash/actions/runs/40739156373), which completed successfully. The unit-test/compile, lint, and assembly steps all passed on that commit.
- The current branch contains regression tests for the privacy-confirmation contract: GitLab.com and self-hosted GitLab previews (including explicit ports), title/body values decoded from the final prepared URL, shortened prefill behavior, plaintext-HTTP warnings including bare URLs, and truthful clipboard/manual-paste guidance. These tests are covered by `:library:testDebugUnitTest` in the successful CI run.
- This validates the current XtCrash branch's compilation, listed unit tests, lint task, and debug assembly only. It does **not** validate runtime UI behavior, Android device behavior, the XtMapper draft build, native/JNI, Waydroid, or host/browser transport.

**Release decision:** keep Issue #26 open. The source API is promising and the SDK baseline is compatible, but full compatibility cannot honestly be called 100% until the moving XtMapper draft is stabilized and the real host transport/browser handoff passes the end-to-end checks.


## Phase 2 — compile-time integration readiness audit (2026-10-28)

### Latest documentation CI result

The documentation update at commit `8a0e54c241efef7fa5d709b6b3fd32453d094dd6` completed successfully in [XtCrash CI run 40749398767](https://github.com/PanDuroDev/XtCrash/actions/runs/40749398767). The workflow's unit-test/compile and lint/consumer-assembly steps passed. This validates the XtCrash branch at that commit; it does not validate cross-project runtime integration.

### Additional static checks against XtMapper PR #5 head

The following source files were re-read at the exact tested head `b8f102a7ab1e6202c423c0c021b18053ac02495b`:

- `app/build.gradle`: no XtCrash dependency is declared. The app uses `minSdk 25`, Java 21 source/target compatibility, core-library desugaring, and R8/minification for release. The build config alone does not establish that XtCrash's release artifact or consumer rules work under minification.
- `app/src/main/AndroidManifest.xml`: no merged XtCrash components can be present from a dependency that is not declared. The manifest currently declares the app's `.App` application class and the existing app services/activities.
- `app/src/main/java/xtr/keymapper/App.kt`: `Application.onCreate()` initializes `XtLogger` and logs startup. It does not configure XtCrash. Keep this initialization behavior intact when designing the integration.
- `settings.gradle`: JitPack is listed as a dependency repository, but that is not evidence that the planned XtCrash coordinates resolve, that a release tag exists, or that an external consumer can compile.

### Phase 2 decision

**Status: BLOCKED before code integration; no XtMapper source changed.** The next safe implementation step is not to guess dependency coordinates or add an unverified provider. First verify the published artifact coordinates and consumer rules, then create an isolated integration branch in the integration owner's XtMapper fork. On that branch, add only the XtCrash core dependency, inspect the merged manifest, compile debug and minified release, and add a consumer-level test. The upstream PR remains a draft and must not be treated as the integration workspace.

The search for `PanDuroDev/XtMapper` still returned no repository result. Until a reachable fork is available, the only safe writable integration record remains this XtCrash report; no code or branch has been written to `SoufianoDev/XtMapper`.

### Updated gate summary

| Gate | Result |
|---|---|
| XtCrash branch tests, lint, consumer assembly at `8a0e54c` | **PASS** — CI run 40749398767 |
| XtMapper standalone JVM tests and debug/native build at `b8f102a` | **PASS** — CI run 40717364818 |
| XtCrash dependency coordinates resolved by an external consumer | **NOT VERIFIED** |
| XtMapper merged manifest and R8/minified release with XtCrash | **NOT RUN** |
| Android device crash UI and privacy flow | **NOT RUN** |
| cage-xtmapper request/ack and browser handoff | **NOT IMPLEMENTED / NOT VERIFIED** |


## Phase 3 — publication and consumer-readiness gate (2026-10-28)

### Re-check of prior phases

- Phase 1 XtCrash build/test/lint/consumer assembly passed in [run 40749398767](https://github.com/PanDuroDev/XtCrash/actions/runs/40749398767).
- Phase 2 documentation commit `cd15520e7c516f3e574483ef8b70e6b970150d32` passed all workflow steps in [run 40755754602](https://github.com/PanDuroDev/XtCrash/actions/runs/40755754602). The Android job completed with conclusion `success`; unit-test/compile and lint/consumer-assembly steps both passed.
- XtMapper's standalone CI checkpoint remains green at [run 40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818), but it is still upstream PR #5, not an integration-owner fork.

### Publication evidence

- The XtCrash fork's GitHub Releases page currently returns an empty release list: [PanDuroDev/XtCrash/releases](https://github.com/PanDuroDev/XtCrash/releases).
- The library build configuration declares `PUBLISH_GROUP_ID = com.github.SoufianoDev`, `PUBLISH_ARTIFACT_ID = xtcrash`, and `PUBLISH_VERSION = 1.0.0`. These are build-time publishing properties only; they do not prove that a corresponding tag or JitPack artifact exists.
- JitPack's documented flow requires a buildable tag/commit and an actual successful artifact build before a consumer can rely on a coordinate. See [JitPack build guidance](https://docs.jitpack.io/building/) and [Android dependency instructions](https://docs.jitpack.io/android/). An externally resolved Gradle consumer has not yet been demonstrated for this project, so `com.github.SoufianoDev:xtcrash:1.0.0` must not be copied into XtMapper as a verified dependency.
- No release or tag was created by this audit. No files were changed in upstream `SoufianoDev/XtMapper`.

### Phase 3 decision

**Publication gate: BLOCKED / NOT VERIFIED.** The safe next step is to establish the intended release source and owner, verify a real tag and JitPack build, and run an isolated consumer Gradle build that resolves the AAR and compiles against the public API. Only after that should a dependency be added to an integration branch in the integration owner's XtMapper fork. That fork is still unavailable at `PanDuroDev/XtMapper`, so cross-project code integration remains blocked.

| Gate | Current result |
|---|---|
| Prior XtCrash CI phases | **PASS** — runs 40749398767 and 40755754602 |
| XtMapper standalone compile/JVM/native CI | **PASS** — run 40717364818, upstream draft only |
| GitHub release for this XtCrash fork | **NONE LISTED** |
| Published tag + successful JitPack artifact | **NOT VERIFIED** |
| External consumer resolves and compiles AAR | **NOT RUN** |
| XtMapper integration branch in owner fork | **BLOCKED — fork unavailable** |
| Android runtime, privacy UI, Waydroid host/browser path | **NOT RUN / NOT IMPLEMENTED** |
