# Light Kafka

Light Kafka is a desktop client for inspecting and operating Apache Kafka clusters. It connects directly from the desktop application to Kafka and keeps connection profiles on the local computer; no Light Kafka server is required.

## What you can do

- Connect with `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL` profiles.
- Review cluster metadata, brokers, topics, partitions, replicas, and topic configuration.
- Search and create topics.
- Read, pause, filter, and inspect messages from a chosen position or partition.
- Produce records with an optional key and partition.
- Review consumer group membership and lag, reset offsets, and delete inactive groups.
- Start a local single-node Kafka KRaft container for development on systems with a POSIX-compatible shell.

## Get started

1. [Install or run Light Kafka and connect to a cluster](guides/getting-started.md).
2. [Browse topics, read messages, and produce records](guides/messages.md).
3. [Inspect and manage consumer groups](guides/consumer-groups.md).

For the system boundary and module responsibilities, see the [architecture overview](architecture/overview.md).

## Local data and credentials

Light Kafka stores profiles and settings in `~/.lightkafka/storage.json`. SASL and SSL password fields are encrypted with AES-GCM and stored separately in `~/.lightkafka/secrets/secrets.json`. The application derives the encryption key from the local username, operating system name, home-directory path, and a random salt.

These files are local application storage, not a shared credential vault. Protect the user account and home directory on any computer that runs Light Kafka.
