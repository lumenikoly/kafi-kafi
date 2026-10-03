//! Reproducible buffer/projection benchmark, independent of network and UI.
use kafi_kafi::{
    domain::KafkaMessage,
    kafka::consumer::{Buffer, LIMITS, row},
};
use std::time::Instant;
fn main() {
    let mut results = Vec::new();
    for (payload_bytes, records) in [(1024, 100_000), (1024 * 1024, 2_000)] {
        let mut buffer = Buffer::new(10_000, 64 * 1024 * 1024);
        let start = Instant::now();
        for id in 1..=records {
            buffer.push(KafkaMessage {
                id: id.to_string(),
                topic: "benchmark".into(),
                partition: 0,
                offset: id,
                timestamp: None,
                key: None,
                value: Some(vec![b'x'; payload_bytes]),
                headers: vec![],
            });
        }
        let ingest_ms = start.elapsed().as_secs_f64() * 1000.0;
        let start = Instant::now();
        let rows = buffer.messages.iter().map(row).collect::<Vec<_>>();
        let projection_ms = start.elapsed().as_secs_f64() * 1000.0;
        let json_bytes = serde_json::to_vec(&rows)
            .expect("serialize projections")
            .len();
        results.push(serde_json::json!({"payloadBytes":payload_bytes,"records":records,"ingestMs":ingest_ms,"projectionMs":projection_ms,"retainedRecords":buffer.messages.len(),"retainedBytes":buffer.bytes(),"evictedRecords":buffer.dropped,"projectionJsonBytes":json_bytes}));
    }
    println!(
        "{}",
        serde_json::json!({"scope":"Rust buffer and projection; excludes WebView, Tauri delivery and Kafka network","batchSize":LIMITS.batch_size,"intervalMs":LIMITS.interval_ms,"results":results})
    );
}
