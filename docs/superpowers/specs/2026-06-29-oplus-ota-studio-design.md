# OPlus OTA Studio Design

## Goal

Build a new mobile-first OTA utility for OPlus/OnePlus devices. The first version runs entirely on the phone and covers device detection, OTA profile setup, package lookup, download, resume, verification, and clear status feedback. A later PC companion can use ADB to automate setup and export logs, but it is not part of the first build.

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
- `core-storage`: Room entities for history and DataStore preferences for user choices.
- `feature-lookup`: device/profile screen and OTA query result screen.
- `feature-downloads`: download queue, active progress surface, file actions, and verification details.

Keep the domain layer Android-light where practical so the OTA parser, download state machine, and checksum logic can be unit-tested without an emulator.

## First Version User Flow

1. The app opens to a dense but clean dashboard showing detected model, region, Android/OxygenOS/ColorOS build, and network status.
2. The user can accept detected values or switch to manual profile mode.
3. The lookup action returns either a package card or a structured no-update/error state.
4. The package card shows version, type, size, source host, MD5 when available, and actions for download or copy link.
5. Download runs as a foreground task with pause/resume/cancel, progress, speed, ETA, and persistent notification.
6. After completion, the app verifies MD5 if available, then moves the final ZIP into a stable app-managed downloads folder.
7. History keeps successful lookups and downloaded packages so users can re-open metadata, copy links, or locate files.

## Interaction Design

The UI should feel like a serious utility, not a landing page. Use restrained Material 3 surfaces, compact information density, clear state colors, and icons for actions. Avoid decorative hero sections and oversized marketing copy.

Important states must be explicit:

- Query idle, querying, package found, no package, blocked network, malformed response, and server error.
- Download queued, running, paused, retrying, failed, verifying, verified, and checksum mismatch.
- Manual profile dirty state, invalid profile fields, and profile saved.

Use progressive disclosure for advanced fields. Normal users should see detected device details and one primary lookup action. Advanced users can expand spoof/build override controls without cluttering the default screen.

## Performance Requirements

Downloads must stream directly to disk and never buffer full ZIP files in memory. Progress updates should be throttled so Compose recomposition stays smooth. Large history lists should use lazy lists and stable keys. Checksum calculation should run on a background dispatcher and expose progress separately from network progress.

The app should survive process death during active downloads by persisting enough state to recover or mark the task as interrupted. It should avoid keeping wake locks directly unless a specific Android API path requires it; foreground work and system download constraints should carry the normal case.

## Error Handling

Errors should be mapped into user-understandable categories:

- Device/profile problem: unsupported model, missing build, invalid region, or manual field validation failure.
- Network problem: DNS, TLS, timeout, blocked host, interrupted connection, or captive network.
- Server problem: empty response, unexpected schema, HTTP status, or OTA service refusal.
- File problem: insufficient storage, write denied, checksum mismatch, or final move failure.

Every failed lookup or download should expose a compact reason and a details view suitable for debugging or future PC companion export.

## Testing Strategy

Use TDD for behavior-heavy code:

- Unit tests for OTA profile normalization and request payload generation.
- Unit tests for OTA response parsing with successful, no-update, malformed, and missing-field fixtures.
- Unit tests for download state transitions, resume header calculation, file promotion rules, and checksum mismatch.
- UI tests for lookup state rendering and primary download interactions after the core flows exist.

The first implementation should prefer fake HTTP servers and local temp files over mocks where possible.

## Future PC Companion

After the mobile app is usable, add a separate PC companion project. It can use ADB to detect connected devices, push profile hints, pull logs, export downloaded package metadata, and eventually coordinate advanced root workflows. It should consume exported metadata from the phone app instead of duplicating OTA business logic immediately.

The PC companion should be treated as a second product surface, not a dependency of the phone app.

## Repository Setup

Create the local project at `E:\coding\oplus-ota-studio`.

Create a GitHub repository named `oplus-ota-studio` under the `Shallow-dusty` account. Start private until the app has a working first release candidate. The repository should contain the spec, implementation plan, Android project, tests, and later release artifacts.
