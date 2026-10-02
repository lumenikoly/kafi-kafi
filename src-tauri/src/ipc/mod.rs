pub mod dto;
use crate::{
    app::{AppState, blocking},
    error::{AppError, Result},
};
use dto::*;
use std::{
    collections::BTreeMap,
    sync::{Arc, atomic::Ordering},
};
use tauri::{Manager, State, ipc::Channel};
use tauri_plugin_dialog::DialogExt;
type Backend<'a> = State<'a, Arc<AppState>>;
static STARTED_AT: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();
#[tauri::command]
fn ui_ready() -> u64 {
    let elapsed = STARTED_AT
        .get()
        .map_or(0, |start| start.elapsed().as_millis() as u64);
    tracing::info!(startup_ms = elapsed, "Desktop interface is interactive");
    elapsed
}

#[tauri::command]
async fn get_profiles(state: Backend<'_>) -> Result<Vec<Profile>> {
    let state = state.inner().clone();
    blocking(move || state.profiles()).await
}
#[tauri::command]
async fn save_profile(state: Backend<'_>, request: SaveProfile) -> Result<SaveResult> {
    let state = state.inner().clone();
    blocking(move || state.save_profile(request)).await
}
#[tauri::command]
async fn delete_profile(state: Backend<'_>, id: String) -> Result<()> {
    let state = state.inner().clone();
    blocking(move || state.delete_profile(&id)).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn test_connection(state: Backend<'_>, request: SaveProfile) -> Result<Cluster> {
    let state = state.inner().clone();
    blocking(move || state.test_connection(request)).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn connect(state: Backend<'_>, id: String) -> Result<Cluster> {
    state.inner().connect(id).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn disconnect(state: Backend<'_>) -> Result<()> {
    state.disconnect().await;
    Ok(())
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn get_cluster(state: Backend<'_>) -> Result<Cluster> {
    state.cluster().await
}
#[tauri::command]
async fn get_brokers(state: Backend<'_>) -> Result<Vec<Broker>> {
    Ok(get_cluster(state).await?.brokers)
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn list_topics(state: Backend<'_>) -> Result<Vec<Topic>> {
    state.topics().await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn describe_topic(state: Backend<'_>, topic: String) -> Result<TopicDetail> {
    state.topic_detail(topic).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn create_topic(state: Backend<'_>, request: CreateTopic) -> Result<()> {
    state.create_topic(request).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn delete_topic(state: Backend<'_>, topic: String, confirmed: bool) -> Result<()> {
    state.delete_topic(topic, confirmed).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn update_topic_config(
    state: Backend<'_>,
    topic: String,
    updates: BTreeMap<String, String>,
) -> Result<()> {
    state.update_topic_config(topic, updates).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn produce_message(state: Backend<'_>, request: ProduceRequest) -> Result<ProduceResult> {
    state.produce(request).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn start_consumer(
    state: Backend<'_>,
    request: StartConsumer,
    channel: Channel<Batch>,
) -> Result<String> {
    state
        .inner()
        .start_consumer(request, Arc::new(move |batch| channel.send(batch).is_ok()))
        .await
}
#[tauri::command]
fn acknowledge_batch(state: Backend<'_>, session_id: String, sequence: u64) -> Result<()> {
    state
        .session(&session_id)?
        .acknowledged
        .fetch_max(sequence, Ordering::Release);
    Ok(())
}
#[tauri::command]
fn pause_consumer(state: Backend<'_>, session_id: String) -> Result<()> {
    state.pause_consumer(&session_id, true)
}
#[tauri::command]
fn resume_consumer(state: Backend<'_>, session_id: String) -> Result<()> {
    state.pause_consumer(&session_id, false)
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn stop_consumer(state: Backend<'_>, session_id: String) -> Result<()> {
    state.stop_consumer(&session_id).await
}
#[tauri::command]
fn set_consumer_filter(
    state: Backend<'_>,
    session_id: String,
    filter: MessageFilter,
) -> Result<()> {
    state.set_filter(&session_id, filter)
}
#[tauri::command]
async fn get_message_detail(
    state: Backend<'_>,
    session_id: String,
    message_id: String,
    full: bool,
) -> Result<MessageDetail> {
    let session = state.session(&session_id)?;
    blocking(move || session.detail(&message_id, full)).await
}
#[tauri::command]
async fn export_message(
    state: Backend<'_>,
    app: tauri::AppHandle,
    session_id: String,
    message_id: String,
    field: String,
) -> Result<bool> {
    let session = state.session(&session_id)?;
    let bytes = {
        let buffer = session.buffer.lock().expect("buffer lock");
        let message = buffer
            .messages
            .iter()
            .find(|m| m.id == message_id)
            .ok_or_else(|| AppError::new("MESSAGE_EVICTED", "Message is no longer retained."))?;
        match field.as_str() {
            "key" => message.key.clone(),
            "value" => message.value.clone(),
            _ => return Err(AppError::invalid("Choose key or value to export.")),
        }
        .ok_or_else(|| AppError::invalid("This field is null."))?
    };
    blocking(move || {
        if let Some(path) = app
            .dialog()
            .file()
            .set_file_name("record.bin")
            .blocking_save_file()
        {
            let path = path
                .into_path()
                .map_err(|_| AppError::invalid("Select a local export file."))?;
            std::fs::write(path, bytes)?;
            return Ok(true);
        }
        Ok(false)
    })
    .await
}
#[tauri::command]
async fn choose_certificate(app: tauri::AppHandle) -> Result<Option<String>> {
    blocking(move || {
        Ok(app
            .dialog()
            .file()
            .add_filter("TLS certificates", &["pem", "crt", "key", "p12", "pfx"])
            .blocking_pick_file()
            .map(|p| p.to_string()))
    })
    .await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn list_consumer_groups(state: Backend<'_>) -> Result<Vec<ConsumerGroup>> {
    state.groups().await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn describe_consumer_group(state: Backend<'_>, id: String) -> Result<GroupDetail> {
    state.group_detail(id).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn preview_group_offsets(state: Backend<'_>, request: ResetRequest) -> Result<ResetPreview> {
    state.inner().preview_reset(request).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn reset_group_offsets(state: Backend<'_>, token: String, confirmed: bool) -> Result<()> {
    state.inner().reset_offsets(token, confirmed).await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn delete_consumer_group(state: Backend<'_>, id: String, confirmed: bool) -> Result<()> {
    state.delete_group(id, confirmed).await
}
#[tauri::command]
async fn get_settings(state: Backend<'_>) -> Result<Settings> {
    let state = state.inner().clone();
    blocking(move || state.settings()).await
}
#[tauri::command]
async fn save_settings(state: Backend<'_>, settings: Settings) -> Result<()> {
    let state = state.inner().clone();
    blocking(move || state.save_settings(settings)).await
}
#[tauri::command]
fn get_legacy_status(state: Backend<'_>) -> LegacyStatus {
    state.legacy_status()
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn import_legacy(state: Backend<'_>, fingerprint: String) -> Result<ImportResponse> {
    let state = state.inner().clone();
    blocking(move || state.import_legacy(fingerprint)).await
}
#[tauri::command]
fn get_producer_templates(state: Backend<'_>) -> Vec<serde_json::Value> {
    state.producer_templates()
}
#[tauri::command]
async fn local_kafka_status(state: Backend<'_>) -> Result<ContainerStatus> {
    state.containers.status().await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn start_local_kafka(state: Backend<'_>) -> Result<ContainerStatus> {
    state.containers.start().await
}
#[tauri::command]
#[tracing::instrument(skip_all)]
async fn stop_local_kafka(state: Backend<'_>) -> Result<ContainerStatus> {
    state.containers.stop().await
}

pub fn run() {
    STARTED_AT.get_or_init(std::time::Instant::now);
    tracing_subscriber::fmt()
        .with_ansi(false)
        .with_span_events(tracing_subscriber::fmt::format::FmtSpan::CLOSE)
        .with_env_filter("kafi_kafi=info")
        .init();
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let path = app.path().app_data_dir()?;
            app.manage(Arc::new(AppState::new(path).map_err(|e| e.message)?));
            let config = app
                .config()
                .app
                .windows
                .first()
                .expect("main window configuration");
            tauri::WebviewWindowBuilder::from_config(app, config)?
                .on_navigation(|url| {
                    (url.scheme() == "tauri" && url.host_str() == Some("localhost"))
                        || (matches!(url.scheme(), "http" | "https")
                            && url.host_str() == Some("tauri.localhost"))
                        || (cfg!(debug_assertions)
                            && url.scheme() == "http"
                            && url.host_str() == Some("127.0.0.1")
                            && url.port() == Some(1420))
                })
                .build()?;
            tracing::info!("Kafi Kafi started");
            Ok(())
        })
        .on_page_load(|webview, _| {
            if let Ok(url) = webview.url() {
                let allowed = url.scheme() == "tauri"
                    || url.host_str() == Some("tauri.localhost")
                    || (cfg!(debug_assertions) && url.host_str() == Some("127.0.0.1"));
                if !allowed {
                    let _ = webview.close();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            ui_ready,
            get_profiles,
            save_profile,
            delete_profile,
            test_connection,
            connect,
            disconnect,
            get_cluster,
            get_brokers,
            list_topics,
            describe_topic,
            create_topic,
            delete_topic,
            update_topic_config,
            produce_message,
            start_consumer,
            acknowledge_batch,
            pause_consumer,
            resume_consumer,
            stop_consumer,
            set_consumer_filter,
            get_message_detail,
            export_message,
            choose_certificate,
            list_consumer_groups,
            describe_consumer_group,
            preview_group_offsets,
            reset_group_offsets,
            delete_consumer_group,
            get_settings,
            save_settings,
            get_legacy_status,
            import_legacy,
            get_producer_templates,
            local_kafka_status,
            start_local_kafka,
            stop_local_kafka
        ])
        .build(tauri::generate_context!())
        .expect("build desktop application")
        .run(|app, event| {
            if matches!(event, tauri::RunEvent::Exit) {
                let state = app.state::<Arc<AppState>>();
                tauri::async_runtime::block_on(state.disconnect());
                tracing::info!("Kafi Kafi exited");
            }
        });
}
