# Browse topics and work with messages

Topics lets you search retained Kafka data and operate on topic configuration. Lists use virtual tables so visible rows remain bounded as the cluster grows.

## Find or create a topic

After connecting, open **Topics**. Search by name; enable **Internal topics** to include names beginning with `__`. Click **Create topic**, enter a valid name and positive partition/replication counts, and optionally enter configuration as one `name=value` per line. Kafka still enforces availability, replication and permissions. Creation refreshes the list.

Click a topic name to open its workspace tab. **Partitions** shows leaders, replicas, in-sync replicas and earliest/latest offsets. **Configuration** shows values and default/read-only/sensitive flags; selecting a writable non-sensitive parameter allows **Apply**. **Delete topic** requires confirmation and permanently removes its records.

## Read messages

Select latest, earliest, offset or timestamp, optionally choose a partition, then click **Start**. Timestamp input uses local date/time and is sent as Unix milliseconds. Explicit offsets must fall inside the retained range for every selected partition. Timestamp positions with no later record use the partition end.

**Pause** suspends consumption; **Resume** continues; **Stop** ends the session and frees records. Navigating between topic sections keeps the session alive. Closing its workspace tab or successfully switching profiles stops it. Inspection uses temporary technical groups with automatic commits and offset storage disabled, so it does not advance user-group offsets.

Key, value and partition filters run in Rust against the retained records and incoming records. Changing a filter rebuilds the displayed snapshot without restarting the consumer. Settings applies both a record limit and byte limit to new sessions. The status shows retained and evicted counts; once a record leaves the buffer, its detail is unavailable.

Click a record's partition cell to open its inspector. Small JSON can be formatted or displayed raw; text displays as supplied. Binary data shows a bounded hexadecimal preview and can be exported as original bytes. Values larger than 64 KiB initially show a preview and **Load full value**; large JSON is not automatically formatted. Header values use bounded previews. Record export writes only to the location selected in a native save dialog.

Native consumer failures stop the session and show an error. Restore the connection and click Start to retry.

## Produce a record

Open the topic's **Produce** section. Enter an optional key, optional partition, value and optional headers as one `name=value` per line, then click **Send record**. Kafka's acknowledgement shows partition, offset and timestamp. An empty key is sent as null; an empty value is zero-length text. Enable **Null value (tombstone)** to send a null value explicitly. Production reuses the active connection's producer.

A delivery timeout may be ambiguous: verify whether the record arrived before retrying to avoid an unintended duplicate.
