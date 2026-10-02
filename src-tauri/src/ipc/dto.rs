use crate::{domain, error::Result};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use ts_rs::TS;

macro_rules! dto {
    ($name:ident { $($(#[$attr:meta])* $field:ident : $ty:ty),* $(,)? }) => {
        #[derive(Clone, Default, Serialize, Deserialize, TS)]
        #[serde(rename_all = "camelCase")]
        #[ts(export, export_to = concat!(env!("CARGO_MANIFEST_DIR"), "/../src/ipc/generated/"))]
        pub struct $name { $( $(#[$attr])* pub $field: $ty),* }
    };
}
dto!(Profile { id: String, name: String, bootstrap_servers: Vec<String>, client_id: Option<String>, security_protocol: String, sasl: Option<Sasl>, tls: Option<Tls>, #[ts(type = "Record<string, string>")] extra_properties: BTreeMap<String,String> });
dto!(Sasl { mechanism: String, username: String, password_ref: Option<String> });
dto!(Tls { ca_path: Option<String>, certificate_path: Option<String>, private_key_path: Option<String>, pkcs12_path: Option<String>, key_password_ref: Option<String>, keystore_password_ref: Option<String>, legacy_truststore_password_ref: Option<String> });
impl Profile {
    pub fn domain(&self) -> Result<domain::ConnectionProfile> {
        Ok(serde_json::from_value(serde_json::to_value(self)?)?)
    }
    pub fn from_domain(profile: &domain::ConnectionProfile) -> Result<Self> {
        Ok(serde_json::from_value(serde_json::to_value(profile)?)?)
    }
}
dto!(Settings { message_buffer_limit: usize, message_buffer_bytes: usize, default_consumer_start_position: String, #[ts(type = "Record<string, string>")] layout: BTreeMap<String,String> });
dto!(SaveProfile { profile: Profile, #[ts(type = "Record<string, string>")] secrets: BTreeMap<String,String> });
dto!(SaveResult { profile: Profile, warnings: Vec<String> });
dto!(Broker { id: i32, host: String, port: i32, rack: Option<String> });
dto!(Cluster { cluster_id: Option<String>, controller: Option<i32>, brokers: Vec<Broker>, profile_id: String, generation: String });
dto!(Topic {
    name: String,
    partitions: usize,
    internal: bool
});
dto!(Partition { id: i32, leader: i32, replicas: Vec<i32>, isr: Vec<i32>, #[ts(type = "number")] earliest_offset: i64, #[ts(type = "number")] latest_offset: i64 });
dto!(ConfigEntry { name: String, value: Option<String>, is_default: bool, read_only: bool, sensitive: bool });
dto!(TopicDetail { name: String, partitions: Vec<Partition>, config: Vec<ConfigEntry> });
dto!(CreateTopic { name: String, partitions: i32, replication_factor: i32, #[ts(type = "Record<string, string>")] config: BTreeMap<String,String> });
dto!(MessageRow { id: String, partition: i32, #[ts(type = "number")] offset: i64, #[ts(type = "number | null")] timestamp: Option<i64>, key_preview: String, value_preview: String, key_size: usize, value_size: usize, value_type: String, headers_count: usize });
dto!(MessageFilter { key: String, value: String, partition: Option<i32> });
dto!(StartConsumer { topic: String, partition: Option<i32>, position: String, #[ts(type = "number | null")] offset: Option<i64>, #[ts(type = "number | null")] timestamp: Option<i64> });
dto!(Batch { session_id: String, #[ts(type = "number")] sequence: u64, rows: Vec<MessageRow>, first_retained_id: Option<String>, #[ts(type = "number")] dropped: u64, retained: usize, reset: bool, status: String, error: Option<crate::error::AppError> });
dto!(Header { key: String, value: Option<String> });
dto!(Payload { kind: String, size: usize, preview: String, text: Option<String>, base64: Option<String>, truncated: bool });
dto!(MessageDetail { id: String, topic: String, partition: i32, #[ts(type = "number")] offset: i64, #[ts(type = "number | null")] timestamp: Option<i64>, key: Option<Payload>, value: Option<Payload>, headers: Vec<Header> });
dto!(ProduceRequest { topic: String, partition: Option<i32>, key: Option<String>, value: Option<String>, headers: Vec<Header> });
dto!(ProduceResult { partition: i32, #[ts(type = "number")] offset: i64, #[ts(type = "number | null")] timestamp: Option<i64> });
dto!(Assignment { topic: String, partitions: Vec<i32> });
dto!(GroupMember { id: String, client_id: String, client_host: String, assignments: Vec<Assignment> });
dto!(ConsumerGroup {
    id: String,
    state: String,
    member_count: usize,
    topic_count: usize
});
dto!(GroupOffset { topic: String, partition: i32, #[ts(type = "number | null")] committed_offset: Option<i64>, #[ts(type = "number")] end_offset: i64, #[ts(type = "number | null")] lag: Option<i64> });
dto!(GroupDetail { id: String, state: String, members: Vec<GroupMember>, offsets: Vec<GroupOffset> });
dto!(ResetRequest { group: String, topic: String, partition: Option<i32>, position: String, #[ts(type = "number | null")] offset: Option<i64>, #[ts(type = "number | null")] timestamp: Option<i64> });
dto!(OffsetChange { topic: String, partition: i32, #[ts(type = "number | null")] old_offset: Option<i64>, #[ts(type = "number")] new_offset: i64 });
dto!(ResetPreview { token: String, group: String, generation: String, changes: Vec<OffsetChange> });
dto!(ContainerStatus { runtime: Option<String>, state: String, image: String });
dto!(LegacyStatus {
    available: bool,
    root: String,
    imported: bool
});
dto!(ImportResponse { profiles: usize, warnings: Vec<String> });
