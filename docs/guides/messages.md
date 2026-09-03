# Read and produce messages

## Open a topic

1. Connect to a cluster.
2. Open `Topics`.
3. Double-click the topic, then open its `Messages` tab.

## Read messages

Choose a start position:

- `Latest` reads records appended after the session starts.
- `Earliest` reads from the beginning of the retained log.
- `Specific Offset` uses the same offset for every selected partition.
- `Timestamp` starts at the first available record at or after the supplied Unix timestamp in milliseconds.

`Partition = All` reads every partition in the topic. Select a single partition before clicking `Start` if you want to limit the session. The start position and partition selector remain locked while the session is active; click `Stop` before changing them.

`Pause` suspends polling without closing the session, `Resume` continues it, and `Stop` closes the consumer. Key and value filters apply to the in-memory messages immediately and do not restart the consumer. The UI retains at most the configured number of recent messages.

Open `Settings` to choose whether new message sessions start at `Latest` or `Earliest` and to set the in-memory limit from 100 to 100,000 messages. Saving changes updates the local settings file; the start-position default applies when you open a new topic message session.

## Produce a message

1. Click `Produce`.
2. Enter an optional key.
3. Leave `Partition (auto)` empty to let Kafka select a partition, or enter an existing partition number.
4. Enter text or JSON in `Value`.
5. Click `Send message`.

After the broker acknowledges the record, the composer shows the actual partition and offset. An empty key is sent as `null`; an empty value is sent as a zero-byte array.

## Recover from errors

Connection, polling, and production failures appear next to the affected action. Polling retries after the session's poll interval. To retry a failed production attempt, correct the input or restore the connection, then click `Send message` again.
