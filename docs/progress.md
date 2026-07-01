# OPlus OTA Studio Progress

Last updated: 2026-07-01

This file is the current implementation/status panel. The product source of
truth remains
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](superpowers/specs/2026-06-29-oplus-ota-studio-design.md).

## Current Branch

- Branch: `feat/backend-core`
- Latest implementation commit at this snapshot: `594af6a feat: clarify OTA verification scope`
- Working tree at the start of this documentation pass: clean
- Local connected-device check on 2026-07-01: `adb devices` reported no attached devices

## Current Product Status

The project has moved beyond the original v0.0 UI shell. The app now has real
backend wiring for lookup, downloads, storage, logging, diagnostics, and
WorkManager-backed download execution, but it is not a complete product release
yet.

The remaining release blockers are mostly evidence and end-to-end validation:
real device detection evidence, live/captured/replayed OTA lookup evidence,
API 29/API 34 storage promotion results, and release packaging polish.

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
- Manual lookup profile editing now includes region selection and an advanced
  host override field backed by existing profile validation.
- Lookup and download screens state that hash checks verify transfer integrity
  only and do not verify OPlus package signatures.
- DataStore backs download preferences and lookup privacy consent.

### Files And Platform Integration

- Final ZIP promotion targets `MediaStore.Downloads/OPlus OTA Studio` on Android
  10+.
- An instrumentation test covers MediaStore promotion on API 29+ behavior.
- Local API 30 emulator evidence on 2026-07-01:
  `connectedDebugAndroidTest` passed
  `AndroidMediaStoreDownloadFilePromoterInstrumentedTest.promotesZipIntoDownloadsCollectionOnScopedStorage`
  on `Vector_API30(AVD) - 11`, SDK 30.
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
- Local emulator validation currently covers API 30 only; API 29 and API 34
  instrumentation evidence is still missing.
- No committed `captured-real-*` or `replayed-real-profile-*` successful OTA
  response fixture exists yet.
- The ColorOS component parser is synthetic-schema coverage, not proof of a live
  server chain.
- v0.2 still requires at least one live-endpoint verified region if v0.1 ships
  on fixture-backed evidence.

### Download/Product Flow

- Full ZIP end-to-end validation with a forced network drop still needs
  device/emulator evidence.
- API 26 storage behavior still needs a recorded local result or documented
  emulator blocker before v0.1 can be called done.
- Checksum mismatch expected/actual hashes are exposed in task failure details,
  but persisting them into structured history/debug records is now a hardening
  item, not the next product-flow blocker.

### Release Readiness

- User-facing strings are still mostly inline Compose text; `en` and `zh-rCN`
  resources are not complete.
- UI tests/screenshot tests for every lookup/download state are not complete.
- Release build still has R8 disabled; v0.3 requires R8 release install/run
  evidence.
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
```

## Next Recommended Work

1. Run connected instrumentation on API 29 and API 34, and record or document
   API 26 behavior.
2. Collect a captured-real or replayed-real-profile OTA success fixture, or keep
   lookup clearly experimental until live verification is possible.
3. Move user-facing strings into `en` and `zh-rCN` resources.
4. Persist checksum mismatch expected/actual hash as structured history/debug
   metadata during release hardening.
