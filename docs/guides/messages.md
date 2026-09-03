# Browse topics and work with messages

The **Topics** workspace lets you find topics, inspect their metadata, consume retained records, and produce new records.

## Find or create a topic

After connecting to a cluster, open **Topics**. Search by name or enable **Hide internal** to exclude topics that Kafka identifies as internal.

To create a topic:

1. Click **Create Topic**.
2. Enter the topic name, partition count, and replication factor.
3. Add optional Kafka topic configuration name-value pairs.
4. Click **Create Topic**.

The cluster validates the request and your permissions. Light Kafka shows broker errors without changing the form so you can correct the values and retry.

## Inspect a topic

Double-click a topic to open it. The detail view contains three tabs:

- **Messages** reads and produces records.
- **Partitions** shows leaders, replicas, and in-sync replicas.
- **Config** shows the topic configuration and marks default, read-only, and sensitive values. Configuration is read-only.

## Read messages

Choose a start position before starting the consumer:

- **Latest** reads records appended after the session starts.
- **Earliest** reads from the beginning of the retained log.
- **Specific Offset** applies one non-negative offset to the selected partitions.
- **Timestamp** starts at the first available record at or after the supplied Unix timestamp in milliseconds.

Select **All** to read every partition or choose one partition, then click **Start**. The position and partition controls remain locked while the session is active. **Pause** suspends polling, **Resume** continues it, and **Stop** closes the consumer session.

The key and value filters search the messages already held in memory and do not restart Kafka consumption. Click a message to inspect its headers and complete JSON or text value. Valid JSON is formatted for readability; non-UTF-8 payloads are identified as binary and shown only by size.

Light Kafka keeps only the most recent messages up to the configured memory limit. Open **Settings** to select the default start position and set a limit from 100 to 100,000 messages. These defaults apply when you open a new topic message session.

## Produce a message

1. Click **Produce** in the **Messages** tab.
2. Enter an optional key.
3. Leave **Partition (auto)** empty to let Kafka choose a partition, or enter an existing partition number.
4. Enter the text or JSON value.
5. Click **Send message**.

After Kafka acknowledges the record, Light Kafka shows its partition and offset. An empty key is sent as `null`; an empty value is sent as a zero-byte value.

Connection, polling, and production errors appear beside the affected action. Polling retries after the consumer poll interval. To retry production, correct the input or restore the connection and click **Send message** again.
