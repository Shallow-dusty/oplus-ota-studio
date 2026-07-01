# Release Packaging

This project keeps release signing material outside the repository. The app
build reads signing values from explicit Gradle properties or environment
variables.

## Version

- Current private-trial version: `0.1.0`
- Current `versionCode`: `10`

## Unsigned Local Build

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

Without signing values, the release variant assembles an unsigned APK for local
build verification.

## Signed Private-Trial Build

Use either Gradle properties:

```powershell
.\gradlew.bat :app:assembleRelease `
  -PoplusOtaStudio.releaseStoreFile="D:\path\to\release.jks" `
  -PoplusOtaStudio.releaseStorePassword="<store-password>" `
  -PoplusOtaStudio.releaseKeyAlias="<key-alias>" `
  -PoplusOtaStudio.releaseKeyPassword="<key-password>"
```

Or environment variables:

```powershell
$env:OPLUS_OTA_STUDIO_RELEASE_STORE_FILE = "D:\path\to\release.jks"
$env:OPLUS_OTA_STUDIO_RELEASE_STORE_PASSWORD = "<store-password>"
$env:OPLUS_OTA_STUDIO_RELEASE_KEY_ALIAS = "<key-alias>"
$env:OPLUS_OTA_STUDIO_RELEASE_KEY_PASSWORD = "<key-password>"
.\gradlew.bat :app:assembleRelease
```

If any signing value is set, all four must be set. Partial signing
configuration fails during Gradle configuration rather than producing a broken
artifact.

## Verification

```powershell
& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
Get-Content app\build\outputs\apk\release\output-metadata.json
```

Local smoke evidence on 2026-07-01:

- `:app:assembleDebug :app:assembleRelease` passed without signing values.
- `:app:assembleRelease` passed with a temporary throwaway keystore.
- `apksigner verify --print-certs` reported a V2 signer for the temporary
  signed APK.
- `output-metadata.json` reported `versionCode` `10` and `versionName` `0.1.0`.

Current-code release smoke after UI state presentation coverage on 2026-07-01:

- `:app:assembleRelease` passed without signing values and ran
  `:app:minifyReleaseWithR8`.
- `:app:assembleRelease` passed with a temporary throwaway keystore under
  `build/tmp/`.
- `apksigner verify --print-certs` reported a V2 signer for
  `app-release.apk`.
- `app-release.apk` installed on `OPlus_API34` after removing an older
  differently signed smoke build, and `.MainActivity` launched as the top
  resumed activity.
- A targeted logcat scan found no `FATAL EXCEPTION` or app-process
  `AndroidRuntime` crash.
- Detailed evidence:
  `docs/evidence/release/r8-release-api34-smoke-2026-07-01-ui-state-refresh.txt`.
