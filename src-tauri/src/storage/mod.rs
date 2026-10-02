pub mod migration;
use crate::{
    domain::{AppSettings, ConnectionProfile},
    error::{AppError, Result},
};
use serde::{Deserialize, Serialize};
use std::{fs, io::Write, path::PathBuf};

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub profiles: Vec<ConnectionProfile>,
    pub settings: AppSettings,
    #[serde(default)]
    pub producer_templates: Vec<serde_json::Value>,
}
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Envelope {
    schema_version: u32,
    payload: Snapshot,
}
pub struct Repository {
    path: PathBuf,
}
impl Repository {
    pub fn new(path: PathBuf) -> Self {
        Self { path }
    }
    pub fn load(&self) -> Result<Snapshot> {
        if !self.path.exists() {
            return Ok(Snapshot::default());
        }
        if let Ok(raw) = serde_json::from_slice::<serde_json::Value>(&fs::read(&self.path)?)
            && raw["schemaVersion"].as_u64().is_some_and(|v| v > 1)
        {
            return Err(AppError::new(
                "STORAGE_FAILED",
                "This storage was written by a newer application. Use that version; downgrading would lose data.",
            ));
        }
        let parse = |path: &std::path::Path| -> Result<Snapshot> {
            let envelope: Envelope = serde_json::from_slice(&fs::read(path)?)?;
            if envelope.schema_version != 1 {
                return Err(AppError::new(
                    "STORAGE_FAILED",
                    "Unsupported storage schema version.",
                ));
            }
            envelope.payload.settings.validate()?;
            Ok(envelope.payload)
        };
        parse(&self.path).or_else(|error| {
            tracing::warn!("Application storage is unreadable; attempting backup recovery");
            parse(&self.path.with_extension("backup.json")).map_err(|_| error)
        })
    }
    pub fn save(&self, snapshot: &Snapshot) -> Result<()> {
        let dir = self
            .path
            .parent()
            .ok_or_else(|| AppError::new("STORAGE_FAILED", "Storage directory is missing."))?;
        fs::create_dir_all(dir)?;
        let bytes = serde_json::to_vec_pretty(&Envelope {
            schema_version: 1,
            payload: snapshot.clone(),
        })?;
        // tempfile::persist replaces atomically on Windows and Unix and stays on the same volume.
        let mut temp = tempfile::NamedTempFile::new_in(dir)?;
        temp.write_all(&bytes)?;
        temp.as_file().sync_all()?;
        if self.path.exists() {
            // Never overwrite a good backup with a corrupt snapshot.
            if serde_json::from_slice::<Envelope>(&fs::read(&self.path)?).is_ok() {
                let mut backup = tempfile::NamedTempFile::new_in(dir)?;
                backup.write_all(&fs::read(&self.path)?)?;
                backup.as_file().sync_all()?;
                backup
                    .persist(self.path.with_extension("backup.json"))
                    .map_err(|e| AppError::from(e.error))?;
            }
        }
        temp.persist(&self.path)
            .map_err(|e| AppError::from(e.error))?;
        #[cfg(unix)]
        fs::File::open(dir)?.sync_all()?;
        Ok(())
    }
}
