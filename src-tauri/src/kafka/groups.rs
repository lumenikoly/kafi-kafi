use crate::{
    error::{AppError, Result},
    ipc::dto::*,
    kafka::connection::Connection,
};
use rdkafka::{
    Offset, TopicPartitionList,
    admin::AdminOptions,
    consumer::{BaseConsumer, CommitMode, Consumer},
};
use std::collections::BTreeSet;

// Kafka consumer assignment wire format: int16 version, int32 count,
// repeated UTF-8 topic + int32 partition array, optional userdata.
pub fn decode_assignment(mut bytes: &[u8]) -> Result<Vec<Assignment>> {
    fn take<'a>(bytes: &mut &'a [u8], n: usize) -> Result<&'a [u8]> {
        if n > bytes.len() {
            return Err(AppError::invalid("Malformed group assignment."));
        }
        let (head, tail) = bytes.split_at(n);
        *bytes = tail;
        Ok(head)
    }
    fn count(bytes: &mut &[u8]) -> Result<usize> {
        let b = take(bytes, 4)?;
        let n = i32::from_be_bytes(b.try_into().expect("four bytes"));
        if !(0..=100_000).contains(&n) {
            return Err(AppError::invalid("Malformed group assignment count."));
        }
        Ok(n as usize)
    }
    let version = take(&mut bytes, 2)?;
    if i16::from_be_bytes(version.try_into().expect("two bytes")) != 0 {
        return Err(AppError::invalid(
            "Unsupported group assignment protocol version.",
        ));
    }
    let n = count(&mut bytes)?;
    let mut assignments = Vec::new();
    for _ in 0..n {
        let b = take(&mut bytes, 2)?;
        let length = i16::from_be_bytes(b.try_into().expect("two bytes"));
        if length < 0 {
            return Err(AppError::invalid("Malformed topic in group assignment."));
        }
        let topic = std::str::from_utf8(take(&mut bytes, length as usize)?)
            .map_err(|_| AppError::invalid("Invalid assignment topic encoding."))?
            .to_owned();
        let n = count(&mut bytes)?;
        let mut partitions = Vec::new();
        for _ in 0..n {
            partitions.push(i32::from_be_bytes(
                take(&mut bytes, 4)?.try_into().expect("four bytes"),
            ));
        }
        assignments.push(Assignment { topic, partitions });
    }
    Ok(assignments)
}
pub fn list(connection: &Connection) -> Result<Vec<ConsumerGroup>> {
    let groups = connection
        .admin
        .inner()
        .fetch_group_list(None, super::TIMEOUT)?;
    groups
        .groups()
        .iter()
        .map(|group| {
            let mut topics = BTreeSet::new();
            for member in group.members() {
                if let Some(bytes) = member.assignment() {
                    for assignment in decode_assignment(bytes)? {
                        topics.insert(assignment.topic);
                    }
                }
            }
            Ok(ConsumerGroup {
                id: group.name().into(),
                state: group.state().into(),
                member_count: group.members().len(),
                topic_count: topics.len(),
            })
        })
        .collect()
}
fn group_consumer(connection: &Connection, group: &str) -> Result<BaseConsumer> {
    if group.trim().is_empty() || group.contains('\0') {
        return Err(AppError::invalid("Enter a group ID."));
    }
    let mut config = super::config::inspection(&connection.config);
    config.set("group.id", group);
    Ok(config.create()?)
}
pub fn describe(connection: &Connection, id: &str) -> Result<GroupDetail> {
    let groups = connection
        .admin
        .inner()
        .fetch_group_list(Some(id), super::TIMEOUT)?;
    let group = groups
        .groups()
        .iter()
        .find(|g| g.name() == id)
        .ok_or_else(|| AppError::new("GROUP_NOT_FOUND", "Consumer group does not exist."))?;
    let members = group
        .members()
        .iter()
        .map(|m| {
            Ok(GroupMember {
                id: m.id().into(),
                client_id: m.client_id().into(),
                client_host: m.client_host().into(),
                assignments: m
                    .assignment()
                    .map(decode_assignment)
                    .transpose()?
                    .unwrap_or_default(),
            })
        })
        .collect::<Result<Vec<_>>>()?;
    let consumer = group_consumer(connection, id)?;
    // Query all known partitions to find commits even for inactive groups with no assignments.
    let metadata = connection
        .admin
        .inner()
        .fetch_metadata(None, super::TIMEOUT)?;
    let mut partitions = TopicPartitionList::new();
    for topic in metadata
        .topics()
        .iter()
        .filter(|t| !t.name().starts_with("__"))
    {
        for p in topic.partitions() {
            partitions.add_partition(topic.name(), p.id());
        }
    }
    let committed = consumer.committed_offsets(partitions, super::TIMEOUT)?;
    let mut offsets = Vec::new();
    for p in committed.elements() {
        p.error()?;
        if let Offset::Offset(offset) = p.offset() {
            let (_, end_offset) =
                consumer.fetch_watermarks(p.topic(), p.partition(), super::TIMEOUT)?;
            offsets.push(GroupOffset {
                topic: p.topic().into(),
                partition: p.partition(),
                committed_offset: Some(offset),
                end_offset,
                lag: Some(end_offset.saturating_sub(offset).max(0)),
            });
        }
    }
    Ok(GroupDetail {
        id: id.into(),
        state: group.state().into(),
        members,
        offsets,
    })
}
pub fn preview_reset(connection: &Connection, request: ResetRequest) -> Result<ResetPreview> {
    let detail = describe(connection, &request.group)?;
    if !detail.members.is_empty() {
        return Err(AppError::invalid(
            "Stop all group members before resetting offsets.",
        ));
    }
    let partitions = super::admin::partitions(connection, &request.topic)?;
    if request
        .partition
        .is_some_and(|p| !partitions.iter().any(|part| part.id == p))
    {
        return Err(AppError::invalid("Partition does not exist."));
    }
    let consumer = group_consumer(connection, &request.group)?;
    let mut timestamps = TopicPartitionList::new();
    if request.position == "timestamp" {
        let timestamp = request
            .timestamp
            .filter(|t| *t >= 0)
            .ok_or_else(|| AppError::invalid("Enter a non-negative timestamp."))?;
        for p in &partitions {
            timestamps.add_partition_offset(&request.topic, p.id, Offset::Offset(timestamp))?;
        }
        timestamps = consumer.offsets_for_times(timestamps, super::TIMEOUT)?;
    }
    let mut changes = Vec::new();
    for p in partitions
        .into_iter()
        .filter(|p| request.partition.is_none_or(|id| id == p.id))
    {
        let new_offset = match request.position.as_str() {
            "earliest" => p.earliest_offset,
            "latest" => p.latest_offset,
            "offset" => request
                .offset
                .filter(|o| *o >= p.earliest_offset && *o <= p.latest_offset)
                .ok_or_else(|| {
                    AppError::invalid("Offset is outside the retained partition range.")
                })?,
            "timestamp" => match timestamps
                .find_partition(&request.topic, p.id)
                .map(|e| e.offset())
            {
                Some(Offset::Offset(o)) => o,
                _ => p.latest_offset,
            },
            _ => return Err(AppError::invalid("Unknown reset position.")),
        };
        let old_offset = detail
            .offsets
            .iter()
            .find(|o| o.topic == request.topic && o.partition == p.id)
            .and_then(|o| o.committed_offset);
        changes.push(OffsetChange {
            topic: request.topic.clone(),
            partition: p.id,
            old_offset,
            new_offset,
        });
    }
    Ok(ResetPreview {
        token: uuid::Uuid::new_v4().to_string(),
        group: request.group,
        generation: connection.generation.clone(),
        changes,
    })
}
pub fn reset(connection: &Connection, preview: &ResetPreview) -> Result<()> {
    if preview.generation != connection.generation {
        return Err(AppError::invalid(
            "Connection changed. Review a new reset preview.",
        ));
    }
    let detail = describe(connection, &preview.group)?;
    if !detail.members.is_empty() {
        return Err(AppError::invalid(
            "Group has active members; offsets cannot be reset.",
        ));
    }
    let mut offsets = TopicPartitionList::new();
    for change in &preview.changes {
        let current = detail
            .offsets
            .iter()
            .find(|o| o.topic == change.topic && o.partition == change.partition)
            .and_then(|o| o.committed_offset);
        if current != change.old_offset {
            return Err(AppError::invalid(
                "Offsets changed since the preview. Review a new preview.",
            ));
        }
        offsets.add_partition_offset(
            &change.topic,
            change.partition,
            Offset::Offset(change.new_offset),
        )?;
    }
    group_consumer(connection, &preview.group)?.commit(&offsets, CommitMode::Sync)?;
    Ok(())
}
pub async fn delete(connection: &std::sync::Arc<Connection>, id: &str) -> Result<()> {
    let c = connection.clone();
    let group_id = id.to_owned();
    let active = tokio::task::spawn_blocking(move || -> Result<bool> {
        let list = c
            .admin
            .inner()
            .fetch_group_list(Some(&group_id), super::TIMEOUT)?;
        let group = list
            .groups()
            .iter()
            .find(|g| g.name() == group_id)
            .ok_or_else(|| AppError::new("GROUP_NOT_FOUND", "Consumer group does not exist."))?;
        Ok(!group.members().is_empty())
    })
    .await
    .map_err(|_| AppError::new("INTERNAL_ERROR", "Cannot check consumer group membership."))??;
    if active {
        return Err(AppError::invalid(
            "Stop the group members before deleting the group.",
        ));
    }
    for result in connection
        .admin
        .delete_groups(
            &[id],
            &AdminOptions::new().operation_timeout(Some(super::TIMEOUT)),
        )
        .await?
    {
        result.map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e.1)))?;
    }
    Ok(())
}
