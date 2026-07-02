# Product Finish Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish OPlus OTA Studio by proving the real OTA evidence path first, then cutting a private-trial release with only proven claims.

**Architecture:** Treat the backend implementation as v0.1-complete unless a real device run exposes a production bug. The remaining work is evidence collection, private-trial packaging, and final claim cleanup; each change must be small enough to revert independently.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, DataStore, OkHttp, MockWebServer, Android device/emulator testing, Gradle release builds.

---

## Scope Decision

The active completion path is no longer "add more backend safeguards." The app
already has backend wiring for lookup, download, resume, checksum verification,
storage promotion, logging, diagnostics, and release smoke evidence. Work now
counts only if it makes one of these product outcomes more true:

1. A real OnePlus/OPlus profile can produce a captured, replayed, or live OTA
   lookup result.
2. A private tester can install the app, run lookup/download flows, and see
   limitations stated honestly.
3. The repository records enough evidence that release claims are auditable.

Do not add new protocol branches, retry logic, parser tolerance, or UI copy
unless a command in this plan exposes a concrete product gap.

## Files And Responsibilities

- `docs/progress.md`: current product status, blockers, and next action.
- `docs/evidence/ota/`: timestamped OTA lookup evidence attempts and blockers.
- `docs/evidence/release/`: release build, signing, install, launch evidence.
- `core-ota/src/test/resources/fixtures/`: only redacted real/replayed OTA
  response fixtures; synthetic fixtures stay clearly labeled.
- `docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`: flip
  uncertain protocol claims only after real evidence proves them.

## Task 1: Finish The OTA Evidence Gate

**Files:**
- Modify: `docs/progress.md`
- Create or modify: `docs/evidence/ota/`
- Modify only on success: `core-ota/src/test/resources/fixtures/`
- Modify only on success: `docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`

- [x] **Step 1: Check for a physical device**

Run:

```powershell
adb devices
```

Expected for success: at least one attached `device` row that is not an
emulator. If the list is empty, record the blocker in `docs/evidence/ota/` and
skip Steps 2-5 for this pass.

- [x] **Step 2: Collect device facts from the attached device**

Run:

```powershell
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.ota
adb shell getprop ro.oppo.region
adb shell getprop ro.build.display.id
```

Expected for success: model, device/codename, and build string are enough to
construct or confirm an `OtaProfile`. Redact serials, IMEIs, account IDs, and
signed URLs before committing any evidence.

- [x] **Step 3: Run lookup from the app or existing backend path**

Use the app UI first if the device is attached and debuggable. If UI operation
is impractical, use the existing `LegacyOtaProtocol` request shape:

```powershell
$otaVersion = (adb shell getprop ro.build.version.ota).Trim()
if ([string]::IsNullOrWhiteSpace($otaVersion)) {
  $otaVersion = (adb shell getprop ro.build.display.id).Trim()
}
$deviceCodename = (adb shell getprop ro.product.device).Trim()
if ([string]::IsNullOrWhiteSpace($deviceCodename)) {
  $deviceCodename = "OnePlus9Pro_CH"
}
curl.exe -i -sS --connect-timeout 20 --max-time 45 `
  -X POST 'https://otacn.oppo.com/OnePlusOTA/OnePlus_OTA.php' `
  -H 'Content-Type: application/x-www-form-urlencoded' `
  --data-urlencode 'systemType=Color OS' `
  --data-urlencode "otaVersion=$otaVersion" `
  --data-urlencode 'mode=full' `
  --data-urlencode "device=$deviceCodename"
```

Expected for success: HTTP completes and returns either a parseable package
response or a parseable no-update response. A TLS handshake failure from the PC
is a blocker, not a backend bug.

- [ ] **Step 4: Commit a real or replayed fixture only after success**

If Step 3 returns a parseable package response, create a fixture named like:

```text
core-ota/src/test/resources/fixtures/replayed-real-profile-oneplus9pro-cn-success.xml
```

Then add a parser test that reads that fixture and asserts the returned
`OtaPackage` fields exactly match the redacted response. Run:

```powershell
.\gradlew.bat :core-ota:testDebugUnitTest --tests "*LegacyOtaProtocolTest"
```

Expected for success: the focused parser test passes.

Current note: the 2026-07-02 successful phone-side ColorOS replay is recorded
as command/test evidence rather than a raw response fixture because the live
response carries signed CDN package URLs. Keep this unchecked until a redacted
fixture can be committed safely.

- [x] **Step 5: Update claims only to the evidence level proven**

If Step 4 succeeds, update `docs/progress.md` and only the proven rows in the
spec. Keep the UI experimental unless the evidence is `live-verified`.

Verification:

```powershell
.\gradlew.bat :core-ota:testDebugUnitTest
.\gradlew.bat test lintDebug :app:compileDebugAndroidTestKotlin
```

Commit plan:

```powershell
git add core-ota/src/test/resources/fixtures/replayed-real-profile-oneplus9pro-cn-success.xml core-ota/src/test/kotlin/dev/shallowdusty/oplusotastudio/core/ota/LegacyOtaProtocolTest.kt
git commit -m "test: add replayed OTA lookup fixture"
git add docs/progress.md docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md docs/evidence/ota/live-replay-attempt-2026-07-02.txt
git commit -m "docs: record OTA lookup evidence"
```

## Task 2: Cut The Private-Trial Build

**Files:**
- Modify: `docs/progress.md`
- Create or modify: `docs/evidence/release/`

- [ ] **Step 1: Build release APK**

Run:

```powershell
.\gradlew.bat :app:assembleRelease
```

Expected for success: release APK builds and R8 runs without errors.

- [ ] **Step 2: Sign and verify with local throwaway values**

Use the workflow in `docs/release.md`, then run:

```powershell
& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Expected for success: `Verifies` is true and the certificate output is recorded
in `docs/evidence/release/`.

- [ ] **Step 3: Install and launch on the best available target**

Prefer a physical OPlus device. If unavailable, use the newest emulator and
state that it is emulator-only evidence.

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n dev.shallowdusty.oplusotastudio/.MainActivity
adb logcat -d | Select-String -Pattern 'AndroidRuntime|FATAL EXCEPTION|dev.shallowdusty.oplusotastudio'
```

Expected for success: install succeeds, launch starts `.MainActivity`, and
there is no app-process fatal crash.

Verification:

```powershell
.\gradlew.bat test lintDebug :app:compileDebugAndroidTestKotlin
```

Commit plan:

```powershell
git add docs/progress.md docs/evidence/release/r8-release-api34-smoke-2026-07-02.txt
git commit -m "docs: record private-trial release evidence"
```

## Task 3: Final Claim Audit

**Files:**
- Modify: `docs/progress.md`
- Modify only if claims change: `README.md`
- Modify only if claims change: `docs/release.md`

- [ ] **Step 1: Audit v0.1 acceptance criteria**

Compare `docs/progress.md` against the v0.1 checklist in
`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`.

Expected for success: every v0.1 item is either proven by an evidence file or
explicitly called out as a private-trial limitation.

- [ ] **Step 2: Remove overclaims**

Search:

```powershell
rg -n "signature|official|live-verified|release ready|public release|verified" README.md docs app/src/main/res
```

Expected for success: no user-facing copy implies OPlus affiliation, OTA
signature verification, or public release readiness.

- [ ] **Step 3: Run the final local gate**

Run:

```powershell
.\gradlew.bat test lintDebug :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleRelease
```

Expected for success: both commands pass.

Commit plan:

```powershell
git add docs/progress.md README.md docs/release.md
git commit -m "docs: finalize private-trial release claims"
```

## Stop Rules

- If no physical device is attached, stop at evidence recording for Task 1 and
  do not expand backend code.
- If PC-side replay fails with TLS handshake errors, record it as an endpoint
  blocker and do not add retries, user agents, or parser changes.
- If a real device run returns a malformed response, preserve the redacted raw
  shape in `docs/evidence/ota/` before changing parsing behavior.
- If release smoke fails, fix the concrete failing app behavior before updating
  docs.

## Current 2026-07-02 Status

- Task 1 is no longer blocked: a physical OnePlus 9 Pro CN device was attached
  and the guarded app instrumentation path returned the expected package from
  the live ColorOS component endpoint. Evidence:
  `docs/evidence/ota/live-coloros-oneplus9pro-cn-2026-07-02.txt`.
- PC-side replay to `otacn.oppo.com/OnePlusOTA/OnePlus_OTA.php` still fails
  during TLS handshake from both Windows curl and WSL curl; that path is stale
  for the supported OnePlus 9 Pro CN ColorOS chain.
- Task 2 has recent API 34 emulator evidence, but should be rerun after any
  further code change.
- Task 3 remains open for final private-trial claim cleanup.
