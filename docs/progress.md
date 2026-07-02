# OPlus OTA Studio Progress

Last updated: 2026-07-02

This file is the current implementation/status panel. The product source of
truth remains
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](superpowers/specs/2026-06-29-oplus-ota-studio-design.md).

## Current Branch

- Branch: `feat/backend-core`
- Latest implementation commit at this snapshot:
  `c7ad5ec fix(core-ota): run OTA HTTP calls on IO dispatcher`
- Local connected-device check on 2026-07-02:
  `adb devices -l` reported a physical OnePlus 9 Pro CN device
  (`model: LE2120`, serial redacted).

## Current Product Status

The project has moved beyond the original v0.0 UI shell. The app now has real
backend wiring for lookup, downloads, storage, logging, diagnostics, and
WorkManager-backed download execution. It now also has physical-device ColorOS
component OTA replay evidence for OnePlus 9 Pro CN.

The remaining release blockers are release-focused: complete final claim
cleanup and keep public release readiness out of scope.

## Implemented

### App Wiring

- `OtaStudioApplication` wires Room, DataStore, MediaStore promotion,
  WorkManager scheduling, battery/storage admission gates, logging, diagnostics,
  and the real ColorOS component OTA lookup service into `AppGraph`.
- `AppGraph` still has fake defaults for tests or non-Application construction,
  but normal app startup injects real storage/download/logging components.

### Device And Profile

- `AndroidDeviceDetector` uses Android `Build.*`, best-effort system property
  reads, display-build parsing, region inference, and an incomplete-profile path.
- Device detection now carries optional ColorOS request hints into `OtaProfile`:
  `ro.build.oplus_nv_id`, runtime Android ID, and device language. The raw
  Android ID is read at runtime and is not committed in tests or evidence.
- `OtaProfile` supports manual host override validation.
- Lookup is blocked when the profile lacks required fields.

### OTA Lookup

- `core-ota` has host resolution, legacy XML/form request construction,
  `OkHttpOtaTransport`, stable error mapping, and parser tests.
- The normal app lookup path now uses the ColorOS component OTA v3 endpoint via
  `ColorOsOtaLookupService`, including AES/CTR request encryption, RSA
  protected-key negotiation, and decrypted component response parsing.
- `OtaEvidenceLevel` marks package provenance as `synthetic`,
  `captured-real`, `replayed-real-profile`, or `live-verified`.
- Lookup UI state surfaces experimental disclosure when a package is not
  `live-verified`.
- Physical OnePlus 9 Pro CN evidence on 2026-07-02:
  `LiveColorOsOtaLookupInstrumentedTest.queriesOnePlus9ProCnColorOsEndpoint`
  passed on a real `LE2120` device against
  `https://component-otapc-cn.allawntech.com/update/v3`, returning
  `LE2120_14.0.0.1901(CN01)` with size `6559817109` and MD5
  `5ae1e4d8101218d58c1da10092b22996`. Details:
  `docs/evidence/ota/live-coloros-oneplus9pro-cn-2026-07-02.txt`.
- The current-version full ZIP package was downloaded locally on 2026-07-02 to
  `E:\coding\oplus-ota-studio-downloads\LE2120_14.0.0.1901_CN01_full.zip`.
  The file size is `6559817109` bytes and MD5 is
  `5ae1e4d8101218d58c1da10092b22996`, matching the live endpoint metadata.
  Details: `docs/evidence/download/current-full-package-oneplus9pro-cn-2026-07-02.txt`.
- A true current-build OnePlus 9 Pro CN query on 2026-07-02 returned ColorOS
  `responseCode=2004` / `artifactV1Result is empty`; this now maps to
  `NoUpdate`. The temporary-signed release APK was installed on the physical
  device, launched, and the UI showed `已是最新` instead of `未知错误`. Details:
  `docs/evidence/release/r8-release-oneplus9pro-smoke-2026-07-02-current-build.txt`.
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

- A physical OnePlus 9 Pro CN debug install, launch smoke, and guarded live
  ColorOS OTA replay lookup passed locally on 2026-07-02. The successful lookup
  is `replayed-real-profile` evidence because it uses a known spoofed OTA build
  string, not a pure current-build update check.
- Local emulator validation now covers API 26, API 29, API 30, and API 34
  storage promotion behavior, plus an API 34 controlled end-to-end download
  smoke through parser, engine, checksum verification, and MediaStore promotion.
- No committed `captured-real-*` or raw `replayed-real-profile-*` successful OTA
  response fixture exists yet. Local 2026-07-01 and 2026-07-02 replay attempts against
  `otacn.oppo.com/OnePlusOTA/OnePlus_OTA.php` did not complete the TLS/HTTP
  handshake from this machine. Windows `curl.exe` failed with Schannel
  `failed to receive handshake`; WSL `curl` failed with OpenSSL
  `SSL_ERROR_SYSCALL`.
  The legacy replay failure log is
  `docs/evidence/ota/live-replay-attempt-2026-07-02.txt`.
- The ColorOS component endpoint chain is now proven by guarded phone-side
  instrumentation. No raw server response fixture is committed because the live
  response contains signed CDN package URLs.
- The real current-version full package has been downloaded and verified on
  local disk; a pure current-build no-update check now passes for OnePlus 9 Pro
  CN. v0.2 should still add broader live coverage for other regions/models and
  at least one pure current-build update-available check when a device profile
  can naturally receive one.

### Release Readiness

- Lookup and download state presentation have focused JVM coverage, but
  screenshot tests are still not complete.
- API 34 Compose UI render evidence now covers selected lookup package-found
  and error states plus all core download state labels, with nonblank root image
  capture checks. This is device-backed render coverage, not final golden image
  comparison or public screenshot review. Details:
  `docs/evidence/instrumentation/ui-state-render-api34-2026-07-01.txt`.
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
- Current-code physical-device release evidence on 2026-07-02 built a
  temporary-signed R8 release APK, installed it on the OnePlus 9 Pro CN, launched
  `.MainActivity`, and verified that the current-build lookup reaches the live
  endpoint and renders the `NoUpdate` screen. Details:
  `docs/evidence/release/r8-release-oneplus9pro-smoke-2026-07-02-current-build.txt`.
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
.\gradlew.bat :core-model:testDebugUnitTest :core-ota:testDebugUnitTest :feature-lookup:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r -e class dev.shallowdusty.oplusotastudio.ota.LiveColorOsOtaLookupInstrumentedTest -e liveOta true -e model LE2120 -e deviceCodename OnePlus9Pro_CH -e otaVersion LE2120_11.H.23_0001_000000000001 -e nvCarrier 10010111 -e language zh-Hans-CN dev.shallowdusty.oplusotastudio.test/androidx.test.runner.AndroidJUnitRunner
.\gradlew.bat :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Next Recommended Work

Use
[`docs/superpowers/plans/2026-07-02-product-finish-path.md`](superpowers/plans/2026-07-02-product-finish-path.md)
as the active finish plan.

1. Stop expanding backend internals unless real-device or release validation
   exposes a concrete blocking bug.
2. Rerun private-trial release build/sign/install/launch smoke after the
   ColorOS lookup code change.
3. Run the final claim audit against README, docs, and user-facing strings.
4. Keep lookup experimental for public claims unless/until more live coverage is
   added beyond the OnePlus 9 Pro CN replay profile.
5. Do not claim public release readiness until release smoke, claim audit, and
   broader model/region confidence are complete.
