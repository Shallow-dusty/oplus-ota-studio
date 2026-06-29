# Backend Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first real backend modules for OTA request/response parsing and download state/checksum logic while keeping the Android UI unchanged.

**Architecture:** `core-ota` implements protocol-neutral request construction, host resolution, and fixture-backed response parsing into `core-model` domain types. `core-download` implements a pure Kotlin state machine and checksum verifier that can later be wrapped by WorkManager and storage promotion. Android-specific download IO and Room storage remain future slices.

**Tech Stack:** Kotlin 2.4.0, Android library modules, JUnit Jupiter, existing `core-model` contracts.

---

### Task 1: Create `core-ota` Module Scaffold

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core-ota/build.gradle.kts`
- Create: `core-ota/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write the module files**

```kotlin
// settings.gradle.kts
include(":core-ota")
```

```kotlin
// core-ota/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.shallowdusty.oplusotastudio.core.ota"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core-model"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

```xml
<!-- core-ota/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 2: Verify the scaffold builds**

Run: `.\gradlew.bat :core-ota:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add settings.gradle.kts core-ota/build.gradle.kts core-ota/src/main/AndroidManifest.xml
git commit -m "build: add core-ota module"
```

### Task 2: Add OTA Request and XML Parser

**Files:**
- Create: `core-ota/src/test/kotlin/dev/shallowdusty/oplusotastudio/core/ota/LegacyOtaProtocolTest.kt`
- Create: `core-ota/src/main/kotlin/dev/shallowdusty/oplusotastudio/core/ota/OtaHostResolver.kt`
- Create: `core-ota/src/main/kotlin/dev/shallowdusty/oplusotastudio/core/ota/LegacyOtaProtocol.kt`

- [ ] **Step 1: Write failing parser/request tests**

```kotlin
@Test
fun `builds legacy form request from profile`() {
    val profile = OtaProfile(
        model = "LE2120",
        region = OtaRegion.China,
        otaVersion = "LE2120_11.H.23_0001_000000000001",
        systemType = "Color OS",
        deviceCodename = "OnePlus9Pro_CH",
    )

    val request = LegacyOtaProtocol().buildRequest(profile)

    assertEquals("otacn.oppo.com", request.host)
    assertEquals("/OnePlusOTA/OnePlus_OTA.php", request.path)
    assertEquals("application/x-www-form-urlencoded", request.contentType)
    assertTrue(request.body.contains("otaVersion=LE2120_11.H.23_0001_000000000001"))
    assertTrue(request.body.contains("device=OnePlus9Pro_CH"))
}

@Test
fun `parses legacy package found response`() {
    val xml = """
        <root>
          <Command>NEW_VERSION</Command>
          <versionName>LE2120_14.0.0.1901(CN01)</versionName>
          <type>full</type>
          <size>6559817109</size>
          <md5>5ae1e4d8101218d58c1da10092b22996</md5>
          <url>https://gauss-compotacostauto-cn.allawnfs.com/package.zip</url>
        </root>
    """.trimIndent()

    val result = LegacyOtaProtocol().parseResponse(xml, "gauss-compotacostauto-cn.allawnfs.com")

    val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
    assertEquals("LE2120_14.0.0.1901(CN01)", pkg.versionName)
    assertEquals(6_559_817_109L, pkg.sizeBytes)
    assertEquals("gauss-compotacostauto-cn.allawnfs.com", pkg.sourceHost)
    assertEquals("5ae1e4d8101218d58c1da10092b22996", pkg.md5)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :core-ota:testDebugUnitTest --tests "*LegacyOtaProtocolTest"`
Expected: FAIL because `LegacyOtaProtocol` does not exist.

- [ ] **Step 3: Implement minimal request/parser code**

Create `OtaHostResolver` with region mappings from spec §1.1 and `LegacyOtaProtocol` with `buildRequest(profile)` plus `parseResponse(raw, sourceHost)`.

- [ ] **Step 4: Verify tests pass**

Run: `.\gradlew.bat :core-ota:testDebugUnitTest --tests "*LegacyOtaProtocolTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core-ota/src/main/kotlin core-ota/src/test/kotlin
git commit -m "feat: add legacy OTA protocol parser"
```

### Task 3: Create `core-download` Module Scaffold

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core-download/build.gradle.kts`
- Create: `core-download/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write the module files**

Add `include(":core-download")` and create an Android library module depending on `core-model`.

- [ ] **Step 2: Verify the scaffold builds**

Run: `.\gradlew.bat :core-download:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add settings.gradle.kts core-download/build.gradle.kts core-download/src/main/AndroidManifest.xml
git commit -m "build: add core-download module"
```

### Task 4: Add Download State Machine

**Files:**
- Create: `core-download/src/test/kotlin/dev/shallowdusty/oplusotastudio/core/download/DownloadStateMachineTest.kt`
- Create: `core-download/src/main/kotlin/dev/shallowdusty/oplusotastudio/core/download/DownloadStateMachine.kt`

- [ ] **Step 1: Write failing state transition tests**

Cover `Queued -> Running`, `Running -> Paused -> Running`, `Running -> Verifying -> Verified`, checksum mismatch terminal failure, retriable network failure to retrying, retry exhaustion, and cancel from queued/running/paused.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :core-download:testDebugUnitTest --tests "*DownloadStateMachineTest"`
Expected: FAIL because `DownloadStateMachine` does not exist.

- [ ] **Step 3: Implement minimal transition code**

Create a pure Kotlin class that accepts the current `DownloadState` and event methods, returning the next `DownloadState` without side effects.

- [ ] **Step 4: Verify tests pass**

Run: `.\gradlew.bat :core-download:testDebugUnitTest --tests "*DownloadStateMachineTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core-download/src/main/kotlin core-download/src/test/kotlin
git commit -m "feat: add download state machine"
```

### Task 5: Add Checksum Verifier

**Files:**
- Create: `core-download/src/test/kotlin/dev/shallowdusty/oplusotastudio/core/download/ChecksumVerifierTest.kt`
- Create: `core-download/src/main/kotlin/dev/shallowdusty/oplusotastudio/core/download/ChecksumVerifier.kt`

- [ ] **Step 1: Write failing checksum tests**

Test SHA-256 preference, MD5 fallback, no-checksum unverified result, and mismatch returning expected/actual hashes.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :core-download:testDebugUnitTest --tests "*ChecksumVerifierTest"`
Expected: FAIL because `ChecksumVerifier` does not exist.

- [ ] **Step 3: Implement minimal streaming verifier**

Use `java.security.MessageDigest` and stream files with an 1 MiB buffer. Return `Verified`, `Unverified`, or `Mismatch`.

- [ ] **Step 4: Verify tests pass**

Run: `.\gradlew.bat :core-download:testDebugUnitTest --tests "*ChecksumVerifierTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core-download/src/main/kotlin core-download/src/test/kotlin
git commit -m "feat: add checksum verifier"
```

### Task 6: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run full unit test suite**

Run: `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Check worktree**

Run: `git status --short`
Expected: clean.
