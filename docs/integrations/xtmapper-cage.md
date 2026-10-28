# XtMapper / cage-xtmapper integration gate

Status: **not end-to-end verified**. This document is a source-backed integration plan for the fork branch `integration/xtmapper-compatibility-audit`; it does not claim that the three projects are already compatible at runtime.

## Source baselines checked

- XtCrash: upstream master at `656055f202b9f416b881bb6f710138f21bfe4875` (includes report routing and the HTTP-URL warning).
- XtMapper: draft PR #5, head `b8f102a7ab1e6202c423c0c021b18053ac02495b`, “WIP: Reconcile upstream dev with XtLogger integration”.
- cage-xtmapper: `migrate-to-c-golang-no-submodule` at `42271bbb533ee00fb46c9ea9146821a34cf3a4c3`.

The XtMapper PR changes a broad surface area, including app startup, manifest/build configuration, service/session lifecycle, logging, native C/C++, and tests. Its own description says no Gradle build, test suite, Android-device, Waydroid, or native/JNI check has been run. Treat this as an unverified draft baseline, not a stable integration target.

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
5. If the handler returns `ACCEPTED`, stop automatic fallback. `FAILED` and `UNKNOWN` remain explicit outcomes; `UNKNOWN` must never cause an automatic duplicate attempt.

The change does not authorize background uploads or silently submit an issue. The user still confirms the report-bearing web URL before the handler can receive it.

## Current cross-project blockers

### XtMapper

- The checked draft's `app/build.gradle` does not declare XtCrash.
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

1. **Freeze a tested XtMapper commit.** First build and test the large draft independently; record the exact commit and preserve the rollback ref. Do not target an unbuilt moving branch.
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
| XtCrash API and core build | Compile against public API and build core artifact | Source inspected; this branch's code change still needs CI/build verification |
| Privacy confirmation | Handler cannot receive GitHub/GitLab URL before explicit confirmation | Policy and dispatcher updated; unit test added; execution pending |
| XtMapper integration | Dependency, merged manifest, R8, startup/crash regression | Not integrated |
| XtLogger boundary | Existing logging/session behavior preserved; no duplicate pipeline | Source design only |
| Host transport | Real request/ack with request IDs, timeouts and permissions | Not implemented |
| Browser handoff | Exact XtCrash URL opened by host; accurate status only | Not implemented |
| Diagnostics export | Bounded export and redaction/retention checks | Not implemented |
| Full end-to-end | Tested XtCrash → XtMapper → cage-xtmapper on recorded commits | Not verified |
| JitPack release | Tag, successful artifact build, external Gradle consumer | Not verified |

**Release decision:** keep Issue #26 open. The source API is promising and the SDK baseline is compatible, but full compatibility cannot honestly be called 100% until the moving XtMapper draft is stabilized and the real host transport/browser handoff passes the end-to-end checks.
