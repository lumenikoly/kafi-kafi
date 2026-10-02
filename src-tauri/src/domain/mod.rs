use crate::error::{AppError, Result};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionProfile {
    pub id: String,
    pub name: String,
    pub bootstrap_servers: Vec<String>,
    pub client_id: Option<String>,
    pub security_protocol: String,
    pub sasl: Option<SaslConfig>,
    pub tls: Option<TlsConfig>,
    #[serde(default)]
    pub extra_properties: BTreeMap<String, String>,
}
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaslConfig {
    pub mechanism: String,
    pub username: String,
    pub password_ref: Option<String>,
}
#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TlsConfig {
    pub ca_path: Option<String>,
    pub certificate_path: Option<String>,
    pub private_key_path: Option<String>,
    pub pkcs12_path: Option<String>,
    pub key_password_ref: Option<String>,
    pub keystore_password_ref: Option<String>,
    pub legacy_truststore_password_ref: Option<String>,
}
impl ConnectionProfile {
    pub fn validate(&self) -> Result<()> {
        uuid::Uuid::parse_str(&self.id)
            .map_err(|_| AppError::invalid("Profile ID must be a UUID."))?;
        if self.name.trim().is_empty()
            || self.bootstrap_servers.is_empty()
            || self.bootstrap_servers.iter().any(|s| {
                s.chars().any(char::is_whitespace)
                    || s.contains([',', '/', '\0'])
                    || !s.rsplit_once(':').is_some_and(|(host, port)| {
                        !host.is_empty() && port.parse::<u16>().is_ok_and(|p| p > 0)
                    })
            })
        {
            return Err(AppError::invalid(
                "Enter a name and at least one host:port bootstrap server.",
            ));
        }
        if !["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"]
            .contains(&self.security_protocol.as_str())
        {
            return Err(AppError::invalid("Unsupported security protocol."));
        }
        if self.security_protocol.starts_with("SASL") {
            let sasl = self
                .sasl
                .as_ref()
                .ok_or_else(|| AppError::invalid("SASL credentials are required."))?;
            if !["PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"].contains(&sasl.mechanism.as_str())
                || sasl.username.is_empty()
            {
                return Err(AppError::invalid(
                    "Select a supported SASL mechanism and enter a username.",
                ));
            }
        }
        // An allowlist keeps arbitrary librdkafka properties from overriding authentication,
        // inspection isolation, callbacks, logging, TLS verification or file access.
        const SAFE: &[&str] = &[
            "socket.timeout.ms",
            "request.timeout.ms",
            "metadata.max.age.ms",
            "reconnect.backoff.ms",
            "reconnect.backoff.max.ms",
            "compression.type",
            "message.max.bytes",
            "receive.message.max.bytes",
            "fetch.message.max.bytes",
            "fetch.max.bytes",
            "fetch.min.bytes",
            "fetch.wait.max.ms",
            "queued.max.messages.kbytes",
            "broker.address.family",
        ];
        if self
            .extra_properties
            .iter()
            .any(|(k, v)| !SAFE.contains(&k.as_str()) || v.len() > 1024)
        {
            return Err(AppError::invalid(
                "An extra property is reserved or not on the safe Kafka property allowlist.",
            ));
        }
        if let Some(tls) = &self.tls {
            for path in [
                &tls.ca_path,
                &tls.certificate_path,
                &tls.private_key_path,
                &tls.pkcs12_path,
            ]
            .into_iter()
            .flatten()
            {
                if path.to_lowercase().ends_with(".jks") {
                    return Err(AppError::invalid(
                        "JKS is not supported by librdkafka. Convert it to PEM or PKCS#12 before connecting.",
                    ));
                }
            }
        }
        Ok(())
    }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    pub message_buffer_limit: usize,
    pub message_buffer_bytes: usize,
    pub default_consumer_start_position: String,
    #[serde(default)]
    pub layout: BTreeMap<String, String>,
}
impl Default for AppSettings {
    fn default() -> Self {
        Self {
            message_buffer_limit: 10_000,
            message_buffer_bytes: 64 * 1024 * 1024,
            default_consumer_start_position: "latest".into(),
            layout: BTreeMap::new(),
        }
    }
}
impl AppSettings {
    pub fn validate(&self) -> Result<()> {
        if !(100..=100_000).contains(&self.message_buffer_limit)
            || !(1024 * 1024..=512 * 1024 * 1024).contains(&self.message_buffer_bytes)
            || !["latest", "earliest"].contains(&self.default_consumer_start_position.as_str())
        {
            return Err(AppError::invalid(
                "Buffer must contain 100–100000 records and 1–512 MiB. Select latest or earliest.",
            ));
        }
        Ok(())
    }
}

pub struct KafkaMessage {
    pub id: String,
    pub topic: String,
    pub partition: i32,
    pub offset: i64,
    pub timestamp: Option<i64>,
    pub key: Option<Vec<u8>>,
    pub value: Option<Vec<u8>>,
    pub headers: Vec<(String, Option<Vec<u8>>)>,
}
impl KafkaMessage {
    pub fn size(&self) -> usize {
        self.key.as_ref().map_or(0, Vec::len)
            + self.value.as_ref().map_or(0, Vec::len)
            + self
                .headers
                .iter()
                .map(|(k, v)| k.len() + v.as_ref().map_or(0, Vec::len))
                .sum::<usize>()
    }
}
