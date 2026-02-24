# Light Kafka Viewer

Desktop Kafka client built with Kotlin and Compose Multiplatform.

## Prerequisites

- Java 25 (SDKMAN candidate: `25.0.2-librca`)
- Gradle 9.3.1

If you use SDKMAN, this repo includes `.sdkmanrc` so you can run:

```bash
sdk env install
sdk env
```

## Modules

- `core-kafka` - Kafka service abstractions and domain contracts
- `core-storage` - local persistence contracts and models
- `ui` - shared Compose Desktop UI components
- `app-desktop` - desktop entrypoint and packaging configuration

## Common commands

```bash
./gradlew build
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
