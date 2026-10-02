use std::{
    collections::{BTreeMap, HashMap},
    fs,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, AtomicUsize, Ordering},
    },
};

use kafi_kafi::{
    domain::{AppSettings, ConnectionProfile, SaslConfig},
    error::{AppError, Result as AppResult},
    secrets::{CredentialBackend, SecretStore, legacy},
    storage::migration,
    storage::{Repository, Snapshot},
};
use serde_json::Value;
use zeroize::Zeroizing;

#[derive(Default)]
struct FakeCredentialBackend {
    values: Mutex<HashMap<String, String>>,
    fail_writes: AtomicBool,
    writes: AtomicUsize,
    removals: AtomicUsize,
}

impl FakeCredentialBackend {
    fn new(fail_writes: bool) -> Self {
        Self {
            fail_writes: AtomicBool::new(fail_writes),
            ..Default::default()
        }
    }
}

impl CredentialBackend for FakeCredentialBackend {
    fn write(&self, id: &str, value: &str) -> AppResult<()> {
        self.writes.fetch_add(1, Ordering::Relaxed);
        if self.fail_writes.load(Ordering::Relaxed) {
            return Err(AppError::new(
                "SECRET_UNAVAILABLE",
                "fake backend is unavailable",
            ));
        }
        self.values
            .lock()
            .unwrap()
            .insert(id.to_owned(), value.to_owned());
        Ok(())
    }

    fn read(&self, id: &str) -> AppResult<Zeroizing<String>> {
        self.values
            .lock()
            .unwrap()
            .get(id)
            .cloned()
            .map(Zeroizing::new)
            .ok_or_else(|| AppError::new("SECRET_UNAVAILABLE", "fake credential not found"))
    }

    fn remove(&self, id: &str) -> AppResult<()> {
        self.removals.fetch_add(1, Ordering::Relaxed);
        self.values.lock().unwrap().remove(id);
        Ok(())
    }
}

fn legacy_storage_root() -> tempfile::TempDir {
    let root = tempfile::tempdir().unwrap();
    let secret_dir = root.path().join("secrets");
    fs::create_dir_all(&secret_dir).unwrap();
    fs::write(
        root.path().join("storage.json"),
        include_bytes!("fixtures/legacy-storage-v1.json"),
    )
    .unwrap();
    fs::write(
        secret_dir.join("secrets.json"),
        include_bytes!("fixtures/legacy-secrets-v1.json"),
    )
    .unwrap();
    fs::write(
        secret_dir.join(".salt"),
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
    )
    .unwrap();
    root
}

fn profile() -> ConnectionProfile {
    ConnectionProfile {
        id: "a6638396-caa8-4d3f-bd56-0c727fbe9d2c".into(),
        name: "Local cluster".into(),
        bootstrap_servers: vec!["localhost:9092".into()],
        client_id: Some("migration-test".into()),
        security_protocol: "SASL_SSL".into(),
        sasl: Some(SaslConfig {
            mechanism: "SCRAM-SHA-512".into(),
            username: "alice".into(),
            password_ref: Some("profile:a6638396-caa8-4d3f-bd56-0c727fbe9d2c:sasl-password".into()),
        }),
        tls: None,
        extra_properties: BTreeMap::from([("socket.timeout.ms".into(), "5000".into())]),
    }
}

#[test]
fn profile_validation_accepts_supported_security_and_safe_properties() {
    assert!(profile().validate().is_ok());
}

#[test]
fn profile_validation_rejects_invalid_protocol_reserved_properties_and_jks() {
    let mut invalid = profile();
    invalid.security_protocol = "PLAINTEXT;security.protocol=SASL_SSL".into();
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");

    let mut invalid = profile();
    invalid
        .extra_properties
        .insert("sasl.password".into(), "secret".into());
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");

    let mut invalid = profile();
    invalid.tls = Some(kafi_kafi::domain::TlsConfig {
        ca_path: Some("C:/certs/truststore.jks".into()),
        ..Default::default()
    });
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");
}

#[test]
fn settings_validation_enforces_record_memory_and_position_bounds() {
    let defaults = AppSettings::default();
    assert!(defaults.validate().is_ok());

    let invalid = AppSettings {
        message_buffer_limit: 99,
        ..defaults.clone()
    };
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");
    let invalid = AppSettings {
        message_buffer_bytes: 1024,
        ..defaults.clone()
    };
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");
    let invalid = AppSettings {
        default_consumer_start_position: "offset:10".into(),
        ..defaults
    };
    assert_eq!(invalid.validate().unwrap_err().code, "INVALID_INPUT");
}

#[test]
fn repository_round_trips_versioned_snapshot_and_keeps_previous_backup() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("storage.json");
    let repository = Repository::new(path.clone());
    let mut snapshot = Snapshot::default();
    snapshot.profiles.push(profile());
    snapshot.settings = AppSettings {
        message_buffer_limit: 25_000,
        default_consumer_start_position: "earliest".into(),
        ..Default::default()
    };

    repository.save(&snapshot).unwrap();
    let serialized: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
    assert_eq!(serialized["schemaVersion"], 1);
    assert_eq!(
        repository.load().unwrap().settings.message_buffer_limit,
        25_000
    );
    assert_eq!(repository.load().unwrap().profiles.len(), 1);

    let previous = fs::read(&path).unwrap();
    let mut next = snapshot.clone();
    next.settings.message_buffer_limit = 30_000;
    repository.save(&next).unwrap();
    assert_eq!(
        fs::read(path.with_file_name("storage.backup.json")).unwrap(),
        previous
    );
}

#[test]
fn repository_recovers_from_corrupt_primary_using_backup() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("storage.json");
    let repository = Repository::new(path.clone());
    let mut snapshot = Snapshot::default();
    snapshot.settings.message_buffer_limit = 12_345;
    repository.save(&snapshot).unwrap();
    snapshot.settings.message_buffer_limit = 23_456;
    repository.save(&snapshot).unwrap();
    fs::write(&path, b"truncated json").unwrap();

    assert_eq!(
        repository.load().unwrap().settings.message_buffer_limit,
        12_345
    );
}

#[test]
fn legacy_crypto_fixture_decrypts_with_original_kdf_fingerprint_and_aes_gcm_format() {
    let fixture: Value =
        serde_json::from_str(include_str!("fixtures/legacy-secrets-v1.json")).unwrap();
    let entries = fixture["payload"]["entries"].as_object().unwrap();
    let salt = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
    let fingerprint = r"migration-test|Windows 11|C:\Users\migration-test";
    let expected = [
        ("profile:profile-legacy:sasl-password", "sasl-password-秘密"),
        (
            "profile:profile-legacy:truststore-password",
            "truststore-password",
        ),
        (
            "profile:profile-legacy:keystore-password",
            "keystore-password",
        ),
        ("profile:profile-legacy:key-password", "key-password"),
    ];

    for (id, plaintext) in expected {
        let entry = &entries[id];
        let value = legacy::decrypt(
            fingerprint,
            &salt,
            entry["iv"].as_str().unwrap(),
            entry["cipherText"].as_str().unwrap(),
        )
        .unwrap();
        assert_eq!(&*value, plaintext);
    }
}

#[test]
fn legacy_secret_decrypt_returns_migration_error_for_wrong_fingerprint_or_tampering() {
    let fixture: Value =
        serde_json::from_str(include_str!("fixtures/legacy-secrets-v1.json")).unwrap();
    let entry = &fixture["payload"]["entries"]["profile:profile-legacy:sasl-password"];
    let salt = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
    let fingerprint = r"migration-test|Windows 11|C:\Users\migration-test";

    let wrong_key = legacy::decrypt(
        "different-machine",
        &salt,
        entry["iv"].as_str().unwrap(),
        entry["cipherText"].as_str().unwrap(),
    )
    .unwrap_err();
    assert_eq!(wrong_key.code, "MIGRATION_FAILED");
    let bad_iv = legacy::decrypt(
        fingerprint,
        &salt,
        "not base64",
        entry["cipherText"].as_str().unwrap(),
    )
    .unwrap_err();
    assert_eq!(bad_iv.code, "MIGRATION_FAILED");
    let bad_tag =
        legacy::decrypt(fingerprint, &salt, entry["iv"].as_str().unwrap(), "AAAA").unwrap_err();
    assert_eq!(bad_tag.code, "MIGRATION_FAILED");
}

#[test]
fn legacy_storage_fixtures_capture_both_kotlin_schema_shapes() {
    let versioned: Value =
        serde_json::from_str(include_str!("fixtures/legacy-storage-v1.json")).unwrap();
    let unwrapped: Value =
        serde_json::from_str(include_str!("fixtures/legacy-storage-v0.json")).unwrap();
    assert_eq!(versioned["version"], 1);
    assert_eq!(
        versioned["payload"]["profiles"][0]["securityProtocol"],
        "SASL_SSL"
    );
    assert_eq!(
        versioned["payload"]["settings"]["defaultConsumerStartPosition"],
        "EARLIEST"
    );
    assert!(unwrapped.get("version").is_none());
    assert_eq!(unwrapped["settings"]["messageBufferLimit"], 10_000);
}

#[test]
fn legacy_import_preserves_profile_credentials_references_tls_paths_and_producer_settings() {
    let imported =
        migration::parse_snapshot(include_str!("fixtures/legacy-storage-v1.json").as_bytes())
            .unwrap();
    let profile = &imported.snapshot.profiles[0];

    assert!(uuid::Uuid::parse_str(&profile.id).is_ok());
    assert_eq!(profile.name, "Legacy SASL SSL");
    assert_eq!(
        profile.bootstrap_servers,
        vec![
            "kafka.example.test:9093".to_owned(),
            "kafka-backup.example.test:9093".to_owned()
        ]
    );
    assert_eq!(profile.security_protocol, "SASL_SSL");
    assert_eq!(profile.sasl.as_ref().unwrap().mechanism, "SCRAM-SHA-512");
    assert_eq!(profile.sasl.as_ref().unwrap().username, "migration-user");
    assert_eq!(
        profile.sasl.as_ref().unwrap().password_ref.as_deref(),
        Some("legacy:profile:profile-legacy:sasl-password")
    );
    let tls = profile.tls.as_ref().unwrap();
    assert_eq!(tls.ca_path.as_deref(), Some("C:/certs/truststore.jks"));
    assert_eq!(
        tls.legacy_truststore_password_ref.as_deref(),
        Some("legacy:profile:profile-legacy:truststore-password")
    );
    assert_eq!(tls.pkcs12_path.as_deref(), Some("C:/certs/client.jks"));
    assert_eq!(
        tls.keystore_password_ref.as_deref(),
        Some("legacy:profile:profile-legacy:keystore-password")
    );
    assert_eq!(
        tls.key_password_ref.as_deref(),
        Some("legacy:profile:profile-legacy:key-password")
    );
    assert_eq!(imported.snapshot.settings.message_buffer_limit, 25_000);
    assert_eq!(
        imported.snapshot.settings.default_consumer_start_position,
        "earliest"
    );
    assert_eq!(imported.snapshot.producer_templates[0]["topic"], "events");
    assert!(
        imported
            .warnings
            .iter()
            .any(|warning| warning.contains("JKS"))
    );
}

#[test]
fn legacy_import_accepts_unwrapped_storage_and_rejects_unknown_versions() {
    let imported =
        migration::parse_snapshot(include_str!("fixtures/legacy-storage-v0.json").as_bytes())
            .unwrap();
    assert!(imported.snapshot.profiles.is_empty());
    assert_eq!(imported.snapshot.settings.message_buffer_limit, 10_000);
    assert_eq!(
        imported.snapshot.settings.default_consumer_start_position,
        "latest"
    );

    let error = migration::parse_snapshot(br#"{"version":2,"payload":{"profiles":[]}}"#)
        .err()
        .unwrap();
    assert_eq!(error.code, "MIGRATION_FAILED");
}

#[test]
fn credential_store_falls_back_to_memory_without_persisting_or_leaking_secrets() {
    let backend = Arc::new(FakeCredentialBackend::new(true));
    let secrets = SecretStore::with_backend(backend.clone());
    let id = "profile:secure-id:sasl-password";

    assert!(!secrets.put(id, "never-on-disk".into()));
    assert_eq!(&*secrets.get(id).unwrap(), "never-on-disk");
    assert!(backend.values.lock().unwrap().is_empty());
    assert_eq!(backend.writes.load(Ordering::Relaxed), 1);

    secrets.delete(id).unwrap();
    assert_eq!(secrets.get(id).unwrap_err().code, "SECRET_UNAVAILABLE");
    assert_eq!(backend.removals.load(Ordering::Relaxed), 1);
}

#[test]
fn unavailable_overwrite_does_not_resurrect_a_stale_persisted_credential_after_delete() {
    let backend = Arc::new(FakeCredentialBackend::default());
    let secrets = SecretStore::with_backend(backend.clone());
    let id = "profile:secure-id:sasl-password";

    assert!(secrets.put(id, "old-persisted-secret".into()));
    backend.fail_writes.store(true, Ordering::Relaxed);
    assert!(!secrets.put(id, "new-session-secret".into()));
    assert_eq!(&*secrets.get(id).unwrap(), "new-session-secret");

    secrets.delete(id).unwrap();
    assert_eq!(secrets.get(id).unwrap_err().code, "SECRET_UNAVAILABLE");
    assert!(!backend.values.lock().unwrap().contains_key(id));
    assert_eq!(backend.removals.load(Ordering::Relaxed), 1);
}

#[cfg(windows)]
#[test]
fn system_credential_backend_round_trips_when_explicitly_enabled() {
    if std::env::var("KAFI_TEST_SYSTEM_CREDENTIALS").as_deref() != Ok("1") {
        eprintln!("Skipping system credential test: KAFI_TEST_SYSTEM_CREDENTIALS is not 1");
        return;
    }

    let store = SecretStore::default();
    let id = format!("test:system-vault:{}", uuid::Uuid::new_v4());
    let value = "public dummy credential used only for opt-in backend verification";

    // Keep cleanup outside the assertion path so a read/write failure still
    // removes a credential if the OS backend partially persisted it.
    let round_trip = (|| -> AppResult<()> {
        if !store.put(&id, value.to_owned()) {
            return Err(AppError::new(
                "SYSTEM_CREDENTIAL_WRITE_FAILED",
                "Windows Credential Manager did not save the test credential.",
            ));
        }
        let loaded = store.get(&id)?;
        if loaded.as_str() != value {
            return Err(AppError::new(
                "SYSTEM_CREDENTIAL_ROUND_TRIP_FAILED",
                "Windows Credential Manager returned a different test credential.",
            ));
        }
        Ok(())
    })();

    let cleanup = store.delete(&id);
    assert!(
        cleanup.is_ok(),
        "test credential cleanup failed: {cleanup:?}"
    );
    round_trip.expect("Windows Credential Manager round-trip failed");
}

#[test]
fn legacy_import_namespaces_refs_and_preserves_existing_credentials_and_source_files() {
    let root = legacy_storage_root();
    let source_storage = fs::read(root.path().join("storage.json")).unwrap();
    let source_secrets = fs::read(root.path().join("secrets/secrets.json")).unwrap();
    let backend = Arc::new(FakeCredentialBackend::default());
    let store = SecretStore::with_backend(backend.clone());
    let colliding_id = "profile:profile-legacy:sasl-password";
    assert!(store.put(colliding_id, "current-profile-secret".into()));

    let imported = migration::import(
        root.path(),
        r"migration-test|Windows 11|C:\Users\migration-test",
        &store,
    )
    .unwrap();

    assert_eq!(&*store.get(colliding_id).unwrap(), "current-profile-secret");
    assert_eq!(
        &*store
            .get("legacy:profile:profile-legacy:sasl-password")
            .unwrap(),
        "sasl-password-秘密"
    );
    assert_eq!(
        imported.snapshot.profiles[0]
            .sasl
            .as_ref()
            .unwrap()
            .password_ref
            .as_deref(),
        Some("legacy:profile:profile-legacy:sasl-password")
    );
    assert!(
        imported
            .warnings
            .iter()
            .any(|warning| warning.contains("JKS"))
    );
    let imported_json = serde_json::to_string(&imported.snapshot.profiles[0]).unwrap();
    assert!(!imported_json.contains("sasl-password-秘密"));
    assert_eq!(
        fs::read(root.path().join("storage.json")).unwrap(),
        source_storage
    );
    assert_eq!(
        fs::read(root.path().join("secrets/secrets.json")).unwrap(),
        source_secrets
    );
    assert_eq!(
        fs::read(root.path().join("secrets/.salt")).unwrap(),
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]
    );
}

#[test]
fn legacy_import_keeps_decrypted_credentials_ephemeral_if_backend_is_unavailable() {
    let root = legacy_storage_root();
    let backend = Arc::new(FakeCredentialBackend::new(true));
    let store = SecretStore::with_backend(backend.clone());

    let imported = migration::import(
        root.path(),
        r"migration-test|Windows 11|C:\Users\migration-test",
        &store,
    )
    .unwrap();

    assert_eq!(backend.writes.load(Ordering::Relaxed), 4);
    assert!(backend.values.lock().unwrap().is_empty());
    assert!(
        imported
            .warnings
            .iter()
            .any(|warning| warning.contains("session only"))
    );
    assert_eq!(
        &*store
            .get("legacy:profile:profile-legacy:truststore-password")
            .unwrap(),
        "truststore-password"
    );
}

#[test]
fn failed_legacy_secret_decryption_does_not_partially_write_credentials() {
    let root = legacy_storage_root();
    let backend = Arc::new(FakeCredentialBackend::default());
    let store = SecretStore::with_backend(backend.clone());

    let error = migration::import(root.path(), "wrong-machine", &store)
        .err()
        .unwrap();

    assert_eq!(error.code, "MIGRATION_FAILED");
    assert_eq!(backend.writes.load(Ordering::Relaxed), 0);
    assert!(backend.values.lock().unwrap().is_empty());
}
