# Inspect and manage consumer groups

## Open a group

1. Connect to a Kafka cluster.
2. Open `Consumer Groups` in the sidebar.
3. Search by group ID or click `Refresh` to reload the list.
4. Select a group to inspect its state, members, assignments, committed offsets, end offsets, and lag per partition.

An empty member list means that the group currently has no active members. A missing committed-offset list means that the group has no stored offsets to display.

## Reset offsets

1. Stop the consumers that use the group.
2. Select the group and click `Reset offsets`.
3. Enter a topic and choose `Earliest`, `Latest`, `Offset`, or `Timestamp`.
4. Confirm the reset.

The reset applies to every partition of the selected topic. `Offset` accepts a non-negative absolute offset. `Timestamp` accepts Unix time in milliseconds and selects the first available offset at or after that time; when a partition has no later record, it uses that partition's end offset. Broker validation failures remain visible in the group details so you can correct the input or stop active consumers and retry.

## Delete a group

Select the group, click `Delete group`, and confirm the destructive action. Kafka rejects deletion while the group has active members; after a successful deletion, the application reloads the group list.
