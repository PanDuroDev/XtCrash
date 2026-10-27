#!/usr/bin/env bash
set -euo pipefail

PACKAGE="io.github.soufianodev.xtcrash.sample"
ACTIVITY="${PACKAGE}/io.github.soufianodev.xtcrash.sample.activity.MainActivity"
ERROR_PROCESS="${PACKAGE}:xtcrash_error"
TRIGGER_EXTRA="io.github.soufianodev.xtcrash.sample.TRIGGER_BACKGROUND_CRASH"
EXPECTED_EXCEPTION="XtCrash smoke test: uncaught exception from background thread"
LOG_FILE="${RUNNER_TEMP:-/tmp}/xtcrash-background-crash-logcat.txt"
ACTIVITY_FILE="${RUNNER_TEMP:-/tmp}/xtcrash-activities.txt"

adb install -r xtcrash-sample/build/outputs/apk/debug/xtcrash-sample-debug.apk
adb shell am force-stop "${PACKAGE}"
adb logcat -c

# MainActivity applies the test configuration before onResume starts the worker.
adb shell am start -W -n "${ACTIVITY}" --ez "${TRIGGER_EXTRA}" true

ERROR_ACTIVITY_VISIBLE=false
ERROR_PROCESS_PID=""
for attempt in $(seq 1 40); do
    activity_dump="$(adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' || true)"
    ERROR_PROCESS_PID="$(adb shell pidof "${ERROR_PROCESS}" 2>/dev/null | tr -d '\r' || true)"

    if printf '%s' "${activity_dump}" | grep -q 'DefaultErrorActivity' \
            && [[ -n "${ERROR_PROCESS_PID}" ]]; then
        printf '%s\n' "${activity_dump}" > "${ACTIVITY_FILE}"
        ERROR_ACTIVITY_VISIBLE=true
        break
    fi
    sleep 1
done

adb logcat -d -v brief > "${LOG_FILE}"

if [[ "${ERROR_ACTIVITY_VISIBLE}" != true ]]; then
    echo "FAIL: XtCrash did not leave DefaultErrorActivity running in its dedicated process."
    echo "Activity dump:"
    cat "${ACTIVITY_FILE}" 2>/dev/null || adb shell dumpsys activity activities || true
    echo "Logcat:"
    cat "${LOG_FILE}"
    exit 1
fi

if ! grep -Fq "${EXPECTED_EXCEPTION}" "${LOG_FILE}"; then
    echo "FAIL: the expected worker-thread exception was not logged by XtCrash."
    cat "${LOG_FILE}"
    exit 1
fi

echo "PASS: XtCrash handled an uncaught worker-thread exception."
echo "PASS: DefaultErrorActivity is present and process ${ERROR_PROCESS} is alive (pid ${ERROR_PROCESS_PID})."
