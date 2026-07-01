# OPlus OTA Studio Progress

Last updated: 2026-07-01

This file is the current implementation/status panel. The product source of
truth remains
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](superpowers/specs/2026-06-29-oplus-ota-studio-design.md).

## Current Branch

- Branch: `feat/backend-core`
- Latest implementation commit at this snapshot: `7ae024e build: enable R8 for release`
- Local connected-device check on 2026-07-01 after emulator shutdown:
  `adb devices` reported no attached devices.

## Current Product Status

The project has moved beyond the original v0.0 UI shell. The app now has real
backend wiring for lookup, downloads, storage, logging, diagnostics, and
WorkManager-backed download execution, but it is not a complete product release
yet.

The remaining release blockers are evidence-focused: real device detection
evidence and live/captured/replayed OTA lookup evidence.

## Implemented

### App Wiring

- `OtaStudioApplication` wires Room, DataStore, MediaStore promotion,
  WorkManager scheduling, battery/storage admission gates, logging, diagnostics,
  and the real legacy OTA lookup service into `AppGraph`.
- `AppGraph` still has fake defaults for tests or non-Application construction,
  but normal app startup injects real storage/download/logging components.

### Device And Profile

- `AndroidDeviceDetector` uses Android `Build.*`, best-effort system property
  reads, display-build parsing, region inference, and an incomplete-profile path.
- `OtaProfile` supports manual host override validation.
- Lookup is blocked when the profile lacks required fields.

### OTA Lookup

- `core-ota` has host resolution, legacy XML/form request construction,
  `OkHttpOtaTransport`, stable error mapping, and parser tests.
- JSON/ColorOS support exists as a disabled strategy plus a decrypted component
  payload parser backed by a synthetic fixture.
- `OtaEvidenceLevel` marks package provenance as `synthetic`,
  `captured-real`, `replayed-real-profile`, or `live-verified`.
- Lookup UI state surfaces experimental disclosure when a package is not
  `live-verified`.
- Lookup state presentation now has focused JVM coverage for detecting,
  incomplete profile, privacy disclosure, querying, package found, no update,
  and network/server/malformed error copy.

### Download And Verification

- `SimpleDownloadEngine` streams to `.zip.part`, throttles progress, emits
  speed, verifies checksum, promotes verified/unverified files, quarantines
  checksum mismatches as `.zip.bad`, and persists state before UI notification.
- Checksum mismatch failures now persist expected/actual hashes as structured
  fields on both download task state and the latest matching history row.
- An app-level controlled smoke test parses a synthetic legacy OTA success
  fixture, downloads its package from `MockWebServer`, verifies MD5, and promotes
  the final ZIP through the real download engine.
- An API 34 Android runtime controlled smoke test parses a synthetic legacy OTA
  success fixture, downloads its package through `SimpleDownloadEngine`, verifies
  MD5, and promotes the final ZIP through `MediaStore.Downloads`.
- Resume handling covers range requests, ignored ranges, rejected ranges,
  changed validators, partial truncation, and restart-from-zero paths.
- WorkManager scheduling respects Wi-Fi-only preference and battery-not-low
  constraints.
- Startup recovery reschedules queued/running/retrying/verifying and retriable
  failed tasks, while leaving user-paused and terminal tasks alone.

### Storage And History

- Room stores download task state and history.
- Database schema is at version 4 with migrations `1 -> 2`, `2 -> 3`, and
  `3 -> 4`.
- History now persists package metadata needed for later details/copy-link flows:
  source host, download URL, checksums, release notes, evidence level, and
  checksum mismatch diagnostics.
- Downloads now surfaces lookup history, copy-link/copy-path actions, and a
  direct download action that reuses the persisted package metadata.
- Download state presentation now has focused JVM coverage for queued, running,
  paused, retrying, verifying, verified, unverified, canceled, failed, and
  checksum-mismatch hash detail rendering.
- Starting a package download from lookup now opens the downloads screen so the
  next visible step is queue/progress management.
- Android 8/9 download actions request legacy shared-storage permission before
  enqueueing package downloads.
- Manual lookup profile editing now includes region selection and an advanced
  host override field backed by existing profile validation.
- Lookup and download screens state that hash checks verify transfer integrity
  only and do not verify OPlus package signatures.
- Primary app, lookup, downloads, and foreground notification copy now uses
  `en` and `zh-rCN` string resources.
- DataStore backs download preferences and lookup privacy consent.

### Files And Platform Integration

- Final ZIP promotion targets `MediaStore.Downloads/OPlus OTA Studio` on Android
  10+.
- Instrumentation tests cover MediaStore promotion on API 29+ behavior and
  legacy public Downloads promotion on API 26 behavior.
- Local API 30 emulator evidence on 2026-07-01:
  `connectedDebugAndroidTest` passed
  `AndroidMediaStoreDownloadFilePromoterInstrumentedTest.promotesZipIntoDownloadsCollectionOnScopedStorage`
  on `Vector_API30(AVD) - 11`, SDK 30.
- Local API 29 and API 34 emulator evidence on 2026-07-01:
  `:app:connectedDebugAndroidTest` passed
  `AndroidMediaStoreDownloadFilePromoterInstrumentedTest.promotesZipIntoDownloadsCollectionOnScopedStorage`
  on `OPlus_API29(AVD) - 10` and `OPlus_API34(AVD) - 14`; the API 34 run also
  passed
  `AndroidOtaLookupDownloadSmokeInstrumentedTest.parsesControlledOtaFixtureAndPromotesDownloadedZip`.
  XML results are retained under `docs/evidence/instrumentation/`.
- Local API 26 emulator evidence on 2026-07-01:
  `:app:connectedDebugAndroidTest` passed
  `AndroidMediaStoreDownloadFilePromoterInstrumentedTest.promotesZipIntoPublicDownloadsOnLegacyStorage`
  on `OPlus_API26(AVD) - 8.0.0`; XML result is retained under
  `docs/evidence/instrumentation/`.
- CI includes unit/lint plus emulator instrumentation jobs for API 29 and API 34.
- Manifest disables cleartext traffic and declares foreground data-sync service
  support.

### Logging And Diagnostics

- Release logging goes to a capped rolling local file store under app files.
- PII and signed/download URLs are redacted before export.
- Diagnostics export includes logs, manifest metadata, and current task error
  chain when present.

## Not Done Yet

### Evidence And Real-World Validation

- No real device is currently attached locally, so device detection and
  MediaStore flows have not been locally run on a physical OnePlus/OPlus device
  in this snapshot.
- Local emulator validation now covers API 26, API 29, API 30, and API 34
  storage promotion behavior, plus an API 34 controlled end-to-end download
  smoke through parser, engine, checksum verification, and MediaStore promotion.
- No committed `captured-real-*` or `replayed-real-profile-*` successful OTA
  response fixture exists yet; local 2026-07-01 replay attempts against
  `otacn.oppo.com/OnePlusOTA/OnePlus_OTA.php` did not complete the TLS/HTTP
  handshake from this machine. Windows `curl.exe` failed with Schannel
  `failed to receive handshake`; WSL `curl` failed with OpenSSL
  `SSL_ERROR_SYSCALL`.
- The ColorOS component parser is synthetic-schema coverage, not proof of a live
  server chain.
- v0.2 still requires at least one live-endpoint verified region if v0.1 ships
  on fixture-backed evidence.

### Release Readiness

- Lookup and download state presentation have focused JVM coverage, but
  screenshot tests are still not complete.
- Debug and release APK assembly passed locally on 2026-07-01.
- Private-trial versioning is configured as `versionCode` `10` and
  `versionName` `0.1.0`.
- Release signing is configured from explicit Gradle properties or
  `OPLUS_OTA_STUDIO_RELEASE_*` environment variables. A local smoke build with
  a temporary throwaway keystore produced `app-release.apk`, and `apksigner
  verify --print-certs` reported a V2 signer.
- Release builds now run R8 minification/obfuscation via
  `:app:minifyReleaseWithR8`. Local API 34 evidence on 2026-07-01 installed the
  temporary-signed R8 APK and launched `.MainActivity` without a detected
  `AndroidRuntime`/fatal crash; details are retained under
  `docs/evidence/release/`.
- Current-code release evidence after lookup/download state presentation
  coverage on 2026-07-01 built unsigned and temporary-signed release APKs,
  verified the V2 signer, installed the APK on `OPlus_API34` after removing an
  older differently signed smoke build, launched `.MainActivity`, and found no
  app-process fatal crash in a targeted logcat scan. Details:
  `docs/evidence/release/r8-release-api34-smoke-2026-07-01-ui-state-refresh.txt`.
- Durable private signing material is intentionally not committed; use
  `docs/release.md` for the local signing workflow.
- The repository should stay private until v0.3 release-candidate readiness.

## Verification Snapshot

The current verification gate for backend-core work is:

```powershell
.\gradlew.bat test lintDebug :app:compileDebugAndroidTestKotlin
```

This covers JVM unit tests, Android Lint, and compilation of instrumentation
tests. It does not replace connected-device or emulator execution evidence.

Additional checks used during this phase:

```powershell
go run github.com/rhysd/actionlint/cmd/actionlint@latest .github/workflows/ci.yml
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Next Recommended Work

Use
[`docs/superpowers/plans/2026-07-01-v0.1-product-convergence.md`](superpowers/plans/2026-07-01-v0.1-product-convergence.md)
as the active convergence plan.

1. Stop expanding backend internals unless real-device or release validation
   exposes a concrete blocking bug.
2. Collect a captured-real or replayed-real-profile OTA success fixture, or keep
   lookup clearly experimental until live verification is possible.
3. Add focused lookup/download UI state confidence tests before any screenshot
   or visual-polish pass.
4. Cut a private-trial release only with explicit known limitations; do not
   claim public release readiness without real-device/live OTA evidence.
