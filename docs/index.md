# Light Kafka

Light Kafka is a desktop client for connecting to Kafka clusters, inspecting topics, reading messages, and producing records. The application is built with Kotlin and Compose Desktop. It stores connection profiles locally and keeps their SASL and SSL password fields in a separate encrypted store.

## Start here

- [Run the application and connect](guides/getting-started.md)
- [Read and produce messages](guides/messages.md)
- [Inspect and manage consumer groups](guides/consumer-groups.md)
- [Understand the architecture](architecture/overview.md)

## Core capabilities

- Connection profiles for `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, and `SASL_SSL`; SASL mechanism choices are `PLAIN`, `SCRAM_SHA_256`, `SCRAM_SHA_512`, and `OAUTHBEARER`.
- Cluster, broker, and topic views, including topic creation, partition details, and read-only topic configuration.
- Message consumption from the latest offset, earliest offset, a specific offset, or a timestamp.
- In-memory filtering by partition, key, and value.
- Record production to a broker-selected or explicit partition.
- Consumer group search, state, members, partition lag, offset reset, and deletion.
- Local settings for the default consumer start position and in-memory message limit.
- A local single-node Kafka KRaft launcher that uses Podman or Docker.

## Validate the documentation

```bash
toudocu check ./docs --strict
```

Start the local documentation portal with:

```bash
toudocu serve ./docs
```
