# OPlus OTA Studio Progress

Last updated: 2026-07-01

This file is the current implementation/status panel. The product source of
truth remains
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](superpowers/specs/2026-06-29-oplus-ota-studio-design.md).

## Current Branch

- Branch: `feat/backend-core`
- Latest implementation commit at this snapshot: `d34cf11 fix: parse legacy OTA XML on Android`
- Local connected-device check on 2026-07-01: `adb devices` reported `emulator-5554 device`

## Current Product Status

The project has moved beyond the original v0.0 UI shell. The app now has real
backend wiring for lookup, downloads, storage, logging, diagnostics, and
WorkManager-backed download execution, but it is not a complete product release
yet.

The remaining release blockers are mostly evidence and release packaging:
real device detection evidence, live/captured/replayed OTA lookup evidence,
and private-trial signing/versioning polish.

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

### Download And Verification

- `SimpleDownloadEngine` streams to `.zip.part`, throttles progress, emits
  speed, verifies checksum, promotes verified/unverified files, quarantines
  checksum mismatches as `.zip.bad`, and persists state before UI notification.
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
- Database schema is at version 3 with migrations `1 -> 2` and `2 -> 3`.
- History now persists package metadata needed for later details/copy-link flows:
  source host, download URL, checksums, release notes, and evidence level.
- Downloads now surfaces lookup history, copy-link/copy-path actions, and a
  direct download action that reuses the persisted package metadata.
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
  response fixture exists yet; a local 2026-07-01 replay attempt against
  `otacn.oppo.com/OnePlusOTA/OnePlus_OTA.php` did not complete the TLS/HTTP
  handshake from this Windows host.
- The ColorOS component parser is synthetic-schema coverage, not proof of a live
  server chain.
- v0.2 still requires at least one live-endpoint verified region if v0.1 ships
  on fixture-backed evidence.

### Download/Product Flow

- Checksum mismatch expected/actual hashes are exposed in task failure details,
  but persisting them into structured history/debug records is now a hardening
  item, not the next product-flow blocker.

### Release Readiness

- UI tests/screenshot tests for every lookup/download state are not complete.
- Debug and unsigned release APK assembly passed locally on 2026-07-01 via
  `:app:assembleDebug :app:assembleRelease`; produced `app-debug.apk` and
  `app-release-unsigned.apk` with `versionName` `0.0.1`.
- Release build still has R8 disabled; v0.3 requires R8 release install/run
  evidence.
- Signed release packaging and v0.1 versioning are not configured yet.
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
```

## Next Recommended Work

1. Collect a captured-real or replayed-real-profile OTA success fixture, or keep
   lookup clearly experimental until live verification is possible.
2. Configure private-trial signing/versioning when cutting a v0.1 artifact.
3. Persist checksum mismatch expected/actual hash as structured history/debug
   metadata during release hardening.
