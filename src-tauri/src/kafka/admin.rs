use crate::{
    error::{AppError, Result},
    ipc::dto::*,
    kafka::connection::Connection,
};
use rdkafka::{
    admin::{
        AdminOptions, AlterConfig, ConfigSource, NewTopic, ResourceSpecifier, TopicReplication,
    },
    consumer::{BaseConsumer, Consumer},
};
use std::collections::BTreeMap;

pub fn validate_topic(name: &str) -> Result<()> {
    if name.is_empty()
        || name.len() > 249
        || [".", ".."].contains(&name)
        || !name
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || b"._-".contains(&c))
    {
        return Err(AppError::invalid(
            "Topic name must contain 1–249 ASCII letters, digits, '.', '_' or '-'.",
        ));
    }
    Ok(())
}
pub fn cluster(connection: &Connection) -> Result<Cluster> {
    super::metadata::describe(connection)
}
pub fn list(connection: &Connection) -> Result<Vec<Topic>> {
    let metadata = connection
        .admin
        .inner()
        .fetch_metadata(None, super::TIMEOUT)?;
    Ok(metadata
        .topics()
        .iter()
        .filter(|t| t.error().is_none())
        .map(|t| Topic {
            name: t.name().into(),
            partitions: t.partitions().len(),
            internal: t.name().starts_with("__"),
        })
        .collect())
}
pub fn partitions(connection: &Connection, topic: &str) -> Result<Vec<Partition>> {
    validate_topic(topic)?;
    let metadata = connection
        .admin
        .inner()
        .fetch_metadata(Some(topic), super::TIMEOUT)?;
    let t = metadata
        .topics()
        .iter()
        .find(|t| t.name() == topic && t.error().is_none())
        .ok_or_else(|| AppError::new("TOPIC_NOT_FOUND", "The topic does not exist."))?;
    let consumer: BaseConsumer = super::config::inspection(&connection.config).create()?;
    t.partitions()
        .iter()
        .map(|p| {
            let (earliest_offset, latest_offset) =
                consumer.fetch_watermarks(topic, p.id(), super::TIMEOUT)?;
            Ok(Partition {
                id: p.id(),
                leader: p.leader(),
                replicas: p.replicas().to_vec(),
                isr: p.isr().to_vec(),
                earliest_offset,
                latest_offset,
            })
        })
        .collect()
}
pub async fn configuration(connection: &Connection, topic: &str) -> Result<Vec<ConfigEntry>> {
    let resources = connection
        .admin
        .describe_configs(
            [&ResourceSpecifier::Topic(topic)],
            &AdminOptions::new().operation_timeout(Some(super::TIMEOUT)),
        )
        .await?;
    let resource = resources
        .into_iter()
        .next()
        .ok_or_else(|| AppError::new("TOPIC_NOT_FOUND", "The topic does not exist."))?
        .map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e)))?;
    Ok(resource
        .entries
        .into_iter()
        .map(|e| ConfigEntry {
            name: e.name,
            value: if e.is_sensitive { None } else { e.value },
            is_default: e.is_default,
            read_only: e.is_read_only,
            sensitive: e.is_sensitive,
        })
        .collect())
}
pub async fn create(connection: &Connection, request: CreateTopic) -> Result<()> {
    validate_topic(&request.name)?;
    if request.partitions < 1 || request.replication_factor < 1 {
        return Err(AppError::invalid(
            "Partitions and replication factor must be positive.",
        ));
    }
    let mut topic = NewTopic::new(
        &request.name,
        request.partitions,
        TopicReplication::Fixed(request.replication_factor),
    );
    for (key, value) in &request.config {
        topic = topic.set(key, value);
    }
    for result in connection
        .admin
        .create_topics(
            [&topic],
            &AdminOptions::new().operation_timeout(Some(super::TIMEOUT)),
        )
        .await?
    {
        result.map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e.1)))?;
    }
    Ok(())
}
pub async fn delete(connection: &Connection, topic: &str) -> Result<()> {
    validate_topic(topic)?;
    for result in connection
        .admin
        .delete_topics(
            &[topic],
            &AdminOptions::new().operation_timeout(Some(super::TIMEOUT)),
        )
        .await?
    {
        result.map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e.1)))?;
    }
    Ok(())
}
pub async fn update_config(
    connection: &Connection,
    topic: &str,
    updates: BTreeMap<String, String>,
) -> Result<()> {
    validate_topic(topic)?;
    // AlterConfigs replaces the entire dynamic configuration, so preserve untouched overrides.
    let result = connection
        .admin
        .describe_configs([&ResourceSpecifier::Topic(topic)], &AdminOptions::new())
        .await?;
    let current = result
        .into_iter()
        .next()
        .ok_or_else(|| AppError::invalid("No configuration returned."))?
        .map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e)))?;
    if updates.keys().any(|key| {
        !current
            .entries
            .iter()
            .any(|e| &e.name == key && !e.is_read_only && !e.is_sensitive)
    }) {
        return Err(AppError::invalid(
            "Only known writable non-sensitive configuration can be changed.",
        ));
    }
    if current
        .entries
        .iter()
        .any(|e| e.source == ConfigSource::DynamicTopic && e.is_sensitive)
    {
        return Err(AppError::invalid(
            "Cannot safely replace configuration containing hidden dynamic values.",
        ));
    }
    let mut values: BTreeMap<String, String> = current
        .entries
        .into_iter()
        .filter(|e| e.source == ConfigSource::DynamicTopic)
        .filter_map(|e| e.value.map(|v| (e.name, v)))
        .collect();
    values.extend(updates);
    let mut config = AlterConfig::new(ResourceSpecifier::Topic(topic));
    for (key, value) in &values {
        config = config.set(key, value);
    }
    for result in connection
        .admin
        .alter_configs(
            [&config],
            &AdminOptions::new().operation_timeout(Some(super::TIMEOUT)),
        )
        .await?
    {
        result.map_err(|e| AppError::from(rdkafka::error::KafkaError::AdminOp(e.1)))?;
    }
    Ok(())
}
