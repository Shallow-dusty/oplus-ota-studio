# OPlus OTA Studio

A mobile-first OTA utility for OPlus / OnePlus devices: device detection, OTA
profile setup, package lookup, resumable download, integrity verification, and
clear status feedback — running entirely on the phone, no root required for the
normal lookup/download flow.

> **Status:** backend-core in progress. The app now has real lookup, download,
> storage, WorkManager, logging, diagnostics, and MediaStore promotion wiring.
> It is not release-complete yet: real-device/live OTA evidence, history/detail
> polish, localization, and connected instrumentation evidence are still open.
> See [`docs/progress.md`](docs/progress.md) for the current progress panel.

## Project layout

The app is split into focused Gradle modules. The dependency direction is
strictly one-way (lower layers never depend on higher layers):

| Module | Layer | Role | Owner |
|---|---|---|---|
| `app` | shell | Android entry point, theme, navigation, DI wiring | frontend |
| `feature-lookup` | feature | Device/profile screen + OTA query result screen | frontend |
| `feature-downloads` | feature | Download queue, progress surface, file actions | frontend |
| `core-model` | domain | Immutable domain models + service contracts (interfaces) | frontend |
| `core-ota` | core | OTA request construction, response parsing, error mapping | backend |
| `core-download` | core | Streaming download engine, range resume, checksum | backend |
| `core-storage` | core | Room entities, DataStore preferences | backend |

`core-model` defines the service contracts (`OtaLookupService`,
`DownloadEngine`, `DeviceDetector`, `PackageRepository`); feature modules depend
only on those interfaces, and `app` injects the implementation. Normal app
startup now uses real Room/DataStore, WorkManager, MediaStore promotion,
logging, diagnostics, device detection, and legacy OTA lookup wiring.
`AppGraph` still keeps fake defaults for tests and non-Application construction.

## Design spec

The authoritative design and delivery plan lives at
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md).
Every architectural decision (protocol contract, device detection, download
state machine, storage, error taxonomy, milestones) is documented there.
Current implementation progress is tracked in
[`docs/progress.md`](docs/progress.md).

## Build

Requires JDK 17 and Android SDK with Build Tools 36.0.0 and platform android-37.
The Gradle wrapper is pinned to Gradle 9.6.1.

```sh
./gradlew assembleDebug      # build the app
./gradlew test               # pure-JVM unit tests
./gradlew lintDebug          # Android Lint (CI runs these three)
```

> **detekt deferred.** No stable detekt release supports Kotlin 2.4.0 yet
> (1.23.8 tops out at Kotlin 2.0.21; 2.0.0-alpha.5 supports 2.4.0 but is a
> pre-release and excluded by the "no snapshot/alpha in main" rule). detekt
> lands as a follow-up once a stable detekt supporting Kotlin 2.4.0 ships.

## License

Apache-2.0. See [LICENSE](LICENSE).

"OPlus", "OnePlus", "OxygenOS", and "ColorOS" are trademarks of their
respective owners. This project is independent and not affiliated with or
endorsed by OPlus/OnePlus.
