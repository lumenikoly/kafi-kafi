# Inspect and manage consumer groups

Consumer Groups lists group state, active member count and the topics represented by active assignments. Selecting a group loads members, client IDs/hosts, assignments, committed offsets, end offsets and partition lag. Refresh is manual.

## Inspect a group

Connect, open **Consumer Groups**, search by group ID and select its name. Empty member lists indicate no active members in the latest response. Empty groups may still retain committed offsets. A group's list topic count describes active assignments; the detail finds commits across cluster topics even without assignments.

## Reset offsets

Stop all members that use the group before resetting its offsets. Enter a topic and optional partition, select earliest/latest/offset/timestamp, and supply an absolute offset or local date/time when requested. Click **Preview reset** to review the group, affected partitions, old offsets and new offsets. Confirm to apply that exact preview.

Timestamp mode chooses the first record at or after the requested time, or the partition end when no later record exists. The backend refuses a reset if the active connection changed, the preview expired, members became active, or committed offsets differ from the preview. Generate and review a fresh preview after such a failure. Kafka remains authoritative for concurrent group changes and permissions.

## Delete a group

Click **Delete group**, review the selected group and confirm. The backend checks for active members and Kafka rejects groups it cannot delete. Successful deletion reloads the list and clears the selected group. Deletion removes the group's committed offsets, not topic records.
