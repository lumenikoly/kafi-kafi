use std::{
    collections::{BTreeMap, HashMap},
    sync::{Arc, Mutex},
};

use kafi_kafi::{
    domain::{ConnectionProfile, SaslConfig, TlsConfig},
    error::{AppError, Result as AppResult},
    kafka::{admin, config, connection::Connection},
    secrets::{CredentialBackend, SecretStore},
};
use zeroize::Zeroizing;

#[derive(Default)]
struct MemoryCredentials(Mutex<HashMap<String, String>>);

impl CredentialBackend for MemoryCredentials {
    fn write(&self, id: &str, value: &str) -> AppResult<()> {
        self.0.lock().unwrap().insert(id.into(), value.into());
        Ok(())
    }

    fn read(&self, id: &str) -> AppResult<Zeroizing<String>> {
        self.0
            .lock()
            .unwrap()
            .get(id)
            .cloned()
            .map(Zeroizing::new)
            .ok_or_else(|| AppError::new("SECRET_UNAVAILABLE", "test credential missing"))
    }

    fn remove(&self, id: &str) -> AppResult<()> {
        self.0.lock().unwrap().remove(id);
        Ok(())
    }
}

fn secure_profile(
    bootstrap: &str,
    username: &str,
    mechanism: &str,
    password_ref: &str,
    ca_path: &str,
) -> ConnectionProfile {
    ConnectionProfile {
        id: uuid::Uuid::new_v4().to_string(),
        name: format!("Kafka security integration ({mechanism})"),
        bootstrap_servers: bootstrap.split(',').map(str::to_owned).collect(),
        client_id: Some("kafi-kafka-security-integration".into()),
        security_protocol: "SASL_SSL".into(),
        sasl: Some(SaslConfig {
            mechanism: mechanism.into(),
            username: username.into(),
            password_ref: Some(password_ref.into()),
        }),
        tls: Some(TlsConfig {
            ca_path: Some(ca_path.into()),
            ..TlsConfig::default()
        }),
        extra_properties: BTreeMap::new(),
    }
}

fn cluster_error(profile: &ConnectionProfile, secrets: &SecretStore) -> String {
    let config = config::build(profile, secrets).expect("build test client configuration");
    match Connection::open(profile.id.clone(), config) {
        Err(error) => error.code,
        Ok(connection) => match admin::cluster(&connection) {
            Err(error) => error.code,
            Ok(_) => panic!("secure Kafka connection should be rejected"),
        },
    }
}

/// Opt-in real broker coverage for SASL authentication and TLS verification.
/// Configure a test broker with SASL_SSL, the three listed SASL mechanisms, test-user credentials,
/// and the server certificate in tests/fixtures/kafka-security. Then run:
/// `KAFI_TEST_KAFKA_SECURE_BOOTSTRAP=localhost:9093 KAFI_TEST_KAFKA_SECURE_USERNAME=test-user KAFI_TEST_KAFKA_SECURE_PASSWORD=test-password cargo test --test kafka_security_integration`
#[test]
fn real_kafka_sasl_mechanisms_and_tls_failures() {
    let (Ok(bootstrap), Ok(username), Ok(password)) = (
        std::env::var("KAFI_TEST_KAFKA_SECURE_BOOTSTRAP"),
        std::env::var("KAFI_TEST_KAFKA_SECURE_USERNAME"),
        std::env::var("KAFI_TEST_KAFKA_SECURE_PASSWORD"),
    ) else {
        eprintln!(
            "Skipping secure Kafka test: secure broker, username and password env vars are required"
        );
        return;
    };

    let fixtures =
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/kafka-security");
    let trusted_ca = fixtures.join("ca.crt");
    let untrusted_ca = fixtures.join("untrusted-ca.crt");
    assert!(trusted_ca.is_file(), "missing test CA fixture");
    assert!(untrusted_ca.is_file(), "missing untrusted CA fixture");

    let secrets = SecretStore::with_backend(Arc::new(MemoryCredentials::default()));
    let mut first_profile = None;
    for mechanism in ["PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"] {
        let secret_ref = format!("test:{mechanism}");
        secrets.put(&secret_ref, password.clone());
        let profile = secure_profile(
            &bootstrap,
            &username,
            mechanism,
            &secret_ref,
            trusted_ca.to_str().unwrap(),
        );
        let client_config = config::build(&profile, &secrets).expect("build secure config");
        let connection = Connection::open(profile.id.clone(), client_config)
            .expect("create secure Kafka client");
        let metadata = admin::cluster(&connection)
            .unwrap_or_else(|error| panic!("{mechanism} with trusted TLS must connect: {error:?}"));
        assert!(
            !metadata.brokers.is_empty(),
            "{mechanism} returned no brokers"
        );
        if mechanism == "PLAIN" {
            first_profile = Some(profile);
        }
    }

    let mut bad_password = first_profile.clone().expect("PLAIN profile");
    let bad_ref = "test:plain-bad-password";
    secrets.put(bad_ref, format!("{password}-invalid"));
    bad_password.sasl.as_mut().unwrap().password_ref = Some(bad_ref.into());
    assert_eq!(
        cluster_error(&bad_password, &secrets),
        "AUTHENTICATION_FAILED"
    );

    let mut untrusted = first_profile.expect("PLAIN profile");
    untrusted.tls.as_mut().unwrap().ca_path = Some(untrusted_ca.to_string_lossy().into_owned());
    assert_eq!(cluster_error(&untrusted, &secrets), "TLS_FAILED");
}
