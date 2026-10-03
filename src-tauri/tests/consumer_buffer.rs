use std::sync::{
    Mutex,
    atomic::{AtomicBool, AtomicU64},
};

use kafi_kafi::{
    domain::KafkaMessage,
    ipc::dto::MessageFilter,
    kafka::consumer::{Buffer, LIMITS, Session, kind, matches, preview, row},
};
use tokio_util::sync::CancellationToken;

fn message(id: &str, key: Option<&[u8]>, value: Option<&[u8]>, partition: i32) -> KafkaMessage {
    KafkaMessage {
        id: id.into(),
        topic: "events".into(),
        partition,
        offset: id.parse().unwrap_or_default(),
        timestamp: Some(1_700_000_000_000),
        key: key.map(<[u8]>::to_vec),
        value: value.map(<[u8]>::to_vec),
        headers: Vec::new(),
    }
}

#[test]
fn buffer_evicts_oldest_records_at_count_limit_and_tracks_bytes() {
    let mut buffer = Buffer::new(2, 100);
    buffer.push(message("1", None, Some(b"one"), 0));
    buffer.push(message("2", None, Some(b"two"), 0));
    buffer.push(message("3", None, Some(b"three"), 0));

    assert_eq!(buffer.messages.len(), 2);
    assert_eq!(buffer.messages.front().unwrap().id, "2");
    assert_eq!(buffer.messages.back().unwrap().id, "3");
    assert_eq!(buffer.bytes(), 8);
    assert_eq!(buffer.dropped, 1);
}

#[test]
fn buffer_evicts_until_byte_limit_holds_and_drops_oversized_records() {
    let mut buffer = Buffer::new(10, 5);
    buffer.push(message("1", None, Some(b"abc"), 0));
    buffer.push(message("2", None, Some(b"def"), 0));
    assert_eq!(buffer.messages.len(), 1);
    assert_eq!(buffer.messages.front().unwrap().id, "2");
    assert_eq!(buffer.bytes(), 3);
    assert_eq!(buffer.dropped, 1);

    buffer.push(message("3", None, Some(b"oversize"), 0));
    assert!(buffer.messages.is_empty());
    assert_eq!(buffer.bytes(), 0);
    assert_eq!(buffer.dropped, 3);
}

#[test]
fn buffer_clear_releases_records_and_resets_byte_accounting() {
    let mut buffer = Buffer::new(10, 100);
    buffer.push(message("1", Some(b"key"), Some(b"payload"), 0));
    assert_eq!(buffer.bytes(), 10);

    buffer.clear();

    assert!(buffer.messages.is_empty());
    assert_eq!(buffer.bytes(), 0);
    assert_eq!(buffer.dropped, 0);
}

#[test]
fn filters_match_key_value_and_partition_without_decoding_payloads() {
    let record = message("1", Some(b"account-42"), Some(br#"{"status":"paid"}"#), 3);
    let filter = MessageFilter {
        key: "count-4".into(),
        value: "\"paid\"".into(),
        partition: Some(3),
    };
    assert!(matches(&record, &filter));

    let wrong_partition = MessageFilter {
        partition: Some(2),
        ..filter.clone()
    };
    assert!(!matches(&record, &wrong_partition));
    let wrong_value = MessageFilter {
        value: "cancelled".into(),
        ..filter
    };
    assert!(!matches(&record, &wrong_value));

    let missing_key = message("2", None, Some(b"value"), 3);
    let key_filter = MessageFilter {
        key: "present".into(),
        ..MessageFilter::default()
    };
    assert!(!matches(&missing_key, &key_filter));
}

#[test]
fn row_and_type_preview_report_binary_data_and_bound_preview_size() {
    let bytes = [0x00, 0xff, 0x10];
    assert_eq!(kind(&bytes), "binary");
    assert_eq!(preview(&bytes), "00 ff 10");

    let data = vec![0x41; LIMITS.preview_bytes + 20];
    let mut record = message("7", Some(b"key"), Some(&data), 4);
    record
        .headers
        .push(("source".into(), Some(b"test".to_vec())));
    let result = row(&record);
    assert_eq!(result.id, "7");
    assert_eq!(result.value_size, data.len());
    assert_eq!(result.value_preview.len(), LIMITS.preview_bytes);
    assert_eq!(result.value_type, "text");
    assert_eq!(result.headers_count, 1);
}

#[test]
fn kind_identifies_json_text_and_binary_payloads() {
    assert_eq!(kind(br#"{"ok":true}"#), "json");
    assert_eq!(kind(b"plain text"), "text");
    assert_eq!(kind(&[0xff, 0xfe]), "binary");
    // Oversized JSON deliberately avoids a full parse/pretty-format pass.
    let oversized_json = format!("{{\"value\":\"{}\"}}", "x".repeat(LIMITS.lazy_bytes));
    assert_eq!(kind(oversized_json.as_bytes()), "text");
}

#[test]
fn message_detail_defers_large_text_and_returns_it_only_on_explicit_full_request() {
    let large = vec![b'x'; LIMITS.lazy_bytes + 32];
    let record = message("9", Some(b"key"), Some(&large), 0);
    let session = Session {
        id: "session".into(),
        generation: "generation".into(),
        buffer: Mutex::new(Buffer::new(10, large.len() + 16)),
        filter: Mutex::new(MessageFilter::default()),
        cancellation: CancellationToken::new(),
        paused: AtomicBool::new(false),
        acknowledged: AtomicU64::new(0),
        revision: AtomicU64::new(0),
        task: Mutex::new(None),
    };
    session.buffer.lock().unwrap().push(record);

    let preview = session.detail("9", false).unwrap();
    let payload = preview.value.unwrap();
    assert!(payload.truncated);
    assert_eq!(payload.size, large.len());
    assert!(payload.text.is_none());
    assert!(payload.base64.is_none());

    let full = session.detail("9", true).unwrap();
    let payload = full.value.unwrap();
    assert!(!payload.truncated);
    let expected = "x".repeat(LIMITS.lazy_bytes + 32);
    assert_eq!(payload.text.as_deref(), Some(expected.as_str()));
}

#[test]
fn binary_message_detail_returns_bounded_preview_and_explicit_base64() {
    let session = Session {
        id: "session".into(),
        generation: "generation".into(),
        buffer: Mutex::new(Buffer::new(10, 100)),
        filter: Mutex::new(MessageFilter::default()),
        cancellation: CancellationToken::new(),
        paused: AtomicBool::new(false),
        acknowledged: AtomicU64::new(0),
        revision: AtomicU64::new(0),
        task: Mutex::new(None),
    };
    session
        .buffer
        .lock()
        .unwrap()
        .push(message("10", None, Some(&[0, 255, 16]), 0));

    let detail = session.detail("10", false).unwrap();
    let payload = detail.value.unwrap();
    assert_eq!(payload.kind, "binary");
    assert_eq!(payload.preview, "00 ff 10");
    assert_eq!(payload.base64.as_deref(), Some("AP8Q"));
    assert!(!payload.truncated);
}
