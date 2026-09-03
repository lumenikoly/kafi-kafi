# Light Kafka Viewer

Desktop Kafka client built with Kotlin and Compose Multiplatform.

## Prerequisites

- JDK 25
- Gradle 9.3.1

## Modules

- `core-kafka` - Kafka service abstractions and domain contracts
- `core-storage` - local persistence contracts and models
- `ui` - shared Compose Desktop UI components
- `app-desktop` - desktop entrypoint and packaging configuration

## Documentation

Project documentation lives in [`docs/`](docs/index.md) and is validated by Toudocu:

```bash
toudocu check ./docs --strict
toudocu serve ./docs
```

## Common commands

```bash
./gradlew build
./gradlew test
./gradlew :core-kafka:integrationTest
./gradlew :app-desktop:run
./gradlew ktlintCheck
./gradlew detektAll
./gradlew :app-desktop:packageDistributionForCurrentOS
```

## Packaging

Desktop packaging is configured via Compose Desktop (jpackage backend):

- `DMG` for macOS
- `MSI` for Windows
- `DEB` for Linux

Use:

```bash
./gradlew :app-desktop:createDistributable
./gradlew :app-desktop:packageDistributionForCurrentOS
```

## GitHub workflows

- CI workflow: `.github/workflows/ci.yml`
  - Runs `ktlintCheck`, `detektAll`, and unit tests on pushes/PRs
  - Runs Kafka Testcontainers integration tests in a dedicated job
- Release workflow: `.github/workflows/release.yml`
  - Triggered on tags matching `v*`
  - Builds and uploads release assets in this set:
    - Windows: MSI (main) + ZIP (portable)
    - Linux: AppImage
    - macOS: DMG
