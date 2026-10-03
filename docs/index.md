# Kafi Kafi

Kafi Kafi is a desktop client for inspecting and operating Apache Kafka clusters. It connects directly from the desktop application to Kafka and keeps connection profiles on the local computer; no Kafi Kafi server is required.

## What you can do

- Connect with `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL` profiles.
- Review cluster metadata, brokers, topics, partitions, replicas, and topic configuration.
- Search and create topics.
- Read, pause, filter, and inspect messages from a chosen position or partition.
- Produce records with an optional key and partition.
- Review consumer group membership and lag, reset offsets, and delete inactive groups.
- Start a local single-node Kafka KRaft container for development through Podman or Docker.

## Get started

1. [Install or run Kafi Kafi and connect to a cluster](guides/getting-started.md).
2. [Browse topics, read messages, and produce records](guides/messages.md).
3. [Inspect and manage consumer groups](guides/consumer-groups.md).

For the system boundary and module responsibilities, see the [architecture overview](architecture/overview.md).

## Local data and credentials

The Tauri runtime uses versioned JSON in the platform application-data directory and the system credential service. [Credential import and recovery](security/credentials.md) explains explicit migration from existing legacy user files.

For source builds and verification, see [development](development.md). For artifacts and release gates, see [releasing](releasing.md). For version changes and known gaps, see [release notes](releases/index.md).
