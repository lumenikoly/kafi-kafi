use crate::{
    domain::{ConnectionProfile, SaslConfig, TlsConfig},
    error::{AppError, Result},
    secrets::{SecretStore, legacy},
    storage::Snapshot,
};
use serde_json::Value;
use std::{collections::BTreeMap, path::Path};

pub struct ImportResult {
    pub snapshot: Snapshot,
    pub warnings: Vec<String>,
}
fn opt(value: &Value, field: &str) -> Option<String> {
    value[field].as_str().map(str::to_owned)
}
fn secret_ref(value: &Value, field: &str) -> Option<String> {
    opt(value, field).map(|id| format!("legacy:{id}"))
}
fn unwrap(value: &Value) -> Result<&Value> {
    if let Some(version) = value.get("version") {
        if version.as_u64() != Some(1) {
            return Err(AppError::new(
                "MIGRATION_FAILED",
                "Unsupported legacy storage version.",
            ));
        }
        value
            .get("payload")
            .ok_or_else(|| AppError::new("MIGRATION_FAILED", "Legacy snapshot has no payload."))
    } else {
        Ok(value)
    }
}
pub fn parse_snapshot(bytes: &[u8]) -> Result<ImportResult> {
    let raw: Value = serde_json::from_slice(bytes)?;
    let value = unwrap(&raw)?;
    let mut snapshot = Snapshot::default();
    let mut warnings = Vec::new();
    let profiles = value["profiles"].as_array().ok_or_else(|| {
        AppError::new("MIGRATION_FAILED", "Legacy snapshot has no profiles array.")
    })?;
    for p in profiles {
        let old_id = p["id"]
            .as_str()
            .ok_or_else(|| AppError::invalid("Legacy profile has no ID."))?;
        let id = if uuid::Uuid::parse_str(old_id).is_ok() {
            old_id.to_owned()
        } else {
            uuid::Uuid::new_v4().to_string()
        };
        let sasl = p.get("sasl").filter(|s| !s.is_null()).map(|s| SaslConfig {
            mechanism: s["mechanism"].as_str().unwrap_or("PLAIN").replace('_', "-"),
            username: s["username"].as_str().unwrap_or_default().into(),
            password_ref: secret_ref(s, "passwordSecretRef"),
        });
        let tls = p.get("ssl").filter(|s| !s.is_null()).map(|s| {
            let keystore = opt(s, "keystorePath");
            TlsConfig {
                ca_path: opt(s, "truststorePath"),
                legacy_truststore_password_ref: secret_ref(s, "truststorePasswordSecretRef"),
                pkcs12_path: keystore,
                keystore_password_ref: secret_ref(s, "keystorePasswordSecretRef"),
                key_password_ref: secret_ref(s, "keyPasswordSecretRef"),
                ..TlsConfig::default()
            }
        });
        let props: BTreeMap<String, String> = p
            .get("additionalProperties")
            .filter(|v| !v.is_null())
            .map(|v| serde_json::from_value(v.clone()))
            .transpose()?
            .unwrap_or_default();
        let profile = ConnectionProfile {
            id,
            name: p["name"].as_str().unwrap_or(old_id).into(),
            bootstrap_servers: serde_json::from_value(p["bootstrapServers"].clone())?,
            client_id: opt(p, "clientId"),
            security_protocol: p["securityProtocol"].as_str().unwrap_or("PLAINTEXT").into(),
            sasl,
            tls,
            extra_properties: props,
        };
        if let Err(error) = profile.validate() {
            warnings.push(format!(
                "{}: {} Update this imported profile before connecting.",
                profile.name, error.message
            ));
        }
        if profile.tls.is_some() {
            warnings.push(format!("{}: legacy truststores must be supplied as PEM CA; keystores as PKCS#12. JKS files are retained for manual conversion, never passed to Kafka.",profile.name));
        }
        snapshot.profiles.push(profile);
    }
    if let Some(limit) = value["settings"]["messageBufferLimit"].as_u64() {
        snapshot.settings.message_buffer_limit = (limit as usize).clamp(100, 100_000);
    }
    snapshot.settings.default_consumer_start_position =
        value["settings"]["defaultConsumerStartPosition"]
            .as_str()
            .unwrap_or("LATEST")
            .to_lowercase();
    if let Some(templates) = value["templates"].as_array() {
        snapshot.producer_templates = templates.clone();
    }
    Ok(ImportResult { snapshot, warnings })
}
pub fn import(root: &Path, fingerprint: &str, secrets: &SecretStore) -> Result<ImportResult> {
    let mut result = parse_snapshot(&std::fs::read(root.join("storage.json"))?)?;
    let path = root.join("secrets/secrets.json");
    if path.exists() {
        let raw: Value = serde_json::from_slice(&std::fs::read(path)?)?;
        let entries = unwrap(&raw)?["entries"]
            .as_object()
            .ok_or_else(|| AppError::new("MIGRATION_FAILED", "Legacy secrets have no entries."))?;
        let salt = std::fs::read(root.join("secrets/.salt"))?;
        // Decrypt every entry before writing any credential so a wrong fingerprint cannot partially import.
        let mut decrypted = Vec::new();
        for (id, entry) in entries {
            let value = legacy::decrypt(
                fingerprint,
                &salt,
                entry["iv"].as_str().unwrap_or_default(),
                entry["cipherText"].as_str().unwrap_or_default(),
            )?;
            decrypted.push((id, value));
        }
        for (id, value) in decrypted {
            if !secrets.put(&format!("legacy:{id}"), value.to_string()) {
                result.warnings.push("System credentials unavailable: imported password is usable for this application session only.".into());
            }
        }
    }
    Ok(result)
}
