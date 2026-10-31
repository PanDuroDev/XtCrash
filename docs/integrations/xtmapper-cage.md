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


## Phase 4 — refreshed upstream checkpoint and lifecycle review (2026-10-29)

### Current state refresh

- XtMapper PR #5 is still **open, unmerged, and Draft**, with head `b8f102a7ab1e6202c423c0c021b18053ac02495b` on `merge/upstream-dev-updates`. The latest PR metadata still says the broad changes are not ready to merge until build and runtime checks are complete.
- The PR description's verification paragraph is stale: it says no Gradle build or test suite has run, while CI run [40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818) succeeded for that exact head and ran `:app:testDebugUnitTest` and `:app:assembleDebug`, including native builds for four ABIs. This resolves the compile/JVM/debug-build gate only; it does not replace device, Waydroid, or release/minification tests.
- A later automated-review summary reported that the incremental review selected `RemoteService.kt` but skipped substantive review of it as similar to prior changes. Its zero-comment result should not be interpreted as a full independent approval. The targeted source check says the already-queued start/pause race is fixed by reading `inputService` inside the shared Handler operation; delayed connection callbacks after a stop/pause remain a separate lifecycle case to test.

### Additional source-backed lifecycle finding

At the exact PR head, `TouchPointer.onStartCommand()` sets `isRunning = true`, stores the active instance, and updates the Quick Settings tile before `connectRemoteService()` has succeeded. In `connectRemoteService()`, the `service == null` callback updates the UI with a connection failure and returns without resetting that running state or updating the tile. This can leave the app/tile reporting an active keymapper after remote startup failed.

Source links:
- [TouchPointer.kt at the tested head](https://github.com/SoufianoDev/XtMapper/blob/b8f102a7ab1e6202c423c0c021b18053ac02495b/app/src/main/java/xtr/keymapper/TouchPointer.kt)
- [PR #5](https://github.com/SoufianoDev/XtMapper/pull/5)

This is a **static source finding**, not a runtime reproduction. The safe correction belongs in an authorized XtMapper working fork: model startup as pending until the remote connection/start succeeds, or roll back the running state and tile on every failure path; ensure delayed callbacks cannot resurrect a stopped service; add regression tests for null connection and stop-before-callback. No change was made to the upstream repository.

### Phase 4 decision

**Previous CI gates remain PASS; integration remains BLOCKED.** No artifact has been proven externally resolvable, no consumer Gradle build has been run, no integration-owner XtMapper fork is available, and Android/Waydroid end-to-end behavior remains unverified.

| Gate | Updated status |
|---|---|
| XtCrash Phase 1 and Phase 2 CI | **PASS** — runs 40749398767 and 40755754602 |
| XtCrash Phase 3 documentation commit CI | **NOT CONFIRMED** by the available commit-workflow query; do not infer success from the earlier runs |
| XtMapper standalone test/debug/native build | **PASS** — run 40717364818 at exact head |
| XtMapper PR review coverage | **PARTIAL** — automated incremental review skipped substantive review of one selected file |
| Remote-start failure state and tile | **STATIC FINDING — fix/test needed** |
| Published artifact / external consumer | **NOT VERIFIED / NOT RUN** |
| Cross-project integration and device runtime | **BLOCKED / NOT RUN** |


## Phase 5 — CI closure and next actionable gate (2026-10-29)

### CI result for the Phase 4 audit commit

The documentation commit `f6f7bc0c1d1e93518929590cf6663566193e2e5b` passed [XtCrash workflow run 40780123864](https://github.com/PanDuroDev/XtCrash/actions/runs/40780123864). The `android` job completed with conclusion `success`; setup, Android SDK package installation, pinned Gradle setup, unit tests/module compilation, lint, and consumer-artifact assembly all completed successfully.

This confirms the audit report and the existing XtCrash branch build/test/lint/assembly gate at this commit. It does not retroactively prove the unconfirmed Phase 3 commit's CI, publish a release, or establish cross-project runtime compatibility.

### Rechecked blockers

- No repository named `PanDuroDev/XtMapper` was returned by the repository search. No fork was created and no code was written to `SoufianoDev/XtMapper`.
- Upstream XtMapper PR #5 remains open, unmerged, and Draft at the same tested head `b8f102a7ab1e6202c423c0c021b18053ac02495b`. Its description still contains stale wording that no Gradle build/test has run, despite the successful exact-head run 40717364818.
- The static `TouchPointer.kt` failed-start state/tile issue remains a source finding without runtime reproduction or a fix in an authorized fork.
- XtCrash publication and external consumer resolution remain unverified; no verified dependency coordinate is available to add safely.

### Phase 5 decision

**XtCrash audit branch CI: PASS. Cross-project integration: BLOCKED.** The next meaningful implementation gate is to obtain or create an authorized `PanDuroDev/XtMapper` fork, preserve the exact tested upstream checkpoint, fix and regression-test the startup failure/delayed-callback lifecycle in that fork, and independently verify XtCrash publication through an external Gradle consumer. Do not merge the upstream draft or add guessed artifact coordinates as a substitute for those checks.

| Gate | Status after Phase 5 |
|---|---|
| XtCrash audit branch build/tests/lint/consumer assembly at `f6f7bc0` | **PASS** — [run 40780123864](https://github.com/PanDuroDev/XtCrash/actions/runs/40780123864) |
| XtMapper standalone build/JVM/native gate at `b8f102a` | **PASS** — [run 40717364818](https://github.com/SoufianoDev/XtMapper/actions/runs/40717364818) |
| Integration-owner XtMapper fork | **NOT FOUND** |
| Remote-start failure rollback and delayed-callback regression tests | **NOT FIXED / NOT RUN in an authorized fork** |
| Published XtCrash artifact and external consumer | **NOT VERIFIED** |
| Android device / Waydroid end-to-end | **NOT RUN** |


## Phase 6 — R8 consumer-side verification closure (2026-10-29)

### Verified CI result

The latest R8-focused workflow, [run 40938770467](https://github.com/PanDuroDev/XtCrash/actions/runs/40938770467), completed with **success** on commit `fa0fee61a32c0443050808ec50b64794215682ac`. All four isolated jobs passed:

- `Isolated unit tests`
- `Isolated lint`
- `Isolated consumer assembly`
- `Isolated release packaging`, including `-PverifyR8=true` and release assembly for the core library, Compose module, and sample app

This is evidence that the current source builds with R8 enabled for the final sample application and that the existing unit-test, lint, and consumer-assembly gates pass. The previous R8 failure on `:xtcrash-compose:minifyReleaseWithR8` is no longer reproduced under the corrected configuration.

### Build configuration decision

Android library AARs should preserve the public API for the consuming app's R8 pass. The core library's release build now stays unminified by default; the sample consumer enables minification only in the explicit verification run. The Compose library remains unminified and is shrunk as part of its consumer application. This CI result is a build-level verification, not a device/runtime test or proof that every consumer's custom R8 rules are correct.

### Next gate: publication and external consumer

The GitHub Releases API returned an empty list for both `PanDuroDev/XtCrash` and `SoufianoDev/XtCrash` at the time of this check. No release was created during this audit. The declared Gradle publishing properties (`com.github.SoufianoDev:xtcrash:1.0.0`) are still not proof of a real published artifact. An external Gradle consumer resolving the artifact has not yet been demonstrated, so the coordinate must remain **unverified** and must not be added to XtMapper.

Next, verify the exact tag/ref and JitPack artifact availability, then build a minimal external consumer against the real public artifact without uploading test files or reports. If publication cannot be confirmed, record the precise blocker rather than inventing coordinates. After that, the remaining major gates are an authorized XtMapper integration fork, merged-manifest inspection, device-level crash/privacy behavior, and cage-xtmapper/Waydroid host-browser handoff.

### Phase 6 gate summary

| Gate | Result |
|---|---|
| Unit tests | **PASS** — run 40938770467 |
| Lint | **PASS** — run 40938770467 |
| Consumer module assembly | **PASS** — run 40938770467 |
| R8-enabled release packaging | **PASS** — run 40938770467 |
| GitHub release / published tag | **NOT VERIFIED** |
| External Gradle consumer resolves artifact | **NOT RUN** |
| XtMapper integration / Android runtime / Waydroid E2E | **BLOCKED / NOT RUN** |


## Phase 7 — publication and external-consumer verification (2026-10-29)

### What was verified

- The latest R8-focused build remains green: [run 40938770467](https://github.com/PanDuroDev/XtCrash/actions/runs/40938770467) passed all four jobs, including consumer-side R8 release packaging. The follow-up report commit also passed [run 40940769408](https://github.com/PanDuroDev/XtCrash/actions/runs/40940769408).
- The repository already has a `jitpack.yml` that selects `openjdk21` and runs `./gradlew clean :library:publishToMavenLocal -x test`. This is useful JitPack build configuration, but it is not proof that JitPack actually built or serves the artifact.
- The Gradle wrapper is pinned to Gradle 9.4.1 and the root build uses Android Gradle Plugin 9.2.1. The library module's publishing script declares the artifact as `com.github.SoufianoDev:xtcrash:1.0.0`.
- The public GitHub Releases API was previously checked for both `PanDuroDev/XtCrash` and `SoufianoDev/XtCrash`; both returned empty release lists. The public JitPack page is generic and did not provide a project-specific successful build record in this audit. Attempts to verify the exact POM/build-log endpoints did not return inspectable artifact/build evidence. Therefore this is **not proof the artifact is absent**, but its availability remains unverified.
- There is an ownership/coordinate concern: this working repository is `PanDuroDev/XtCrash`, while the publication metadata and SCM URLs still identify `SoufianoDev/XtCrash`. Do not publish or consume the fork under the upstream owner's namespace without confirming that the upstream owner intends and authorizes that release. A fork's CI success does not establish that the upstream coordinate resolves to this fork's code.

### External consumer result

**NOT VERIFIED — no external Gradle consumer has yet resolved the public AAR and compiled against its API.** The successful local sample-app/CI build validates the checked-out source and consumer-side R8 behavior; it does not validate a remote Maven coordinate. No dependency was added to XtMapper, no tag/release was created, and no test artifacts were uploaded.

### Phase 2 decision

The repository configuration is partly prepared for JitPack, but the actual publication gate remains **BLOCKED / NOT VERIFIED**. The gate can only be closed with both:
1. An authorized, immutable version/ref with unambiguous ownership and coordinates.
2. A clean external Gradle consumer build that downloads the AAR/POM from JitPack, compiles against the public XtCrash API, and (for the minified-consumer case) completes release/R8 assembly.

Until then, do not treat `com.github.SoufianoDev:xtcrash:1.0.0` as a verified dependency and do not add it to XtMapper.

| Phase 2 sub-gate | Result |
|---|---|
| JitPack build configuration present | **PASS — configuration exists; not a remote build result** |
| Repository release list | **NONE LISTED** for the checked fork and upstream |
| Project-specific successful JitPack build / POM / AAR | **NOT VERIFIED** |
| Coordinate ownership is unambiguous | **NO — fork metadata still names upstream owner** |
| External Gradle consumer resolves and compiles remote artifact | **NOT RUN / NOT VERIFIED** |
| XtMapper dependency change | **NOT MADE** |


## Phase 8 — local external-consumer test attempt (2026-10-29)

### Attempt and environment result

I attempted to clone the exact audit branch into a clean local workspace so I could build a separate Gradle consumer against the remote JitPack artifact. The attempt was blocked before project configuration or compilation:

- Java 21 is installed in the execution environment.
- A system Gradle installation is not present.
- `git clone https://github.com/PanDuroDev/XtCrash.git` failed because the environment could not resolve `github.com` (DNS/network access unavailable).
- Direct access to the JitPack artifact/POM/build-log endpoints was also not available through the local execution environment; the web lookup did not expose a project-specific successful build record.

This is an **environment/network blocker**, not a failing XtCrash test. No claim is made that Gradle resolution or the artifact itself failed. No test files, artifacts, tags, or releases were added to the repository.

### CI result for this report update

The preceding report-only commit `7598ed64b7e2e9630a986d472798153f86d2239f` passed [workflow run 40941330644](https://github.com/PanDuroDev/XtCrash/actions/runs/40941330644), with all isolated jobs successful. That run validates the checked-out project tests/build/lint/R8 gates, but it does not substitute for a remote external consumer resolving a published Maven artifact.

### Phase 2 status

**Still BLOCKED / NOT VERIFIED.** A true external consumer test needs a runner with network access and Gradle dependency resolution. It must use a confirmed owner/ref, download the POM/AAR from JitPack, compile against the public API, and assemble a minified consumer release. Until that run succeeds, the published coordinate must not be treated as verified or added to XtMapper.


## Phase 9 — JitPack coordinate mapping review (2026-10-29)

### Additional finding

The JitPack publishing path has a second issue to resolve before the external-consumer gate can pass:

- The root Gradle settings include three modules: `:xtcrash-sample`, `:library`, and `:xtcrash-compose`.
- JitPack's multi-module guidance documents individual module coordinates in the form `com.github.User.Repo:Module:Tag`; its Android publishing guide also requires checking that the selected publication installs to Maven Local.
- The current `:library` publication explicitly sets `groupId = com.github.SoufianoDev`, `artifactId = xtcrash`, and `version = 1.0.0`, while `jitpack.yml` invokes `:library:publishToMavenLocal`.
- This custom coordinate does not follow JitPack's documented default multi-module coordinate pattern. Custom Maven publication coordinates can be valid, but their actual availability through JitPack's resolver must be demonstrated rather than assumed. The current web lookup did not expose a project-specific build row or an inspectable POM/AAR, so this remains a **coordinate-resolution risk**, not a confirmed publication failure.

References:
- JitPack multi-module and build guidance: https://docs.jitpack.io/building/
- JitPack Android publishing checks: https://docs.jitpack.io/android/

### Required next verification

On a network-enabled runner, first inspect the Maven-local output of `:library:publishToMavenLocal` and confirm the exact generated POM path and coordinates. Then use a clean external Gradle project to request that exact dependency from JitPack and verify that the resolved POM/AAR matches the expected source/ref. Do not change the public coordinates or add a dependency to XtMapper until this succeeds and repository ownership is resolved.

### Updated Phase 2 decision

**BLOCKED / NOT VERIFIED.** The source CI remains green, but no remote artifact resolution has been proven. This report update does not add test files, upload artifacts, create a tag/release, or modify XtMapper.


## Phase 10 — Git ref and repository ownership verification (2026-10-30)

### Newly verified GitHub state

The GitHub repository API was checked directly for both repositories:

- `PanDuroDev/XtCrash` exists and is a fork whose parent is `SoufianoDev/XtCrash`.
- `SoufianoDev/XtCrash` is the upstream repository.
- The GitHub Git References API returned an empty list for tag refs in both repositories at the time of this check.
- The GitHub Releases API returned an empty list for both repositories.

This is stronger than checking only the Releases page: neither repository currently exposes a Git tag ref that could identify a versioned JitPack build. It does not prove that no arbitrary JitPack build exists for a commit or branch, but it means the declared `1.0.0` cannot currently be tied to a published Git tag in either repository.

### Consequence for the planned coordinate

The library module still hardcodes:

- Group: `com.github.SoufianoDev`
- Artifact: `xtcrash`
- Version: `1.0.0`
- SCM URL: `https://github.com/SoufianoDev/XtCrash`

The code under audit is in the `PanDuroDev/XtCrash` fork. JitPack's documented multi-module default convention is `com.github.User.Repo:Module:Tag`; this project instead declares a custom Maven publication. That custom publication may be usable, but it must be tested against the exact fork/ref and cannot be inferred from the source configuration.

JitPack's documented build-status API was identified, but the API endpoint and artifact endpoints were not accessible through the available web retrieval environment during this check. No JitPack status or artifact existence is therefore claimed.

### Required decision before publication

The repository owner must choose which repository is the intended publishing source:

1. Publish from `SoufianoDev/XtCrash` only if the upstream owner intends that release and its source is the intended code; or
2. Publish the fork under `PanDuroDev/XtCrash` with matching group/SCM metadata and an immutable ref, after confirming that is the intended ownership.

Do not silently repoint coordinates, create a tag/release, or add the dependency to XtMapper as part of this audit. Once the publishing source and ownership are explicit, run a network-enabled external Gradle consumer against the resulting exact coordinates and verify POM, AAR, public API compilation, and minified release assembly.

### Phase 2 gate

**BLOCKED / NOT VERIFIED.** No tags or GitHub Releases exist in either checked repository; no successful JitPack build or external Gradle consumer resolution has been demonstrated. The successful source CI and R8 consumer checks remain valid, but they do not close publication verification. No test files or artifacts were uploaded, no tag/release was created, and XtMapper was not modified.


## Phase 11 — verification of the latest audit commit (2026-10-30)

### CI result

The phase-10 report commit `cfe79a1fc7a8a51a74475b88a91395fb033dacc9` passed [GitHub Actions run 40992945276](https://github.com/PanDuroDev/XtCrash/actions/runs/40992945276). All four jobs completed successfully:

- Isolated unit tests
- Isolated consumer assembly
- Isolated release packaging, including the R8-enabled sample consumer
- Isolated lint

This confirms that the documentation update did not coincide with a source/build regression, and that the checked source still passes the existing local-source CI matrix. The workflow does not run `:library:publishToMavenLocal`, does not contact JitPack, and does not resolve a dependency from a clean external project. It therefore does not change the publication gate.

### JitPack endpoint attempt

The JitPack build-status API URLs for both the upstream repository and the fork, and the declared POM URL for `com.github.SoufianoDev:xtcrash:1.0.0`, were attempted through the available retrieval environment. The endpoints were inaccessible and returned no inspectable response. This is an access limitation, not evidence that a build or artifact exists or is absent.

### Current exact position

- Source unit tests / lint / debug assembly / R8-enabled sample release packaging: **PASS** on `cfe79a1fc7a8a51a74475b88a91395fb033dacc9`.
- Git tags and GitHub Releases: **none listed** for either fork or upstream at the time checked.
- JitPack build status and POM/AAR: **NOT VERIFIED**.
- External Gradle consumer resolving the public artifact: **NOT RUN**.
- Publication owner and coordinates: **UNRESOLVED** (`PanDuroDev` fork source vs `SoufianoDev` metadata).
- XtMapper dependency change: **NOT MADE**.

**Phase 2 remains BLOCKED / NOT VERIFIED.** The next meaningful technical step is a network-enabled runner that publishes to Maven Local, records the generated POM/AAR coordinates, then tests a clean Android consumer against the actual JitPack artifact after the publishing repository and immutable ref are explicitly selected. The latest CI success does not substitute for that test.


## Phase 12 — remote JitPack consumer attempt and retry (2026-10-31)

### First remote attempt

The corrected fork-commit consumer job ran in workflow run [41079071223](https://github.com/PanDuroDev/XtCrash/actions/runs/41079071223), against commit `51a829ae04202d17683018ddc7d783bb44540122`.

The clean consumer requested:

```
com.github.PanDuroDev.XtCrash:library:51a829a
```

Gradle attempted to retrieve the POM from:

```
https://jitpack.io/com/github/PanDuroDev/XtCrash/library/51a829a/library-51a829a.pom
```

The request ended with `java.net.SocketTimeoutException: Read timed out` after approximately two minutes. The job therefore failed during remote dependency retrieval. This is a network timeout; the available log does **not** establish whether JitPack built the commit successfully, whether the coordinate is invalid, or whether the artifact is absent.

### Retry

The failed JitPack job alone was re-run using GitHub Actions job retry. The retry job ID is `124426724231`. At the time of this report update it was still **in progress**, before reaching the dependency-resolution step. No retry outcome is claimed yet.

### Other jobs in the same workflow run

The following jobs completed successfully:

- Isolated unit tests
- Isolated consumer assembly
- Isolated lint
- Isolated release packaging, including the R8-enabled sample consumer
- Maven-local publication and external consumer smoke test

### Current Phase 2 gate

**BLOCKED / NOT VERIFIED.** Maven Local publication and a clean local-repository consumer are verified. JitPack remote resolution remains unverified because the first attempt timed out and the retry has not completed. The fork coordinate tested here is exploratory and does not validate the declared `com.github.SoufianoDev:xtcrash:1.0.0` coordinate.

No tag or release was created, no artifact was uploaded, no XtMapper dependency was changed, and phases 3 and 4 remain untouched.
