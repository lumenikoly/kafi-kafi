# Light Kafka

Light Kafka is a desktop client for working with Apache Kafka clusters. It connects directly from your computer, so you can inspect a cluster, browse and produce messages, and manage consumer groups without deploying a separate backend.

## Features

- Save multiple connection profiles with `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL` security.
- Inspect cluster metadata, brokers, topics, partitions, replicas, and topic configuration.
- Search topics, hide internal topics, and create topics with custom partition, replication, and configuration values.
- Read messages from the latest offset, earliest offset, a specific offset, or a timestamp.
- Pause and resume consumption, select a partition, and filter loaded messages by key or value.
- Inspect message headers and complete JSON or text values, and identify binary payloads by size.
- Produce records with an optional key and partition.
- Inspect consumer group members, assignments, committed offsets, and lag; reset offsets or delete inactive groups.
- Start a local single-node Kafka KRaft container through Podman or Docker on systems with a POSIX-compatible shell.

Connection profiles and settings stay in `~/.lightkafka`. Password fields are stored separately in an AES-GCM encrypted file.

## Install

Published releases provide native downloads on the [GitHub Releases](https://github.com/lumenikoly/kafi-kafi/releases) page:

- Linux: AppImage
- Windows: MSI installer and portable ZIP
- macOS: DMG image

Download the file for your operating system and launch it normally. On Linux, make the AppImage executable first:

```bash
chmod +x LightKafkaViewer-*.AppImage
./LightKafkaViewer-*.AppImage
```

## Run from source

Install JDK 25, then use the included Gradle wrapper. You do not need to install Gradle separately.

```bash
git clone https://github.com/lumenikoly/kafi-kafi.git
cd kafi-kafi
./gradlew :app-desktop:run
```

On Windows, run `gradlew.bat :app-desktop:run` instead.

To connect, open **Connections**, create a profile with at least one Kafka bootstrap server, test it, save it, and click **Connect**. Podman or Docker is only required for the built-in local Kafka launcher and integration tests. The launcher also requires a POSIX-compatible `sh`, so it does not run in a standard Windows environment.

## Documentation

The [documentation](docs/index.md) covers connection setup, local Kafka, topic messages, consumer groups, and the application architecture.

## Development

```bash
./gradlew ktlintCheck detektAll test
./gradlew :core-kafka:integrationTest
./gradlew :app-desktop:packageDistributionForCurrentOS
```

Integration tests require a running Docker-compatible container engine. The packaging command creates the native format configured for the current operating system.

Maintainers publish a release from **Actions → Release Build → Run workflow** on the `main` branch. The required tag must use the `vX.Y.Z` format; the workflow builds every native package and creates the tag and GitHub Release from the selected `main` commit.
