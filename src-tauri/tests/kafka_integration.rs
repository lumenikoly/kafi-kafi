use std::{
    collections::BTreeMap,
    sync::{Arc, atomic::Ordering, mpsc},
    time::Duration,
};

use kafi_kafi::{
    app::AppState,
    domain::{AppSettings, ConnectionProfile, SaslConfig},
    ipc::dto::{
        CreateTopic, Header, MessageFilter, ProduceRequest, Profile, ResetRequest, SaveProfile,
        StartConsumer,
    },
    kafka::{admin, config, connection::Connection, consumer, groups, producer},
    secrets::SecretStore,
};
use rdkafka::consumer::{BaseConsumer, CommitMode, Consumer};

fn start_reader(
    connection: Arc<Connection>,
    topic: &str,
    position: &str,
    offset: Option<i64>,
    timestamp: Option<i64>,
) -> (
    Arc<consumer::Session>,
    mpsc::Receiver<kafi_kafi::ipc::dto::Batch>,
) {
    let (sender, receiver) = mpsc::channel();
    let send = Arc::new(move |batch: kafi_kafi::ipc::dto::Batch| sender.send(batch).is_ok());
    let session = consumer::start(
        connection,
        StartConsumer {
            topic: topic.into(),
            partition: Some(0),
            position: position.into(),
            offset,
            timestamp,
        },
        AppSettings::default(),
        send,
    )
    .expect("start Kafka inspection session");
    (session, receiver)
}

async fn wait_for_topic(connection: &Arc<Connection>, topic: &str) {
    let deadline = std::time::Instant::now() + Duration::from_secs(15);
    loop {
        if admin::partitions(connection, topic).is_ok_and(|partitions| !partitions.is_empty()) {
            return;
        }
        assert!(
            std::time::Instant::now() < deadline,
            "topic did not become visible: {topic}"
        );
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
}

async fn collect_rows(
    session: Arc<consumer::Session>,
    receiver: mpsc::Receiver<kafi_kafi::ipc::dto::Batch>,
    minimum: usize,
) -> (
    Vec<kafi_kafi::ipc::dto::MessageRow>,
    mpsc::Receiver<kafi_kafi::ipc::dto::Batch>,
) {
    tokio::task::spawn_blocking(move || {
        let mut rows = Vec::new();
        let receiver = receiver;
        while rows.len() < minimum {
            let batch = receiver
                .recv_timeout(Duration::from_secs(20))
                .expect("receive Kafka message batch");
            session
                .acknowledged
                .store(batch.sequence, Ordering::Release);
            rows.extend(batch.rows);
        }
        (rows, receiver)
    })
    .await
    .expect("join Kafka message receiver")
}

async fn acknowledge_batch(
    session: Arc<consumer::Session>,
    receiver: mpsc::Receiver<kafi_kafi::ipc::dto::Batch>,
) -> (
    kafi_kafi::ipc::dto::Batch,
    mpsc::Receiver<kafi_kafi::ipc::dto::Batch>,
) {
    tokio::task::spawn_blocking(move || {
        let batch = receiver
            .recv_timeout(Duration::from_secs(10))
            .expect("receive Kafka batch");
        session
            .acknowledged
            .store(batch.sequence, Ordering::Release);
        (batch, receiver)
    })
    .await
    .expect("join Kafka batch receiver")
}

/// Real Kafka coverage is opt-in so normal unit and CI runs never start or require a broker.
/// Run separately with `KAFI_TEST_KAFKA_BOOTSTRAP=localhost:9092 cargo test --test kafka_integration`.
#[tokio::test]
async fn real_kafka_connection_admin_produce_consume_and_groups_smoke() {
    let Ok(bootstrap) = std::env::var("KAFI_TEST_KAFKA_BOOTSTRAP") else {
        eprintln!("Skipping real Kafka test: KAFI_TEST_KAFKA_BOOTSTRAP is not set");
        return;
    };

    let suffix = uuid::Uuid::new_v4().simple().to_string();
    let topic = format!("kafi_integration_{}", &suffix[..12]);
    let group_id = format!("kafi-integration-{suffix}");
    let profile = ConnectionProfile {
        id: uuid::Uuid::new_v4().to_string(),
        name: "Kafka integration test".into(),
        bootstrap_servers: bootstrap.split(',').map(str::to_owned).collect(),
        client_id: Some("kafi-kafi-integration-test".into()),
        security_protocol: "PLAINTEXT".into(),
        sasl: None,
        tls: None,
        extra_properties: BTreeMap::new(),
    };
    let secret_store = SecretStore::default();
    let client_config =
        config::build(&profile, &secret_store).expect("build Kafka client configuration");
    let connection =
        Arc::new(Connection::open(profile.id.clone(), client_config).expect("connect to Kafka"));

    let cluster = admin::cluster(&connection).expect("read cluster metadata");
    assert!(!cluster.brokers.is_empty());
    admin::create(
        &connection,
        CreateTopic {
            name: topic.clone(),
            partitions: 1,
            replication_factor: 1,
            config: BTreeMap::new(),
        },
    )
    .await
    .expect("create test topic");
    wait_for_topic(&connection, &topic).await;
    assert!(
        admin::list(&connection)
            .unwrap()
            .iter()
            .any(|entry| entry.name == topic)
    );
    assert_eq!(admin::partitions(&connection, &topic).unwrap().len(), 1);
    let config_entries = admin::configuration(&connection, &topic)
        .await
        .expect("describe topic config");
    assert!(
        config_entries
            .iter()
            .any(|entry| entry.name == "cleanup.policy")
    );

    let sent = producer::produce(
        &connection,
        ProduceRequest {
            topic: topic.clone(),
            partition: Some(0),
            key: Some("kafi-test-key".into()),
            value: Some("kafi-test-value".into()),
            headers: vec![Header {
                key: "suite".into(),
                value: Some("integration".into()),
            }],
        },
    )
    .await
    .expect("produce test record");
    assert_eq!(sent.partition, 0);
    assert!(sent.offset >= 0);

    let next_offset = producer::produce(
        &connection,
        ProduceRequest {
            topic: topic.clone(),
            partition: Some(0),
            key: Some("kafi-lag-key-1".into()),
            value: Some("kafi-lag-value-1".into()),
            headers: Vec::new(),
        },
    )
    .await
    .expect("produce first lag fixture record");
    let end_offset = producer::produce(
        &connection,
        ProduceRequest {
            topic: topic.clone(),
            partition: Some(0),
            key: Some("kafi-lag-key-2".into()),
            value: Some("kafi-lag-value-2".into()),
            headers: Vec::new(),
        },
    )
    .await
    .expect("produce second lag fixture record");
    assert_eq!(next_offset.offset, sent.offset + 1);
    assert_eq!(end_offset.offset, next_offset.offset + 1);

    let (sender, receiver) = mpsc::channel();
    let send_batch = Arc::new(move |batch: kafi_kafi::ipc::dto::Batch| sender.send(batch).is_ok());
    let session = consumer::start(
        connection.clone(),
        StartConsumer {
            topic: topic.clone(),
            partition: Some(0),
            position: "earliest".into(),
            offset: None,
            timestamp: None,
        },
        AppSettings::default(),
        send_batch,
    )
    .expect("start inspection consumer");
    let batch = tokio::task::spawn_blocking(move || {
        loop {
            let batch = receiver
                .recv_timeout(Duration::from_secs(20))
                .expect("receive consumer batch");
            if !batch.rows.is_empty() {
                return batch;
            }
        }
    })
    .await
    .expect("join receiver");
    assert!(batch.rows.iter().any(|row| row.key_preview == "kafi-test-key" && row.value_preview == "kafi-test-value"));
    session.stop().await;

    // Commit an offset with a real Kafka group so the application's groups list/detail/reset
    // paths exercise the broker protocol rather than a mocked DTO.
    let mut group_config = config::build(&profile, &secret_store).unwrap();
    group_config
        .set("group.id", &group_id)
        .set("enable.auto.commit", "false")
        .set("auto.offset.reset", "earliest");
    let group_consumer: BaseConsumer = group_config.create().expect("create offset test consumer");
    group_consumer
        .subscribe(&[&topic])
        .expect("subscribe offset test consumer");
    let committed = tokio::task::spawn_blocking(move || {
        let deadline = std::time::Instant::now() + Duration::from_secs(20);
        loop {
            if let Some(Ok(message)) = group_consumer.poll(Duration::from_millis(250)) {
                group_consumer
                    .commit_message(&message, CommitMode::Sync)
                    .expect("commit test group offset");
                break;
            }
            assert!(
                std::time::Instant::now() < deadline,
                "timed out reading record into test group"
            );
        }
        drop(group_consumer);
    })
    .await;
    committed.expect("join group consumer");

    let listed = groups::list(&connection).expect("list consumer groups");
    assert!(listed.iter().any(|group| group.id == group_id));
    let detail = groups::describe(&connection, &group_id).expect("describe consumer group");
    let offset = detail
        .offsets
        .iter()
        .find(|offset| offset.topic == topic && offset.partition == 0)
        .expect("group detail includes the test topic partition");
    assert_eq!(offset.committed_offset, Some(sent.offset + 1));
    assert_eq!(offset.end_offset, end_offset.offset + 1);
    assert_eq!(offset.lag, Some(2));
    let reset = groups::preview_reset(
        &connection,
        ResetRequest {
            group: group_id.clone(),
            topic: topic.clone(),
            partition: Some(0),
            position: "earliest".into(),
            offset: None,
            timestamp: None,
        },
    )
    .expect("preview group reset");
    assert_eq!(reset.changes.len(), 1);
    groups::reset(&connection, &reset).expect("reset test group offset");
    groups::delete(&connection, &group_id)
        .await
        .expect("delete test consumer group");
    admin::delete(&connection, &topic)
        .await
        .expect("delete test topic");
}

#[tokio::test]
async fn real_kafka_consumer_positions_filters_pause_resume_and_cancel() {
    let Ok(bootstrap) = std::env::var("KAFI_TEST_KAFKA_BOOTSTRAP") else {
        eprintln!("Skipping real Kafka test: KAFI_TEST_KAFKA_BOOTSTRAP is not set");
        return;
    };
    let _ = tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .try_init();

    let profile_id = uuid::Uuid::new_v4().to_string();
    let suffix = uuid::Uuid::new_v4().simple().to_string();
    let topic = format!("kafi_stream_{}", &suffix[..12]);
    let profile = ConnectionProfile {
        id: profile_id.clone(),
        name: "Kafka stream integration test".into(),
        bootstrap_servers: bootstrap.split(',').map(str::to_owned).collect(),
        client_id: Some("kafi-kafi-stream-test".into()),
        security_protocol: "PLAINTEXT".into(),
        sasl: None,
        tls: None,
        extra_properties: BTreeMap::new(),
    };
    let secret_store = SecretStore::default();
    let client_config = config::build(&profile, &secret_store).unwrap();
    let connection =
        Arc::new(Connection::open(profile_id, client_config).expect("connect to Kafka"));
    admin::create(
        &connection,
        CreateTopic {
            name: topic.clone(),
            partitions: 1,
            replication_factor: 1,
            config: BTreeMap::new(),
        },
    )
    .await
    .expect("create stream test topic");
    wait_for_topic(&connection, &topic).await;

    let mut produced = Vec::new();
    for index in 0..3 {
        produced.push(
            producer::produce(
                &connection,
                ProduceRequest {
                    topic: topic.clone(),
                    partition: Some(0),
                    key: Some(format!("key-{index}")),
                    value: Some(format!("value-{index}")),
                    headers: Vec::new(),
                },
            )
            .await
            .expect("produce positional fixture record"),
        );
        tokio::time::sleep(Duration::from_millis(60)).await;
    }
    assert_eq!(
        produced
            .iter()
            .map(|record| record.offset)
            .collect::<Vec<_>>(),
        [0, 1, 2]
    );

    let (earliest, receiver) = start_reader(connection.clone(), &topic, "earliest", None, None);
    let (rows, _receiver) = collect_rows(earliest.clone(), receiver, 3).await;
    assert!(rows.iter().any(|row| row.offset == 0));
    assert!(rows.iter().any(|row| row.offset == 1));
    assert!(rows.iter().any(|row| row.offset == 2));
    earliest.stop().await;

    let (specific_offset, receiver) =
        start_reader(connection.clone(), &topic, "offset", Some(1), None);
    let (rows, _receiver) = collect_rows(specific_offset.clone(), receiver, 2).await;
    assert!(rows.iter().all(|row| row.offset >= 1));
    assert!(rows.iter().any(|row| row.offset == 2));
    specific_offset.stop().await;

    let timestamp = produced[1]
        .timestamp
        .expect("broker supplies record timestamps");
    let (specific_timestamp, receiver) = start_reader(
        connection.clone(),
        &topic,
        "timestamp",
        None,
        Some(timestamp),
    );
    let (rows, _receiver) = collect_rows(specific_timestamp.clone(), receiver, 1).await;
    assert!(rows.iter().all(|row| row.offset >= 1));
    specific_timestamp.stop().await;

    let (latest, receiver) = start_reader(connection.clone(), &topic, "latest", None, None);
    tokio::time::sleep(Duration::from_millis(250)).await;
    assert!(latest.buffer.lock().unwrap().messages.is_empty());
    assert!(receiver.try_iter().all(|batch| batch.rows.is_empty()));
    latest.stop().await;

    let (sender, receiver) = mpsc::channel();
    let send = Arc::new(move |batch: kafi_kafi::ipc::dto::Batch| sender.send(batch).is_ok());
    let session = consumer::start(
        connection.clone(),
        StartConsumer {
            topic: topic.clone(),
            partition: Some(0),
            position: "earliest".into(),
            offset: None,
            timestamp: None,
        },
        AppSettings::default(),
        send,
    )
    .unwrap();
    let (rows, receiver) = collect_rows(session.clone(), receiver, 3).await;
    assert_eq!(rows.len(), 3);
    while session.buffer.lock().unwrap().messages.len() < 3 {
        tokio::task::yield_now().await;
    }

    while let Ok(batch) = receiver.try_recv() {
        session
            .acknowledged
            .store(batch.sequence, Ordering::Release);
    }
    *session.filter.lock().unwrap() = MessageFilter {
        key: "key-1".into(),
        ..MessageFilter::default()
    };
    session.revision.fetch_add(1, Ordering::Release);
    let filter_session = session.clone();
    let filtered = tokio::task::spawn_blocking(move || {
        loop {
            let batch = receiver
                .recv_timeout(Duration::from_secs(5))
                .expect("receive filter snapshot");
            filter_session
                .acknowledged
                .store(batch.sequence, Ordering::Release);
            if batch.reset {
                return (batch, receiver);
            }
        }
    })
    .await
    .unwrap();
    let (filtered, receiver) = filtered;
    assert_eq!(filtered.rows.len(), 1);
    assert_eq!(filtered.rows[0].key_preview, "key-1");
    tokio::time::timeout(Duration::from_secs(10), session.stop())
        .await
        .expect("filtered session teardown timed out");
    drop(receiver);

    let (sender, receiver) = mpsc::channel();
    let send = Arc::new(move |batch: kafi_kafi::ipc::dto::Batch| sender.send(batch).is_ok());
    let session = consumer::start(
        connection.clone(),
        StartConsumer {
            topic: topic.clone(),
            partition: Some(0),
            position: "latest".into(),
            offset: None,
            timestamp: None,
        },
        AppSettings::default(),
        send,
    )
    .unwrap();
    let (initial, receiver) = acknowledge_batch(session.clone(), receiver).await;
    assert!(initial.reset);
    session.paused.store(true, Ordering::Release);
    let (paused, receiver) = acknowledge_batch(session.clone(), receiver).await;
    assert_eq!(paused.status, "paused");
    assert!(session.buffer.lock().unwrap().messages.is_empty());
    tokio::time::timeout(
        Duration::from_secs(10),
        producer::produce(
            &connection,
            ProduceRequest {
                topic: topic.clone(),
                partition: Some(0),
                key: Some("key-3".into()),
                value: Some("value-3".into()),
                headers: Vec::new(),
            },
        ),
    )
    .await
    .expect("paused producer timed out")
    .expect("produce while paused");
    tokio::time::sleep(Duration::from_millis(300)).await;
    assert!(session.buffer.lock().unwrap().messages.is_empty());

    session.paused.store(false, Ordering::Release);
    let (rows, receiver) = collect_rows(session.clone(), receiver, 1).await;
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].key_preview, "key-3");
    let mut stop = Box::pin(session.stop());
    tokio::select! {
        result = &mut stop => result,
        _ = tokio::time::sleep(Duration::from_millis(100)) => {
            assert!(session.cancellation.is_cancelled(), "stop should cancel promptly");
            assert!(session.buffer.lock().unwrap().messages.is_empty(), "stop should clear buffered records promptly");
            tokio::time::timeout(Duration::from_secs(10), &mut stop)
                .await
                .expect("consumer native teardown timed out");
        }
    }
    drop(receiver);
    assert!(session.cancellation.is_cancelled());
    assert!(session.buffer.lock().unwrap().messages.is_empty());
    assert_eq!(session.buffer.lock().unwrap().bytes(), 0);

    tokio::time::timeout(Duration::from_secs(10), admin::delete(&connection, &topic))
        .await
        .expect("topic deletion timed out")
        .expect("delete stream test topic");
}

#[tokio::test]
async fn failed_profile_switch_keeps_active_connection_and_consumer_session() {
    let Ok(bootstrap) = std::env::var("KAFI_TEST_KAFKA_BOOTSTRAP") else {
        eprintln!("Skipping real Kafka test: KAFI_TEST_KAFKA_BOOTSTRAP is not set");
        return;
    };

    let dir = tempfile::tempdir().unwrap();
    let state = Arc::new(AppState::new(dir.path().to_path_buf()).unwrap());
    let good = ConnectionProfile {
        id: uuid::Uuid::new_v4().to_string(),
        name: "working connection".into(),
        bootstrap_servers: bootstrap.split(',').map(str::to_owned).collect(),
        client_id: Some("kafi-switch-test".into()),
        security_protocol: "PLAINTEXT".into(),
        sasl: None,
        tls: None,
        extra_properties: BTreeMap::new(),
    };
    state
        .save_profile(SaveProfile {
            profile: Profile::from_domain(&good).unwrap(),
            secrets: BTreeMap::new(),
        })
        .unwrap();
    state
        .connect(good.id.clone())
        .await
        .expect("connect working profile");
    let previous_connection = state.connection().unwrap();

    let topic = format!(
        "kafi_switch_{}",
        &uuid::Uuid::new_v4().simple().to_string()[..12]
    );
    admin::create(
        &previous_connection,
        CreateTopic {
            name: topic.clone(),
            partitions: 1,
            replication_factor: 1,
            config: BTreeMap::new(),
        },
    )
    .await
    .unwrap();
    wait_for_topic(&previous_connection, &topic).await;
    let (sender, _receiver) = mpsc::channel();
    let send = Arc::new(move |batch: kafi_kafi::ipc::dto::Batch| sender.send(batch).is_ok());
    let session_id = state
        .start_consumer(
            StartConsumer {
                topic: topic.clone(),
                partition: Some(0),
                position: "latest".into(),
                offset: None,
                timestamp: None,
            },
            send,
        )
        .await
        .expect("start consumer on existing connection");
    let previous_session = state.session(&session_id).unwrap();

    let mut bad = good.clone();
    bad.id = uuid::Uuid::new_v4().to_string();
    bad.name = "missing credentials".into();
    bad.security_protocol = "SASL_PLAINTEXT".into();
    bad.sasl = Some(SaslConfig {
        mechanism: "PLAIN".into(),
        username: "missing-secret".into(),
        password_ref: Some(format!("absent:{}", bad.id)),
    });
    state
        .save_profile(SaveProfile {
            profile: Profile::from_domain(&bad).unwrap(),
            secrets: BTreeMap::new(),
        })
        .unwrap();

    let error = state.connect(bad.id).await.err().expect("switch must fail");
    assert_eq!(error.code, "SECRET_UNAVAILABLE");
    assert!(Arc::ptr_eq(
        &previous_connection,
        &state.connection().unwrap()
    ));
    assert!(Arc::ptr_eq(
        &previous_session,
        &state.session(&session_id).unwrap()
    ));

    state.stop_consumer(&session_id).await.unwrap();
    state.disconnect().await;
    admin::delete(&previous_connection, &topic).await.unwrap();
}
