use crate::{
    domain::{AppSettings, KafkaMessage},
    error::{AppError, Result},
    ipc::dto::*,
    kafka::connection::Connection,
};
use base64::{Engine, engine::general_purpose::STANDARD};
use rdkafka::{
    Offset, TopicPartitionList,
    client::ClientContext,
    consumer::{Consumer, ConsumerContext, StreamConsumer},
    message::{Headers, Message},
};
use std::{
    collections::VecDeque,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
};
use tokio_util::sync::CancellationToken;

pub struct StreamLimits {
    pub batch_size: usize,
    pub interval_ms: u64,
    pub preview_bytes: usize,
    pub lazy_bytes: usize,
    pub in_flight: u64,
}
pub const LIMITS: StreamLimits = StreamLimits {
    batch_size: 200,
    interval_ms: 33,
    preview_bytes: 2048,
    lazy_bytes: 64 * 1024,
    in_flight: 2,
};
pub struct Buffer {
    pub messages: VecDeque<KafkaMessage>,
    bytes: usize,
    pub dropped: u64,
    count_limit: usize,
    byte_limit: usize,
}
impl Buffer {
    pub fn new(count_limit: usize, byte_limit: usize) -> Self {
        Self {
            messages: VecDeque::new(),
            bytes: 0,
            dropped: 0,
            count_limit,
            byte_limit,
        }
    }
    pub fn push(&mut self, message: KafkaMessage) {
        self.bytes += message.size();
        self.messages.push_back(message);
        while self.messages.len() > self.count_limit || self.bytes > self.byte_limit {
            if let Some(old) = self.messages.pop_front() {
                self.bytes = self.bytes.saturating_sub(old.size());
                self.dropped += 1;
            }
        }
    }
    pub fn bytes(&self) -> usize {
        self.bytes
    }
    pub fn clear(&mut self) {
        self.messages.clear();
        self.bytes = 0;
    }
}
pub fn matches(message: &KafkaMessage, filter: &MessageFilter) -> bool {
    fn contains(bytes: &Option<Vec<u8>>, query: &str) -> bool {
        query.is_empty()
            || bytes
                .as_ref()
                .is_some_and(|b| b.windows(query.len()).any(|w| w == query.as_bytes()))
    }
    filter.partition.is_none_or(|p| p == message.partition)
        && contains(&message.key, &filter.key)
        && contains(&message.value, &filter.value)
}
pub fn preview(bytes: &[u8]) -> String {
    let truncated = bytes.len() > LIMITS.preview_bytes;
    let bytes = &bytes[..bytes.len().min(LIMITS.preview_bytes)];
    match std::str::from_utf8(bytes) {
        Ok(s) => s.into(),
        Err(error) if truncated && error.error_len().is_none() => {
            String::from_utf8_lossy(&bytes[..error.valid_up_to()]).into_owned()
        }
        Err(_) => hex_preview(bytes),
    }
}
fn hex_preview(bytes: &[u8]) -> String {
    bytes
        .iter()
        .take(256)
        .map(|b| format!("{b:02x}"))
        .collect::<Vec<_>>()
        .join(" ")
}
pub fn kind(bytes: &[u8]) -> String {
    match std::str::from_utf8(bytes) {
        Ok(text)
            if bytes.len() <= LIMITS.lazy_bytes
                && serde_json::from_str::<serde_json::Value>(text).is_ok() =>
        {
            "json".into()
        }
        Ok(_) => "text".into(),
        Err(_) => "binary".into(),
    }
}
pub fn row(message: &KafkaMessage) -> MessageRow {
    MessageRow {
        id: message.id.clone(),
        partition: message.partition,
        offset: message.offset,
        timestamp: message.timestamp,
        key_preview: message.key.as_ref().map_or(String::new(), |b| preview(b)),
        value_preview: message.value.as_ref().map_or(String::new(), |b| preview(b)),
        key_size: message.key.as_ref().map_or(0, Vec::len),
        value_size: message.value.as_ref().map_or(0, Vec::len),
        value_type: message.value.as_ref().map_or("null".into(), |b| kind(b)),
        headers_count: message.headers.len(),
    }
}
fn payload(bytes: &[u8], full: bool) -> Payload {
    let kind = kind(bytes);
    let truncated = !full && bytes.len() > LIMITS.lazy_bytes;
    let text = if truncated {
        None
    } else {
        std::str::from_utf8(bytes).ok().map(str::to_owned)
    };
    Payload {
        preview: if kind == "binary" {
            hex_preview(bytes)
        } else {
            preview(bytes)
        },
        kind,
        size: bytes.len(),
        text,
        base64: if !truncated && std::str::from_utf8(bytes).is_err() {
            Some(STANDARD.encode(bytes))
        } else {
            None
        },
        truncated,
    }
}
pub struct InspectorContext;
impl ClientContext for InspectorContext {
    fn log(&self, _: rdkafka::config::RDKafkaLogLevel, _: &str, _: &str) {}
    fn error(&self, _: rdkafka::error::KafkaError, _: &str) {}
}
impl ConsumerContext for InspectorContext {}
pub struct Session {
    pub id: String,
    pub generation: String,
    pub buffer: Mutex<Buffer>,
    pub filter: Mutex<MessageFilter>,
    pub cancellation: CancellationToken,
    pub paused: AtomicBool,
    pub acknowledged: AtomicU64,
    pub revision: AtomicU64,
    pub task: Mutex<Option<tokio::task::JoinHandle<()>>>,
}
impl Session {
    pub fn detail(&self, id: &str, full: bool) -> Result<MessageDetail> {
        let buffer = self.buffer.lock().expect("buffer lock");
        let m = buffer.messages.iter().find(|m| m.id == id).ok_or_else(|| {
            AppError::new(
                "MESSAGE_EVICTED",
                "This message has left the bounded buffer.",
            )
        })?;
        Ok(MessageDetail {
            id: m.id.clone(),
            topic: m.topic.clone(),
            partition: m.partition,
            offset: m.offset,
            timestamp: m.timestamp,
            key: m.key.as_ref().map(|b| payload(b, full)),
            value: m.value.as_ref().map(|b| payload(b, full)),
            headers: m
                .headers
                .iter()
                .map(|(k, v)| Header {
                    key: k.clone(),
                    value: v.as_ref().map(|b| preview(b)),
                })
                .collect(),
        })
    }
    pub async fn stop(&self) {
        tracing::debug!(session=%self.id,"Consumer stop requested");
        self.cancellation.cancel();
        let task = self.task.lock().expect("task lock").take();
        if let Some(task) = task {
            let _ = task.await;
        }
        tracing::debug!(session=%self.id,"Consumer task joined");
        self.buffer.lock().expect("buffer lock").clear();
    }
}
pub fn start(
    connection: Arc<Connection>,
    request: StartConsumer,
    settings: AppSettings,
    send: Arc<dyn Fn(Batch) -> bool + Send + Sync>,
) -> Result<Arc<Session>> {
    super::admin::validate_topic(&request.topic)?;
    let mut config = super::config::inspection(&connection.config);
    config.set("queued.max.messages.kbytes", "8192");
    let consumer: StreamConsumer<InspectorContext> =
        config.create_with_context(InspectorContext)?;
    let partitions = super::admin::partitions(&connection, &request.topic)?;
    if request
        .partition
        .is_some_and(|p| !partitions.iter().any(|part| part.id == p))
    {
        return Err(AppError::invalid("Partition does not exist."));
    }
    let mut list = TopicPartitionList::new();
    for partition in partitions
        .iter()
        .filter(|p| request.partition.is_none_or(|id| id == p.id))
    {
        let offset = match request.position.as_str() {
            "earliest" => Offset::Offset(partition.earliest_offset),
            "latest" => Offset::Offset(partition.latest_offset),
            "offset" => Offset::Offset(
                request
                    .offset
                    .filter(|o| *o >= partition.earliest_offset && *o <= partition.latest_offset)
                    .ok_or_else(|| {
                        AppError::invalid("Offset is outside the retained partition range.")
                    })?,
            ),
            "timestamp" => {
                Offset::Offset(request.timestamp.filter(|t| *t >= 0).ok_or_else(|| {
                    AppError::invalid("Enter a non-negative Unix timestamp in milliseconds.")
                })?)
            }
            _ => return Err(AppError::invalid("Unknown start position.")),
        };
        list.add_partition_offset(&request.topic, partition.id, offset)?;
    }
    if request.position == "timestamp" {
        list = consumer.offsets_for_times(list, super::TIMEOUT)?;
        for mut p in list.elements() {
            if p.offset() == Offset::Invalid {
                let end = partitions
                    .iter()
                    .find(|partition| partition.id == p.partition())
                    .expect("validated partition")
                    .latest_offset;
                p.set_offset(Offset::Offset(end))?;
            }
        }
    }
    consumer.assign(&list)?;
    let session = Arc::new(Session {
        id: uuid::Uuid::new_v4().to_string(),
        generation: connection.generation.clone(),
        buffer: Mutex::new(Buffer::new(
            settings.message_buffer_limit,
            settings.message_buffer_bytes,
        )),
        filter: Mutex::new(MessageFilter::default()),
        cancellation: CancellationToken::new(),
        paused: AtomicBool::new(false),
        acknowledged: AtomicU64::new(0),
        revision: AtomicU64::new(0),
        task: Mutex::new(None),
    });
    let active = session.clone();
    let task = tokio::spawn(async move {
        let mut interval =
            tokio::time::interval(std::time::Duration::from_millis(LIMITS.interval_ms));
        interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
        let mut message_id = 0u64;
        let mut sequence = 0u64;
        let mut cursor = 0u64;
        let mut revision = u64::MAX;
        let mut was_paused = false;
        let mut last_dropped = u64::MAX;
        let mut last_sent_paused = false;
        let mut last_error = None;
        tracing::info!(session=%active.id,"Consumer session started");
        loop {
            tokio::select! {
                biased;
                _=active.cancellation.cancelled()=>break,
                _=interval.tick()=> {
                    let paused=active.paused.load(Ordering::Relaxed);
                    if paused!=was_paused {
                        let _=if paused {consumer.pause(&list)} else {consumer.resume(&list)};
                        was_paused=paused;
                    }
                    if sequence.saturating_sub(active.acknowledged.load(Ordering::Acquire))>=LIMITS.in_flight {continue;}
                    let current_revision=active.revision.load(Ordering::Acquire);
                    let reset=revision!=current_revision;
                    if reset {cursor=0;revision=current_revision;}
                    let filter=active.filter.lock().expect("filter lock").clone();
                    let batch={
                        let buffer=active.buffer.lock().expect("buffer lock");
                        let mut rows=Vec::new();
                        for m in &buffer.messages {
                            let id=m.id.parse::<u64>().unwrap_or(0);
                            if id<=cursor {continue;}
                            cursor=id;
                            if matches(m,&filter) {rows.push(row(m));}
                            if rows.len()>=LIMITS.batch_size {break;}
                        }
                        if rows.is_empty() && !reset && buffer.dropped==last_dropped && last_error.is_none() && paused==last_sent_paused {continue;}
                        last_sent_paused=paused;
                        last_dropped=buffer.dropped;
                        sequence+=1;
                        Batch {session_id:active.id.clone(),sequence,rows,first_retained_id:buffer.messages.front().map(|m|m.id.clone()),dropped:buffer.dropped,retained:buffer.messages.len(),reset,status:if paused {"paused"} else {"running"}.into(),error:last_error.take()}
                    };
                    if !send(batch) {break;}
                },
                result=consumer.recv(),if !active.paused.load(Ordering::Relaxed)=> {
                    match result {
                        Ok(message)=> {
                            message_id+=1;
                            let headers=message.headers().map(|h|h.iter().map(|h|(h.key.into(),h.value.map(<[u8]>::to_vec))).collect()).unwrap_or_default();
                            let m=KafkaMessage {id:message_id.to_string(),topic:message.topic().into(),partition:message.partition(),offset:message.offset(),timestamp:message.timestamp().to_millis(),key:message.key().map(<[u8]>::to_vec),value:message.payload().map(<[u8]>::to_vec),headers};
                            active.buffer.lock().expect("buffer lock").push(m);
                        },
                        Err(e)=> {last_error=Some(AppError::from(e));break;}
                    }
                }
            }
        }
        tracing::debug!(session=%active.id,"Consumer receive loop exited");
        if let Some(error) = last_error {
            let _ = send(Batch {
                session_id: active.id.clone(),
                sequence: sequence + 1,
                rows: vec![],
                first_retained_id: None,
                dropped: 0,
                retained: 0,
                reset: true,
                status: "failed".into(),
                error: Some(error),
            });
        }
        active.buffer.lock().expect("buffer lock").clear();
        active.cancellation.cancel();
        // librdkafka destruction can wait for its native threads. Keep teardown
        // off the Tokio worker that must service cancellation and other sessions.
        let _ = tokio::task::spawn_blocking(move || {
            tracing::debug!("Consumer native unassign started");
            let _ = consumer.unassign();
            // pause/resume can attach native partition references to the list.
            // Release those before destroying the client they refer to.
            drop(list);
            tracing::debug!("Consumer native destruction started");
            drop(consumer);
            tracing::debug!("Consumer native destruction finished");
        })
        .await;
        tracing::info!(session=%active.id,"Consumer session stopped");
    });
    *session.task.lock().expect("task lock") = Some(task);
    Ok(session)
}
