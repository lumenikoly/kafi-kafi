use crate::{
    error::{AppError, Result},
    ipc::dto::{ProduceRequest, ProduceResult},
    kafka::connection::Connection,
};
use rdkafka::{
    message::{Header, OwnedHeaders},
    producer::FutureRecord,
};
pub async fn produce(connection: &Connection, request: ProduceRequest) -> Result<ProduceResult> {
    super::admin::validate_topic(&request.topic)?;
    if request.partition.is_some_and(|p| p < 0) {
        return Err(AppError::invalid("Partition must be non-negative."));
    }
    let mut headers = OwnedHeaders::new();
    for header in &request.headers {
        headers = headers.insert(Header {
            key: &header.key,
            value: header.value.as_deref(),
        });
    }
    let mut record: FutureRecord<'_, str, str> = FutureRecord::to(&request.topic).headers(headers);
    if let Some(value) = &request.value {
        record = record.payload(value);
    }
    if let Some(key) = &request.key {
        record = record.key(key);
    }
    if let Some(partition) = request.partition {
        record = record.partition(partition);
    }
    let delivery = tokio::time::timeout(
        super::TIMEOUT,
        connection.producer.send(record, super::TIMEOUT),
    )
    .await
    .map_err(|_| {
        AppError::new(
            "TIMEOUT",
            "Producer delivery timed out; verify the topic before retrying.",
        )
    })?
    .map_err(|(e, _)| AppError::from(e))?;
    Ok(ProduceResult {
        partition: delivery.partition,
        offset: delivery.offset,
        timestamp: delivery.timestamp.to_millis(),
    })
}
