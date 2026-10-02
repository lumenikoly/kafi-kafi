mod operations;
use crate::{
    containers::ContainerManager,
    domain::{AppSettings, ConnectionProfile},
    error::{AppError, Result},
    ipc::dto::*,
    kafka::{self, connection::Connection, consumer::Session},
    secrets::SecretStore,
    storage::{Repository, Snapshot, migration},
};
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{
        Arc, Mutex,
        atomic::{AtomicU64, Ordering},
    },
};

struct LocalStore {
    repository: Repository,
    snapshot: Snapshot,
}
struct Runtime {
    connection: Option<Arc<Connection>>,
    sessions: HashMap<String, Arc<Session>>,
}
pub struct AppState {
    local: Mutex<LocalStore>,
    pub secrets: Arc<SecretStore>,
    runtime: Mutex<Runtime>,
    connect_ticket: AtomicU64,
    previews: Mutex<HashMap<String, (ResetPreview, std::time::Instant)>>,
    pub containers: ContainerManager,
}
pub async fn blocking<T: Send + 'static>(
    work: impl FnOnce() -> Result<T> + Send + 'static,
) -> Result<T> {
    tokio::task::spawn_blocking(work).await.map_err(|_| {
        AppError::new(
            "INTERNAL_ERROR",
            "The background operation could not complete.",
        )
    })?
}
impl AppState {
    pub fn new(path: PathBuf) -> Result<Self> {
        let repository = Repository::new(path.join("storage.json"));
        let snapshot = repository.load()?;
        Ok(Self {
            local: Mutex::new(LocalStore {
                repository,
                snapshot,
            }),
            secrets: Arc::new(SecretStore::default()),
            runtime: Mutex::new(Runtime {
                connection: None,
                sessions: HashMap::new(),
            }),
            connect_ticket: AtomicU64::new(0),
            previews: Mutex::new(HashMap::new()),
            containers: ContainerManager::default(),
        })
    }
    pub fn profiles(&self) -> Result<Vec<Profile>> {
        self.local
            .lock()
            .expect("storage lock")
            .snapshot
            .profiles
            .iter()
            .map(Profile::from_domain)
            .collect()
    }
    pub fn settings(&self) -> Result<Settings> {
        Ok(serde_json::from_value(serde_json::to_value(
            &self.local.lock().expect("storage lock").snapshot.settings,
        )?)?)
    }
    pub fn save_settings(&self, settings: Settings) -> Result<()> {
        let settings: AppSettings = serde_json::from_value(serde_json::to_value(settings)?)?;
        settings.validate()?;
        let mut local = self.local.lock().expect("storage lock");
        let mut next = local.snapshot.clone();
        next.settings = settings;
        local.repository.save(&next)?;
        local.snapshot = next;
        Ok(())
    }
    fn profile(&self, id: &str) -> Result<ConnectionProfile> {
        self.local
            .lock()
            .expect("storage lock")
            .snapshot
            .profiles
            .iter()
            .find(|p| p.id == id)
            .cloned()
            .ok_or_else(|| AppError::invalid("Profile does not exist."))
    }
    fn prepare_profile(
        &self,
        request: SaveProfile,
        persist: bool,
    ) -> Result<(ConnectionProfile, Vec<String>)> {
        let mut profile = request.profile.domain()?;
        profile.validate()?;
        let mut warnings = Vec::new();
        for (slot, value) in request.secrets {
            let id = format!("profile:{}:{slot}", profile.id);
            match slot.as_str() {
                "sasl-password" => {
                    profile
                        .sasl
                        .as_mut()
                        .ok_or_else(|| AppError::invalid("SASL is not configured."))?
                        .password_ref = Some(id.clone())
                }
                "key-password" => {
                    profile
                        .tls
                        .as_mut()
                        .ok_or_else(|| AppError::invalid("TLS is not configured."))?
                        .key_password_ref = Some(id.clone())
                }
                "keystore-password" => {
                    profile
                        .tls
                        .as_mut()
                        .ok_or_else(|| AppError::invalid("TLS is not configured."))?
                        .keystore_password_ref = Some(id.clone())
                }
                _ => return Err(AppError::invalid("Unknown credential field.")),
            }
            if persist {
                if !self.secrets.put(&id, value) {
                    warnings.push("Credential storage is unavailable. Password is usable for this session only and was not saved.".into());
                }
            } else {
                // Tests use private temporary refs, never overwrite a saved profile's password.
                let temporary = format!("test:{}:{slot}", uuid::Uuid::new_v4());
                self.secrets.put_ephemeral(&temporary, value);
                match slot.as_str() {
                    "sasl-password" => {
                        profile.sasl.as_mut().expect("SASL checked").password_ref = Some(temporary)
                    }
                    "key-password" => {
                        profile.tls.as_mut().expect("TLS checked").key_password_ref =
                            Some(temporary)
                    }
                    _ => {
                        profile
                            .tls
                            .as_mut()
                            .expect("TLS checked")
                            .keystore_password_ref = Some(temporary)
                    }
                }
            }
        }
        Ok((profile, warnings))
    }
    pub fn save_profile(&self, request: SaveProfile) -> Result<SaveResult> {
        let (profile, warnings) = self.prepare_profile(request, true)?;
        let mut local = self.local.lock().expect("storage lock");
        let mut next = local.snapshot.clone();
        if let Some(index) = next.profiles.iter().position(|p| p.id == profile.id) {
            next.profiles[index] = profile.clone();
        } else {
            next.profiles.push(profile.clone());
        }
        local.repository.save(&next)?;
        local.snapshot = next;
        Ok(SaveResult {
            profile: Profile::from_domain(&profile)?,
            warnings,
        })
    }
    pub fn delete_profile(&self, id: &str) -> Result<()> {
        if self
            .runtime
            .lock()
            .expect("runtime lock")
            .connection
            .as_ref()
            .is_some_and(|c| c.profile_id == id)
        {
            return Err(AppError::invalid(
                "Disconnect the active profile before deleting it.",
            ));
        }
        let profile = self.profile(id)?;
        for id in secret_refs(&profile) {
            self.secrets.delete(&id)?;
        }
        let mut local = self.local.lock().expect("storage lock");
        let mut next = local.snapshot.clone();
        next.profiles.retain(|p| p.id != id);
        local.repository.save(&next)?;
        local.snapshot = next;
        Ok(())
    }
    pub fn test_connection(&self, request: SaveProfile) -> Result<Cluster> {
        let (profile, _) = self.prepare_profile(request, false)?;
        let refs = secret_refs(&profile);
        let result = kafka::config::build(&profile, &self.secrets)
            .and_then(|config| Connection::open(profile.id, config))
            .and_then(|c| kafka::admin::cluster(&c));
        for id in refs.iter().filter(|id| id.starts_with("test:")) {
            self.secrets.remove_ephemeral(id);
        }
        result
    }
    pub fn connection(&self) -> Result<Arc<Connection>> {
        self.runtime
            .lock()
            .expect("runtime lock")
            .connection
            .clone()
            .ok_or_else(|| AppError::new("NOT_CONNECTED", "Connect to a Kafka profile first."))
    }
    pub async fn connect(self: &Arc<Self>, id: String) -> Result<Cluster> {
        let ticket = self.connect_ticket.fetch_add(1, Ordering::SeqCst) + 1;
        let state = self.clone();
        let connection = blocking(move || {
            let profile = state.profile(&id)?;
            let config = kafka::config::build(&profile, &state.secrets)?;
            Ok(Arc::new(Connection::open(id, config)?))
        })
        .await?;
        let clone = connection.clone();
        let cluster = blocking(move || kafka::admin::cluster(&clone)).await?;
        let sessions = {
            let mut runtime = self.runtime.lock().expect("runtime lock");
            if self.connect_ticket.load(Ordering::SeqCst) != ticket {
                return Err(AppError::new(
                    "CANCELLED",
                    "A newer connection request superseded this one.",
                ));
            }
            runtime.connection = Some(connection);
            std::mem::take(&mut runtime.sessions)
        };
        self.previews.lock().expect("preview lock").clear();
        for session in sessions.values() {
            session.cancellation.cancel();
        }
        for session in sessions.into_values() {
            session.stop().await;
        }
        tracing::info!(profile=%cluster.profile_id,"Kafka connection activated");
        Ok(cluster)
    }
    pub async fn disconnect(&self) {
        self.connect_ticket.fetch_add(1, Ordering::SeqCst);
        let sessions = {
            let mut runtime = self.runtime.lock().expect("runtime lock");
            runtime.connection = None;
            std::mem::take(&mut runtime.sessions)
        };
        self.previews.lock().expect("preview lock").clear();
        for session in sessions.values() {
            session.cancellation.cancel();
        }
        for session in sessions.into_values() {
            session.stop().await;
        }
        tracing::info!("Kafka disconnected");
    }
    pub async fn start_consumer(
        self: &Arc<Self>,
        request: StartConsumer,
        send: Arc<dyn Fn(Batch) -> bool + Send + Sync>,
    ) -> Result<String> {
        let connection = self.connection()?;
        let settings = self
            .local
            .lock()
            .expect("storage lock")
            .snapshot
            .settings
            .clone();
        let clone = connection.clone();
        let session =
            blocking(move || kafka::consumer::start(clone, request, settings, send)).await?;
        let accepted = {
            let mut runtime = self.runtime.lock().expect("runtime lock");
            if runtime
                .connection
                .as_ref()
                .is_some_and(|c| c.generation == session.generation)
            {
                runtime.sessions.insert(session.id.clone(), session.clone());
                true
            } else {
                false
            }
        };
        if !accepted {
            session.stop().await;
            return Err(AppError::new(
                "CANCELLED",
                "Connection changed while starting the consumer.",
            ));
        }
        let state = Arc::downgrade(self);
        let active = session.clone();
        tokio::spawn(async move {
            active.cancellation.cancelled().await;
            if let Some(state) = state.upgrade() {
                state
                    .runtime
                    .lock()
                    .expect("runtime lock")
                    .sessions
                    .remove(&active.id);
            }
        });
        Ok(session.id.clone())
    }
    pub fn session(&self, id: &str) -> Result<Arc<Session>> {
        self.runtime
            .lock()
            .expect("runtime lock")
            .sessions
            .get(id)
            .cloned()
            .ok_or_else(|| AppError::new("SESSION_NOT_FOUND", "Consumer session is closed."))
    }
    pub async fn stop_consumer(&self, id: &str) -> Result<()> {
        let session = self
            .runtime
            .lock()
            .expect("runtime lock")
            .sessions
            .remove(id);
        if let Some(session) = session {
            session.stop().await;
        }
        Ok(())
    }
    pub fn pause_consumer(&self, id: &str, pause: bool) -> Result<()> {
        self.session(id)?.paused.store(pause, Ordering::Relaxed);
        Ok(())
    }
    pub fn set_filter(&self, id: &str, filter: MessageFilter) -> Result<()> {
        if filter.key.len() > 4096 || filter.value.len() > 4096 {
            return Err(AppError::invalid("Filter text is limited to 4096 bytes."));
        }
        let session = self.session(id)?;
        *session.filter.lock().expect("filter lock") = filter;
        session.revision.fetch_add(1, Ordering::Release);
        Ok(())
    }
    pub async fn preview_reset(self: &Arc<Self>, request: ResetRequest) -> Result<ResetPreview> {
        let connection = self.connection()?;
        let preview = blocking(move || kafka::groups::preview_reset(&connection, request)).await?;
        let mut previews = self.previews.lock().expect("preview lock");
        previews.clear();
        previews.insert(
            preview.token.clone(),
            (preview.clone(), std::time::Instant::now()),
        );
        Ok(preview)
    }
    pub async fn reset_offsets(self: &Arc<Self>, token: String, confirmed: bool) -> Result<()> {
        if !confirmed {
            return Err(AppError::invalid("Confirm the offset reset explicitly."));
        }
        let (preview, created) = self
            .previews
            .lock()
            .expect("preview lock")
            .remove(&token)
            .ok_or_else(|| AppError::invalid("Reset preview expired. Generate a new preview."))?;
        if created.elapsed() > std::time::Duration::from_secs(300) {
            return Err(AppError::invalid(
                "Reset preview expired. Generate a new preview.",
            ));
        }
        let connection = self.connection()?;
        blocking(move || kafka::groups::reset(&connection, &preview)).await
    }
    pub fn legacy_status(&self) -> LegacyStatus {
        let root = dirs::home_dir().unwrap_or_default().join(".lightkafka");
        LegacyStatus {
            available: root.join("storage.json").exists(),
            root: root.to_string_lossy().into(),
            imported: self
                .local
                .lock()
                .expect("storage lock")
                .snapshot
                .settings
                .layout
                .contains_key("legacyImported"),
        }
    }
    pub fn import_legacy(&self, fingerprint: String) -> Result<ImportResponse> {
        let status = self.legacy_status();
        if status.imported {
            return Err(AppError::invalid(
                "Legacy storage has already been imported.",
            ));
        }
        let imported = migration::import(
            std::path::Path::new(&status.root),
            &fingerprint,
            &self.secrets,
        )?;
        let profiles = imported.snapshot.profiles.len();
        let mut local = self.local.lock().expect("storage lock");
        let mut next = local.snapshot.clone();
        for profile in imported.snapshot.profiles {
            if !next.profiles.iter().any(|p| p.id == profile.id) {
                next.profiles.push(profile);
            }
        }
        next.producer_templates
            .extend(imported.snapshot.producer_templates);
        next.settings = imported.snapshot.settings;
        next.settings
            .layout
            .insert("legacyImported".into(), "true".into());
        local.repository.save(&next)?;
        local.snapshot = next;
        Ok(ImportResponse {
            profiles,
            warnings: imported.warnings,
        })
    }
    pub fn producer_templates(&self) -> Vec<serde_json::Value> {
        self.local
            .lock()
            .expect("storage lock")
            .snapshot
            .producer_templates
            .clone()
    }
}
fn secret_refs(profile: &ConnectionProfile) -> Vec<String> {
    let mut refs = Vec::new();
    if let Some(sasl) = &profile.sasl
        && let Some(id) = &sasl.password_ref
    {
        refs.push(id.clone());
    }
    if let Some(tls) = &profile.tls {
        refs.extend(
            [
                &tls.key_password_ref,
                &tls.keystore_password_ref,
                &tls.legacy_truststore_password_ref,
            ]
            .into_iter()
            .flatten()
            .cloned(),
        );
    }
    refs
}
