#[cfg(windows)]
mod windows {
    use std::{process::Command, time::Duration};

    use kafi_kafi::{
        containers::{ContainerManager, NAME},
        domain::ConnectionProfile,
        kafka::{config, connection::Connection},
        secrets::SecretStore,
    };
    use serde_json::Value;

    fn inspect_local_container() -> Option<Value> {
        let output = Command::new("podman")
            .args(["inspect", NAME])
            .output()
            .expect("run podman inspect with direct arguments");
        if !output.status.success() {
            return None;
        }
        serde_json::from_slice::<Value>(&output.stdout)
            .expect("parse Podman inspect JSON")
            .as_array()
            .and_then(|containers| containers.first())
            .cloned()
    }

    struct StopStartedOwnedContainer;

    impl Drop for StopStartedOwnedContainer {
        fn drop(&mut self) {
            let Some(container) = inspect_local_container() else {
                return;
            };
            if container["Config"]["Labels"]["com.kafikafi.owner"] != "kafi-kafi"
                || container["State"]["Status"] != "running"
            {
                return;
            }
            // Fallback cleanup is limited to the known test-created name and the
            // production ownership label; never invoke a shell or remove the container.
            let _ = Command::new("podman").args(["stop", NAME]).output();
        }
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn production_manager_starts_connects_and_stops_local_kafka() {
        if std::env::var("KAFI_TEST_LOCAL_KAFKA").as_deref() != Ok("1") {
            eprintln!("Skipping local Kafka test: KAFI_TEST_LOCAL_KAFKA is not 1");
            return;
        }

        let info = Command::new("podman")
            .args(["info", "--format", "json"])
            .output()
            .expect("Podman is required for this opt-in Windows test");
        assert!(info.status.success(), "Podman must be running");

        let exists = Command::new("podman")
            .args(["container", "exists", NAME])
            .status()
            .expect("check the fixed local Kafka container name");
        assert_eq!(
            exists.code(),
            Some(1),
            "refusing to touch a pre-existing or indeterminate {NAME} container"
        );

        let manager = ContainerManager::default();
        let before = manager.status().await.expect("read local Kafka status");
        assert_eq!(before.runtime.as_deref(), Some("podman"));
        assert_eq!(before.state, "missing");

        let _cleanup = StopStartedOwnedContainer;
        let started = manager
            .start()
            .await
            .expect("start local Kafka through production manager");
        assert_eq!(started.runtime.as_deref(), Some("podman"));
        assert_eq!(started.state, "running");
        assert_eq!(started.image, "apache/kafka:3.9.1");

        let profile = ConnectionProfile {
            id: uuid::Uuid::new_v4().to_string(),
            name: "Local Kafka integration test".into(),
            bootstrap_servers: vec!["localhost:9092".into()],
            client_id: Some("kafi-local-kafka-test".into()),
            security_protocol: "PLAINTEXT".into(),
            sasl: None,
            tls: None,
            extra_properties: Default::default(),
        };
        let deadline = tokio::time::Instant::now() + Duration::from_secs(120);
        let mut connected = false;
        while tokio::time::Instant::now() < deadline {
            let attempt_profile = profile.clone();
            let result = tokio::task::spawn_blocking(move || {
                let client_config = config::build(&attempt_profile, &SecretStore::default())
                    .map_err(|error| error.message)?;
                Connection::open(attempt_profile.id, client_config)
                    .map(drop)
                    .map_err(|error| error.message)
            })
            .await
            .expect("join production Kafka connection attempt");
            if result.is_ok() {
                connected = true;
                break;
            }
            tokio::time::sleep(Duration::from_secs(2)).await;
        }
        assert!(
            connected,
            "production Connection could not reach localhost:9092"
        );
        let stopped = manager
            .stop()
            .await
            .expect("stop local Kafka through production manager");
        assert_eq!(stopped.runtime.as_deref(), Some("podman"));
        assert_eq!(stopped.state, "exited");

        let container = inspect_local_container().expect("stopped local container is retained");
        assert_eq!(
            container["Config"]["Labels"]["com.kafikafi.owner"],
            "kafi-kafi"
        );
        assert_eq!(container["State"]["Status"], "exited");
    }
}
