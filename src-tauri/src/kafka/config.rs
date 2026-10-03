use crate::{domain::ConnectionProfile, error::Result, secrets::SecretStore};
use rdkafka::config::ClientConfig;

pub fn build(profile: &ConnectionProfile, secrets: &SecretStore) -> Result<ClientConfig> {
    profile.validate()?;
    let mut config = ClientConfig::new();
    config
        .set("bootstrap.servers", profile.bootstrap_servers.join(","))
        .set(
            "client.id",
            profile.client_id.as_deref().unwrap_or("kafi-kafi"),
        )
        .set("security.protocol", &profile.security_protocol)
        .set("socket.timeout.ms", "10000")
        .set("enable.ssl.certificate.verification", "true")
        .set("ssl.endpoint.identification.algorithm", "https");
    if profile.security_protocol.starts_with("SASL") {
        let sasl = profile.sasl.as_ref().expect("validated SASL");
        config
            .set("sasl.mechanism", &sasl.mechanism)
            .set("sasl.username", &sasl.username);
        if let Some(id) = &sasl.password_ref {
            config.set("sasl.password", &*secrets.get(id)?);
        }
    }
    if let Some(tls) = &profile.tls {
        for (key, value) in [
            ("ssl.ca.location", &tls.ca_path),
            ("ssl.certificate.location", &tls.certificate_path),
            ("ssl.key.location", &tls.private_key_path),
            ("ssl.keystore.location", &tls.pkcs12_path),
        ] {
            if let Some(value) = value {
                config.set(key, value);
            }
        }
        for (key, id) in [
            ("ssl.key.password", &tls.key_password_ref),
            ("ssl.keystore.password", &tls.keystore_password_ref),
        ] {
            if let Some(id) = id {
                config.set(key, &*secrets.get(id)?);
            }
        }
    }
    for (key, value) in &profile.extra_properties {
        config.set(key, value);
    }
    Ok(config)
}
pub fn inspection(config: &ClientConfig) -> ClientConfig {
    let mut config = config.clone();
    config
        .set("group.id", format!("kafi-inspect-{}", uuid::Uuid::new_v4()))
        .set("enable.auto.commit", "false")
        .set("enable.auto.offset.store", "false")
        .set("auto.offset.reset", "latest");
    config
}
