# Kafi Kafi

Kafi Kafi is a desktop client for working with Apache Kafka clusters. It connects directly from your computer, so you can inspect a cluster, browse and produce messages, and manage consumer groups without deploying a separate backend.

## Features

- Save multiple connection profiles with `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL` security.
- Inspect cluster metadata, brokers, topics, partitions, replicas, and topic configuration.
- Search topics, hide internal topics, and create topics with custom partition, replication, and configuration values.
- Read messages from the latest offset, earliest offset, a specific offset, or a timestamp.
- Pause and resume consumption, select a partition, and filter loaded messages by key or value.
- Inspect message headers and complete JSON or text values, and identify binary payloads by size.
- Produce records with an optional key and partition.
- Inspect consumer group members, assignments, committed offsets, and lag; reset offsets or delete inactive groups.
- Start a local single-node Kafka KRaft container through Podman or Docker.

The Tauri runtime stores profiles in the platform application-data directory and passwords in the system credential service. Settings offers explicit import from existing legacy user files in `~/.lightkafka`. See [credential migration](docs/security/credentials.md).

## Migration status

The Rust/Tauri runtime lives in `src-tauri/` and the React interface in `src/`. This is the only application implementation; the Kotlin/JVM source and Gradle workflows have been removed. Read-only import of existing user data remains available. [Migration acceptance](docs/work/TASK-MIGRATION-001.md) records the remaining platform and performance checks.

## Install

Tauri release builds produce these native packages for distribution through [GitHub Releases](https://github.com/lumenikoly/kafi-kafi/releases). Check release notes when downloading an older published version:

- Linux: AppImage
- Windows: NSIS installer and portable ZIP
- macOS: DMG image

Download the file for your operating system and launch it normally. On Linux, make the AppImage executable first:

```bash
chmod +x KafiKafi-*.AppImage
./KafiKafi-*.AppImage
```

## Run from source

Install the [native development prerequisites](docs/development.md), Node 22.18.0, pnpm 11.25.0 and the pinned Rust toolchain.

```sh
pnpm install --frozen-lockfile
pnpm tauri dev
```

Create a profile in Connections, test it, save it and connect. Docker or Podman is needed only for optional local Kafka and broker integration tests.

## Documentation

The [documentation](docs/index.md) covers connection setup, local Kafka, topic messages, consumer groups, and the application architecture.

## Development

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
cargo fmt --manifest-path src-tauri/Cargo.toml --check
cargo clippy --manifest-path src-tauri/Cargo.toml --locked --all-targets --all-features -- -D warnings
cargo test --manifest-path src-tauri/Cargo.toml --locked
pnpm tauri build
```

See [development](docs/development.md) for real Kafka fixtures and [releasing](docs/releasing.md) for the SemVer-tag workflow, platform packages and SHA-256 manifests.
