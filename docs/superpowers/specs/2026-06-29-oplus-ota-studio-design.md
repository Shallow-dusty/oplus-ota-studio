# OPlus OTA Studio Design and Delivery Plan

## Goal

Build a new mobile-first OTA utility for OPlus/OnePlus devices. The first version runs entirely on the phone and covers device detection, OTA profile setup, package lookup, download, resume, verification, and clear status feedback. A later PC companion can use ADB to automate setup and export logs, but it is not part of the first build.

This document is both the product design and the delivery source of truth. If a requirement below cannot be implemented, tested, or evidenced in a small revertable commit, split it into a smaller milestone before coding.

## Product Boundary

This is a rewrite, not a fork UI refresh. The upstream project can be used only as behavioral reference for public OTA request patterns. Source structure, UI, storage, download handling, and interaction logic should be original.

Version 1 focuses on the phone because OTA CDN access and device network context are more reliable there than on the PC. The app should not require root for normal lookup and download flows. Root-related workflows, boot image patching, and PC-side ADB automation are future phases.

## Recommended Architecture

Use a native Android app with Kotlin, Jetpack Compose, Material 3, coroutines, WorkManager, Room, DataStore, and OkHttp.

The app is split into focused modules:

- `app`: Android entry point, navigation, theme, permission prompts, and dependency wiring.
- `core-model`: immutable domain models for devices, OTA profiles, packages, download state, and verification results.
- `core-ota`: OTA request construction, response parsing, region/build validation, and stable error mapping.
- `core-download`: streaming download engine, range resume, temporary file handling, checksum calculation, and final file promotion.
- `core-storage`: Room entities for history and download state, plus DataStore preferences for user choices.
- `feature-lookup`: device/profile screen and OTA query result screen.
- `feature-downloads`: download queue, active progress surface, file actions, and verification details.

Keep the domain layer Android-light where practical so the OTA parser, download state machine, and checksum logic can be unit-tested without an emulator.

## Delivery Rules

### Evidence Levels

Protocol and device claims must be labeled with the strongest evidence currently available:

- **live-verified**: the app or a test harness successfully queried a current OPlus/OnePlus endpoint from a real device/network profile.
- **captured-real**: a real device request/response was captured from device or system-client traffic and redacted into a committed fixture.
- **replayed-real-profile**: a locally constructed request using real device facts returned a plausible response, but capture parity is not fully proven.
- **synthetic**: fixture or behavior was generated from the known schema to exercise code paths; useful for tests, not proof of live server behavior.

v0.1 may ship on `captured-real` / `replayed-real-profile` parser evidence if live endpoint access is blocked, but the app UI must disclose that live lookup support is experimental until at least one `live-verified` chain exists.

### Execution Shape

The project should be implemented in small, rollback-safe slices:

1. Bootstrap the repo and Android scaffold.
2. Land domain models and pure JVM tests before Android UI.
3. Land protocol fixtures and parser tests before live networking UI.
4. Land the download state machine before WorkManager and notifications.
5. Land storage promotion tests before exposing final "save to Downloads" actions.
6. Land UI screens after their state models are test-covered.

Each slice should compile and test independently. Do not batch unrelated scaffold, feature, and documentation changes into one commit; follow `AGENTS.md` for staging and commit granularity.

## 1. OTA Protocol Contract

> **Verification status convention.** This section is a *reference contract* distilled from publicly observable OPlus/OnePlus OTA behavior. Field names and paths marked ✅ are well-established from public research; those marked ❓ must be confirmed by on-device packet capture during implementation week 1 before being relied on. Treat the contract as the code/test skeleton, not as ground truth until verified.

### 1.1 Host / Region Matrix

OPlus routes OTA queries to region-specific CDN front-ends. Non-root devices reach these over HTTPS. Known host families (verify current resolution before pinning):

| Region | Host (❓ verify) | Notes |
|---|---|---|
| Global / generic | `otagm.oppo.com` | Common fallback for non-CN builds |
| India | `otadiu.oppo.com` | Distinct pool for IN region |
| International | `otai.oppo.com` | EU/SEA mixed |
| China | `otacn.oppo.com` (❓) | CN builds; may differ for OnePlus vs OPPO |
| OnePlus legacy | `ota*.oneplus.cn` (❓ deprecated) | Pre-merge OxygenOS hosts; may redirect |

The app should resolve the target host from the detected region, never hard-code a single endpoint. For v1, maintain the mapping as a built-in versioned data file in `core-ota`, expose an advanced manual host override, and allow later JSON import/export for updated host maps. Do not introduce a remote configuration service in v1.

### 1.2 Request Contract (reference)

OPlus services have shipped both a legacy XML/form style (OnePlus lineage) and a newer JSON style (ColorOS lineage). The architecture must support multiple protocol strategies, but v0.1 only has to ship one enabled strategy backed by `captured-real`, `replayed-real-profile`, or `live-verified` evidence. Prefer the OnePlus 9 Pro CN / ColorOS chain we already have access to; keep the other style as a disabled, testable strategy stub until captures or replay data prove the contract.

**Style A — OnePlus XML/form (legacy OxygenOS):**

- Method: `POST`
- Path: ❓ `/OnePlusOTA/OnePlus_OTA.php` (legacy; confirm)
- Headers: `Content-Type: application/x-www-form-urlencoded`
- Body fields:
  - `systemType` ✅ — `"Oxygen OS"` / `"Color OS"`
  - `otaVersion` ✅ — full build string, e.g. `11.0.2.2.LE28AA`
  - `mode` ❓ — `"full"` vs incremental
  - `device` ✅ — model codename
  - `serialNumber` ❓ — device serial (PII; see §10 Privacy and Compliance)
- Response: XML
  - `<Command>` ✅ — `NEW_VERSION` / `NO_NEW_VERSION`
  - `<versionName>` ✅, `<size>` ✅, `<md5>` ✅, `<url>` ✅, `<type>` ❓

**Style B — OPlus JSON (ColorOS):**

- Method: `POST` ✅
- Verified CN endpoint: `https://component-otapc-cn.allawntech.com/update/v3` ✅
- Headers: `Content-Type: application/json`, `version: 2`,
  `protectedKey`, plus model/build/region/language/device hint headers ✅
- Body shape: JSON root with encrypted `params`; decrypted payload includes
  `model`, `productName`, `romVersion`, `otaVersion`, `androidVersion`,
  `colorOSVersion`, `uRegion`, `trackRegion`, `deviceId`, and `otaPrefix` ✅
  for the OnePlus 9 Pro CN replay profile. `nvCarrier` is sent as a request
  header in the verified chain.
- Response shape: JSON root with `responseCode` and encrypted `body`;
  decrypted body contains `components[0].componentPackets.url`,
  `components[0].componentPackets.size`,
  `components[0].componentPackets.md5`, and root `versionName` ✅ for the
  OnePlus 9 Pro CN replay profile.

### 1.3 Response Model

`core-ota` must parse both styles into one canonical domain model:

```kotlin
sealed interface OtaLookupResult {
    data class PackageFound(val pkg: OtaPackage) : OtaLookupResult
    data object NoUpdate : OtaLookupResult
    data class Error(val category: OtaErrorCategory, val raw: String?) : OtaLookupResult
}
```

`OtaPackage` carries `versionName`, `type`, `sizeBytes`, `sourceHost`, `downloadUrl`, `md5?`, `sha256?`, `releaseNotes?`.

### 1.4 Authentication / Signature

- No documented static API token is known for the public OTA path. The server historically keys on model + build + region.
- ❓ Confirm whether any header (e.g. `User-Agent` OEM string, `X-OPT-*`) is required by capturing a real request. If a custom UA is needed, send a neutral app UA plus the device's real build string — do not impersonate the system OTA client beyond what is required to get a valid response.

### 1.5 Week-1 Verification Protocol

Before enabling a `core-ota` protocol strategy in the app, establish at least one ground-truth chain:

1. Collect local device facts with `adb shell getprop`, Android `Build.*`, and the current OTA/build strings. **Provenance note:** `getprop` returns system-level properties the app cannot read at runtime (hidden API restriction, see §2.1); use it only as ground truth for fixture construction, never as app input. Fixtures must be built from what `Build.*` + best-effort `ro.*` actually return in-app, otherwise parser tests pass against inputs the production app can never produce.
2. Replay the known public OTA request shape against the live endpoint from a test harness or the app, using the real device profile. If the replay returns an unexpected schema or the request shape itself is wrong, fall back to MITM capture (`mitmproxy`/`HttpToolkit`) or manually constructing the request from documented field semantics.
3. Record one successful package-found response for the first supported chain as ground truth. If live replay is blocked but a real captured response exists, v0.1 may continue with fixture-backed lookup and must track live endpoint verification as a v0.2 blocker. No-update and error fixtures are synthetic (derived from the observed schema) and must be labeled `synthetic-*` in the fixture directory — parser branches exercised only against synthetic fixtures are not proven against real server behavior.
4. Scrub any IMEI/serial from captured or replayed requests before committing fixtures (see §10).
5. Commit redacted fixtures under `core-ota/src/test/resources/fixtures/` as canonical parser inputs. Filenames must encode provenance: `captured-real-<chain>-success.*`, `replayed-real-profile-<chain>-success.*`, `synthetic-<chain>-noupdate.*`, etc.
6. Update §1.1/§1.2 host and field tables in this spec from the verified chain, flipping ❓ → ✅ only for fields proven by a real response.

MITM with `mitmproxy`/`HttpToolkit`, a system CA, or root-level packet tracing is optional evidence, not a v0.1 blocker. Modern Android system clients may reject user CAs or use tighter trust policy, so implementation must not depend on MITM access — but it remains the fallback when step 2 replay fails.

## 2. Device Detection Signals

The lookup is only as good as the profile it sends. Detection must run on first launch and produce a best-effort profile, then offer manual override.

### 2.1 Signal Inventory

Use `Build.*` constants as the stable no-root baseline. `android.os.SystemProperties` reflection and shell-style `getprop` keys are best-effort providers because hidden API policy can vary by Android version and vendor build. Wrap every property source behind a single `DevicePropertyProvider` so failures can be faked in tests and never crash the app.

| Field | Source | Root needed | Notes |
|---|---|---|---|
| Model name / codename | `Build.MODEL`, `Build.PRODUCT` | No | |
| Marketing name | `ro.oppo.market.name` ❓ / `ro.product.marketname` ❓ | No, best effort | Confirm key for current OPlus builds |
| OxygenOS/ColorOS version | `ro.build.version.ota` ✅, `ro.oppo.version` ❓, `ro.build.version.opporom` ❓ | No, best effort | Try in order; first non-empty wins |
| Build display string | `Build.DISPLAY` | No | Source of `otaVersion` |
| Android version | `Build.VERSION.RELEASE` / `SDK_INT` | No | |
| Security patch | `Build.VERSION.SECURITY_PATCH` | No | Display only |
| Region | `ro.oppo.region` ❓, SIM MCC, locale | No, best effort | See §2.2 |
| Serial | `Build.getSerial()` | Requires READ_PRIVILEGED_PHONE_STATS on API 26+ | Treat as optional; never required |

### 2.2 Region Inference Order

Resolve region with the first non-empty hit:

1. `ro.oppo.region` / `ro.oppo.market.name` suffix (❓ confirm)
2. SIM ISO country code (`TelephonyManager.getSimCountryIso()`) — requires READ_PHONE_STATE
3. `TelephonyManager.getNetworkCountryIso()`
4. `Resources.configuration.locales[0]` country
5. Default: `global`; surface a "region inferred, confirm?" hint

### 2.3 Detection Robustness

- Any hidden property lookup failure must degrade silently to `Build.*` constants, never crash, and mark the affected profile field as `unknown`.
- **Expected product reality:** on most Android 9+ non-root devices, hidden API policy blocks `ro.build.version.ota` and friends, so the OxygenOS/ColorOS version will frequently be `unknown`. Do not design the UI around auto-detection succeeding; treat manual build-string entry as the primary path and auto-detection as a convenience.
- Attempt to recover the version from `Build.DISPLAY` string parsing first (where the display embeds the build id, e.g. `..._11.0.2.2.LE28AA`-style strings) before prompting the user. Parsing is best-effort and must not block on format mismatches.
- If the version is still empty after `Build.DISPLAY` parsing, mark the profile `incomplete` and block lookup until the user enters a manual build string.
- Detection runs on a background dispatcher; the dashboard shows a brief "detecting…" skeleton, never a blank screen.

## 3. Download and Storage Design

### 3.1 Engine Selection

- **OkHttp** for HTTP with explicit `Range` header support and an interceptor that records `ETag`/`Last-Modified`/`Accept-Ranges`.
- **WorkManager** foreground worker for lifecycle, with `ForegroundServiceType_DATA_SYNC` on Android 14+.
- **Single-task serial queue** for v1. Concurrent downloads add complexity (bandwidth, storage, verification ordering) with little user benefit for a phone utility. Multiple queued tasks are allowed; only one runs at a time.
- Do **not** use `DownloadManager` — it hides resume/etag state and cannot verify checksums mid-stream.

### 3.2 Storage Location

Final ZIPs go to **`MediaStore.Downloads`** (external, user-visible in the system Files app, survives app uninstall on Android 10+ via the public Downloads collection). Rationale: users expect to flash or copy the ZIP from outside the app; app-specific storage would hide it.

- On Android 10+ use the scoped `MediaStore` write path with `RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS/<AppName>`.
- On API 28 and below, fall back to `Environment.getExternalStoragePublicDirectory(DOWNLOADS)/<AppName>` with the legacy storage permission.
- Temp file lives in **app-specific external storage** (`context.externalCacheDir` when available, otherwise `getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)`). Use internal `context.cacheDir` only as a last-resort fallback for small test downloads, not for multi-GB OTA packages. Partial downloads must never be written directly to shared Downloads. Temp filename = `<taskId>.zip.part`.
- Before enqueueing a multi-GB download, estimate available bytes in the selected temp root and target Downloads volume. If temp and final storage share a volume, require `targetSize * 2 + 1 GiB` free to allow copy/promote plus cleanup; if they differ, require `targetSize + 1 GiB` free on each involved volume.

### 3.3 Range Resume Persistence

Persist per-task state in Room (`download_task` table):

- `taskId`, `downloadUrl`, `sourceHost`
- `etag`, `lastModified` (captured on first response)
- `acceptRanges: Boolean`
- `downloadedBytes: Long`
- `tempFileName`
- `state: DownloadState`
- `targetSize: Long?`, `md5?`, `sha256?`

On resume:
1. If `acceptRanges == true` AND the server still returns the same `ETag`/`Last-Modified` for a `HEAD` (or the first range `GET` returns `206` when `HEAD` is unsupported), send `Range: bytes=<downloadedBytes>-` and append to `.part`.
2. If `acceptRanges == false` or the etag changed, discard `.part` and restart from 0. Surface "server changed package, restarting" as the reason.
3. Verify `.part` size matches `downloadedBytes` before resuming; if larger (interrupted double-write), truncate to `downloadedBytes`.

### 3.4 Network and Doze Strategy

- Register a `NetworkCallback`; on loss, transition to `Paused(NetworkLost)` and let WorkManager retry with backoff when connectivity returns.
- On metered vs unmetered switch, respect the user's "download on Wi-Fi only" preference (default on).
- Rely on WorkManager constraints (`NetworkType.UNMETERED` when toggled) rather than holding a `PowerManager.WakeLock` for the whole transfer. Acquire a `PARTIAL_WAKE_LOCK` only for the brief checksum pass after completion if needed.
- Honor `BatteryTooLow` (default threshold 15%) as a pause condition.

### 3.5 Download State Machine

```
   Queued ──start──▶ Running ──pause──▶ Paused
                       │ ▲                 │
                       │ └── resume ───────┘
                       │
             ┌─────────┼──────────┐
         cancel      complete   error(retriable)
             │           │          │
             ▼           ▼          ▼
         Canceled    Verifying    Failed
         (terminal)    │ │          │
                    ok │ │ mismatch │ no retries left
                       ▼ │          ▼
                   Verified    Failed (terminal)
                   (terminal)     │
                                  │ retriable + backoff
                                  ▼
                               Retrying ──retry──▶ Running
```

Rules:
- `Running` → `Paused` (pause); `Paused` → `Running` (resume).
- `Running` → `Verifying` (complete). `Verifying` → `Verified` (ok, terminal) or → `Failed(ChecksumMismatch)` (mismatch, terminal — do not auto-retry; re-downloading the same URL yields the same bytes, so let the user decide discard vs retry).
- `Running` → `Failed` (retriable network/server error).
- `Failed` → `Retrying` only if the error category is retriable (network/server, not file/storage and not checksum mismatch). After N=3 retries, stay `Failed` (terminal) and surface the reason.
- `Retrying` → `Running` (retry attempt, with backoff).
- `Queued`/`Running`/`Paused` → `Canceled` (user cancel). `Canceled` is terminal and deletes `.part` + the Room row on the next idle tick.
- Every transition persists to Room before notifying the UI, so process death always observes a recoverable state.

### 3.6 Temp File Hygiene

- On app start, scan every configured temp root (`externalCacheDir`, `getExternalFilesDir(DOWNLOADS)`, and internal `cacheDir` fallback) for `*.zip.part` with no matching Room task (orphaned) and delete them.
- On low-storage warning (`ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_LOW_CRITICAL)`), do not delete active `.part` files, but reject new enqueues.
- Keep a cap on total queue size (default 20 tasks) to bound storage use.

## 4. Integrity Verification

### 4.1 Checksum Policy

- Prefer **SHA-256** if the server provides it; fall back to **MD5** (the legacy OxygenOS field). If neither is present, mark the package `unverified` and warn the user explicitly in the UI.
- Compute the hash by streaming the completed `.part` file in 1 MiB chunks on `Dispatchers.IO`; never load the whole file into memory.
- Expose verify progress as a separate channel from download progress so the UI can show "downloading" vs "verifying".

### 4.2 Signature Scope

v1 verifies **transfer integrity only** (the checksum above). Full APK/OTA-signature verification (checking the package is signed by OPlus) is a future, root-optional phase — note this limitation in the UI ("transfer verified, not package-signed").

### 4.3 Failure Handling

On mismatch:
- Do **not** auto-delete the `.part`. Move it aside as `<taskId>.zip.bad` in cache and offer "discard" vs "retry download" actions.
- Persist the mismatched hash and expected hash in the history record for debugging.

## 5. First Version User Flow

1. The app opens to a dense but clean dashboard showing detected model, region, Android/OxygenOS/ColorOS build, and network status.
2. The user can accept detected values or switch to manual profile mode.
3. The lookup action returns either a package card or a structured no-update/error state.
4. The package card shows version, type, size, source host, MD5/SHA-256 when available, and actions for download or copy link.
5. Download runs as a foreground task with pause/resume/cancel, progress, speed, ETA, and persistent notification.
6. After completion, the app verifies the checksum if available, then promotes the final ZIP to the public Downloads collection (`MediaStore.Downloads/<AppName>`).
7. History keeps successful lookups and downloaded packages so users can re-open metadata, copy links, or locate files.

## 6. Interaction Design

The UI should feel like a serious utility, not a landing page. Use restrained Material 3 surfaces, compact information density, clear state colors, and icons for actions. Avoid decorative hero sections and oversized marketing copy.

Important states must be explicit:

- Query idle, querying, package found, no package, blocked network, malformed response, and server error.
- Download queued, running, paused, retrying, failed, verifying, verified, and checksum mismatch.
- Manual profile dirty state, invalid profile fields, and profile saved.

Use progressive disclosure for advanced fields. Normal users should see detected device details and one primary lookup action. Advanced users can expand spoof/build override controls without cluttering the default screen.

## 7. Performance Requirements

Downloads must stream directly to disk and never buffer full ZIP files in memory. Progress updates should be throttled (≤ 4 updates/sec, or on each 1 MiB written, whichever is rarer) so Compose recomposition stays smooth. Large history lists should use lazy lists with stable keys. Checksum calculation should run on a background dispatcher and expose progress separately from network progress.

The app should survive process death during active downloads by persisting enough state (§3.3, §3.5) to recover or mark the task as interrupted. It should avoid keeping wake locks directly unless a specific Android API path requires it; foreground work and system download constraints should carry the normal case.

## 8. Error Handling

Errors should be mapped into user-understandable categories:

- Device/profile problem: unsupported model, missing build, invalid region, or manual field validation failure.
- Network problem: DNS, TLS, timeout, blocked host, interrupted connection, or captive network.
- Server problem: empty response, unexpected schema, HTTP status, or OTA service refusal.
- File problem: insufficient storage, write denied, checksum mismatch, or final move failure.

Every failed lookup or download should expose a compact reason and a details view suitable for debugging or future PC companion export.

## 9. Observability

- Structured logs via a thin app logger wrapping `android.util.Log` in debug; in release, logs are written to a rolling local file under `context.filesDir/logs/` capped at 5 MiB total. Nothing is uploaded.
- Log levels: `ERROR` (failures), `WARN` (retries, degraded paths), `INFO` (task state transitions), `DEBUG` (HTTP host/status, download bytes — never bodies).
- Redact PII before logging: IMEI, serial, MAC, full build string is allowed but serial is masked to last 4 chars.
- The details view (§8) surfaces the last N log lines and the current task's error chain, exportable as a `.zip` for the future PC companion.

## 10. Privacy and Compliance

- **Fingerprint disclosure:** the OTA request necessarily sends model, build, and region to OPlus CDN. Manual profile mode may send user-entered values. Show a one-time disclosure ("This app queries OPlus servers with your device's build info. Continue?") before the first lookup, and link it from manual profile mode.
- **Optional fields:** serial/IMEI are never required for a lookup. If a captured reference request includes them, omit them by default and only include if a lookup fails without them (then prompt the user).
- **Transport:** HTTPS only. Disable cleartext to OTA hosts via `networkSecurityConfig`. Certificate pinning is deferred (pins would need capture and break on host rotation); rely on system trust for v1.
- **Storage:** downloaded ZIPs go to public Downloads; the app does not read or modify other files there.
- **Trademark & naming:** "OPlus", "OnePlus", "OxygenOS", "ColorOS" are trademarks. App name, package id, and store listing must not imply official affiliation. Recommended package id: `dev.shallowdusty.oplusotastudio` (neutral). No OPlus logo/assets in the app.
- **License:** Apache-2.0 for the codebase. Fixture data derived from real OTA responses must be reduced to the minimum parser input, redacted before commit, and kept out of public releases until reviewed for tokens, signed URLs, PII, and redistribution risk.

## 11. Testing Strategy

Use TDD for behavior-heavy code. The default verification ladder is pure JVM first, fake HTTP second, emulator/device only where Android platform behavior matters:

- Unit tests for OTA profile normalization and request payload generation (Style A and Style B).
- Unit tests for OTA response parsing with successful, no-update, malformed, and missing-field fixtures (redacted captures from §1.5).
- Unit tests for download state transitions (full state machine in §3.5), resume header calculation, etag-change discard, file promotion rules, and checksum mismatch.
- Integration tests with a fake `MockWebServer` covering: full download + verify, resume after disconnect, server-side package change, and checksum mismatch.
- Instrumentation tests for storage promotion to `MediaStore.Downloads` across API 26/29/34. CI runs API 29/34 first; API 26 may be local-only until emulator stability is proven, but its command and result must be recorded before v0.1 is called done.
- UI tests for lookup state rendering and primary download interactions after the core flows exist; add Compose screenshot tests for each state in §6.

The first implementation should prefer fake HTTP servers (`okhttp3.mockwebserver`) and local temp files over mocks where possible.

## 12. Internationalization

- Ship with `en` and `zh-rCN` from day one; design strings as resources, never inline.
- All OPlus build strings, region codes, and error categories are data, not translatable strings.
- Format sizes (`42.3 MB`), dates, and speeds via `NumberFormat`/`DateUtils` with the device locale.

## 13. Engineering Baseline

- **minSdk 26** (Android 8.0 as the practical floor for current OxygenOS/ColorOS devices; exact coverage share to be confirmed before release), **targetSdk 36 preferred / 35 minimum**. Google Play currently requires new phone apps and updates to target Android 15 / API 35 or higher from August 31, 2025; Android 16 / API 36 is the migration target when dependencies and emulator images are stable.
- **Default scaffold baseline:** Kotlin `2.4.0`, AGP `9.2.1`, Gradle `9.6.1`, JDK `17`, SDK Build Tools `36.0.0`, Compose BOM pinned in `libs.versions.toml`.
- **Fallback baseline if AGP 9.2 or Kotlin 2.4 blocks Compose/Room/KSP stability:** Kotlin `2.2.21`, AGP `8.13.x`, JDK `17`, targetSdk `35`. If the fallback is used, open a `docs:` follow-up to record why and when to retry the modern baseline.
- Build variants: `debug` (verbose logs, no R8), `release` (R8 full mode, obfuscation on, signed via a keystore stored outside the repo).
- Android Lint runs in CI; new code must be clean. **detekt deferred:** no stable detekt release supports Kotlin 2.4.0 as of 2026-06 (1.23.8 tops out at Kotlin 2.0.21; 2.0.0-alpha.5 supports 2.4.0 but is excluded by the no-snapshot/alpha rule below). detekt is re-enabled as a follow-up once a stable release supporting Kotlin 2.4.0 ships; until then Android Lint is the sole static check.
- **CI:** GitHub Actions matrix (unit tests on JVM, instrumentation on API 29/34 emulators via `reactivecircus/android-emulator-runner`). Block merges on red unit tests; instrumentation is informational until stable.
- **Branch & commit:** trunk `main` protected; feature branches `feat/`, `fix/`, `docs/`; squash-merge PRs; conventional-commit messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
- **Dependencies:** version catalog (`libs.versions.toml`); no snapshot dependencies in `main`.

## 14. Milestones and Acceptance Criteria

### v0.0 — Revertable foundation

Done when:
- [ ] Repo has Apache-2.0 `LICENSE`, `README.md` pointing to this spec, `.gitignore`, version catalog, Gradle wrapper, and CI skeleton.
- [ ] Android project compiles with the selected §13 baseline and contains the planned modules with empty but buildable source sets.
- [ ] `core-model` defines `OtaProfile`, `OtaPackage`, `OtaLookupResult`, `DownloadState`, and error categories with pure JVM tests.
- [ ] `core-download` has a pure Kotlin state machine test suite for every transition in §3.5 before WorkManager exists. *(Backend module; lands when codex implements core-download. Frontend v0.0 defines the `DownloadState` type this suite exercises.)*
- [ ] CI runs `./gradlew test lintDebug` (detekt deferred per §13; the `detekt` portion of this criterion is met when a stable detekt supporting Kotlin 2.4.0 ships).
- [ ] Every bootstrap concern lands as a separate commit: license/readme, Gradle scaffold, module graph, CI, first domain tests.

### v0.1 — Core MVP (lookup + download + verify)

Done when:
- [ ] Device detection (§2) fills a best-effort profile on one real OnePlus/OPlus device and one second profile source (real OPPO device preferred; emulator/manual profile acceptable only if clearly labeled).
- [ ] Lookup returns a `PackageFound` through one enabled strategy backed by `captured-real`, `replayed-real-profile`, or `live-verified` evidence (§1.5). If not `live-verified`, the UI marks live lookup as experimental and v0.2 carries the live verification blocker.
- [ ] A full ZIP downloads end-to-end with resume after a forced network drop, and is promoted to `MediaStore.Downloads`.
- [ ] Checksum verification passes on a known-good package and fails (explicitly) on a tampered one.
- [ ] All §11 unit and MockWebServer integration tests green; storage promotion is verified on API 29/34 CI or locally, and API 26 has a recorded local result or documented emulator blocker.
- [ ] Release copy, details view, and logs do not imply the app verifies OPlus package signatures; v0.1 verifies transfer integrity only.

### v0.2 — Profile control + history

Done when:
- [ ] Manual profile mode accepts overrides, validates fields, and round-trips a lookup.
- [ ] History list persists lookups and downloads across process death; lazy list scrolls smoothly with 100+ entries.
- [ ] Region/host override exposed in advanced (progressive disclosure) section.
- [ ] Live-endpoint verification for at least one region is complete. This is mandatory for v0.2 if v0.1 shipped on fixture-backed evidence.

### v0.3 — Release candidate polish

Done when:
- [ ] `en` + `zh-rCN` strings complete; no hardcoded user-facing text.
- [ ] Error categories in §8 all have user-facing copy and a details view.
- [ ] Local logging + export (§9) wired to the details view.
- [ ] R8 release build installs and runs; CI green on emulator matrix.
- [ ] Public release readiness review passes: naming/trademark, privacy disclosure, fixture redistribution, screenshots, and branch protection.

### Future

- PC companion (ADB), root-optional package-signature verification, optional certificate pinning, additional locales.

## Future PC Companion

After the mobile app is usable, add a separate PC companion project. It can use ADB to detect connected devices, push profile hints, pull logs, export downloaded package metadata, and eventually coordinate advanced root workflows. It should consume exported metadata from the phone app instead of duplicating OTA business logic immediately.

The PC companion should be treated as a second product surface, not a dependency of the phone app.

## Repository Setup

The local project already lives at `E:\coding\oplus-ota-studio`, with GitHub remote `Shallow-dusty/oplus-ota-studio`. Keep the repository private until the app has a working first release candidate (v0.3).

Before regular feature work begins, finish the v0.0 bootstrap checklist:

1. Add `LICENSE` and `README.md` in one docs/setup commit.
2. Add Gradle wrapper, settings, version catalog, and empty module graph in one build commit.
3. Add CI skeleton in one `ci:` commit.
4. Add first domain model + state machine tests in focused `feat:` / `test:` commits.
5. Configure branch protection on `main` once CI exists: require PR review and green required checks.
