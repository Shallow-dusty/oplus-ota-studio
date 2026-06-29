# OPlus OTA Studio

A mobile-first OTA utility for OPlus / OnePlus devices: device detection, OTA
profile setup, package lookup, resumable download, integrity verification, and
clear status feedback — running entirely on the phone, no root required for the
normal lookup/download flow.

> **Status:** v0.0 — revertable foundation (scaffold + domain contracts + UI
> shell with fake bindings). Real protocol/download/storage implementations are
> landing incrementally; see the design spec for the milestone roadmap.

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
only on those interfaces, and `app` injects the implementation. During v0.0 the
app injects **fake** implementations so the UI compiles and runs before the
backend modules exist.

## Design spec

The authoritative design and delivery plan lives at
[`docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`](docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md).
Every architectural decision (protocol contract, device detection, download
state machine, storage, error taxonomy, milestones) is documented there.

## Build

Requires JDK 17 and Android SDK with Build Tools 36.0.0. The Gradle wrapper is
pinned to Gradle 9.6.1.

```sh
./gradlew assembleDebug      # build the app
./gradlew test               # pure-JVM unit tests
./gradlew detekt lintDebug   # static checks
```

## License

Apache-2.0. See [LICENSE](LICENSE).

"OPlus", "OnePlus", "OxygenOS", and "ColorOS" are trademarks of their
respective owners. This project is independent and not affiliated with or
endorsed by OPlus/OnePlus.
