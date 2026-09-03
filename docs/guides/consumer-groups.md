# Inspect and manage consumer groups

The **Consumer Groups** workspace shows each group's state, active members, assignments, committed offsets, end offsets, and lag by partition.

## Inspect a group

1. Connect to a Kafka cluster and open **Consumer Groups**.
2. Search by group ID or click **Refresh** to reload the list.
3. Select a group to view its details.

An empty member list means the latest group details contain no active members. A group can exist without committed offsets, in which case there is no lag table to display.

## Reset offsets

Resetting offsets changes where consumers in the group continue reading. Stop all consumers that use the group before making this change.

1. Select the group and click **Reset offsets**.
2. Enter a topic.
3. Choose **Earliest**, **Latest**, **Offset**, or **Timestamp**.
4. Enter a non-negative absolute offset or Unix timestamp in milliseconds when the selected mode requires it.
5. Confirm the reset.

The reset applies to every partition of the topic. Timestamp mode uses the first available offset at or after the requested time; if a partition has no later record, it uses that partition's end offset. Kafka validation errors remain visible so you can correct the input or stop active consumers and retry.

## Delete a group

Select the group, click **Delete group**, and confirm. This removes the group and its committed offsets. Kafka rejects the request while the group has active members. After a successful deletion, Light Kafka reloads the group list.
