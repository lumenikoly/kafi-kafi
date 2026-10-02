use crate::{
    error::{AppError, Result},
    ipc::dto::ContainerStatus,
};
use tokio::process::Command;
pub const NAME: &str = "kafi-kafi-kraft";
pub const LABEL: &str = "com.kafikafi.owner=kafi-kafi";
pub const IMAGE: &str = "apache/kafka:3.9.1";
pub struct ContainerManager {
    operation: tokio::sync::Mutex<()>,
}
impl Default for ContainerManager {
    fn default() -> Self {
        Self {
            operation: tokio::sync::Mutex::new(()),
        }
    }
}
fn command(runtime: &str) -> Command {
    let mut command = Command::new(runtime);
    command.kill_on_drop(true);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    command
}
async fn execute(runtime: &str, args: &[&str]) -> Result<std::process::Output> {
    tokio::time::timeout(
        std::time::Duration::from_secs(60),
        command(runtime).args(args).output(),
    )
    .await
    .map_err(|_| AppError::new("TIMEOUT", "Container runtime did not respond."))?
    .map_err(|_| {
        AppError::new(
            "CONTAINER_RUNTIME_NOT_FOUND",
            "Install and start Podman or Docker.",
        )
    })
}
async fn runtime() -> Option<String> {
    for name in ["podman", "docker"] {
        if let Ok(out) = execute(name, &["info", "--format", "json"]).await
            && out.status.success()
        {
            return Some(name.into());
        }
    }
    None
}
async fn inspect(runtime: &str) -> Result<Option<serde_json::Value>> {
    let out = execute(runtime, &["inspect", NAME]).await?;
    if !out.status.success() {
        return Ok(None);
    }
    let value: serde_json::Value = serde_json::from_slice(&out.stdout)
        .map_err(|_| AppError::new("CONTAINER_FAILED", "Invalid container status response."))?;
    let container = value
        .as_array()
        .and_then(|a| a.first())
        .cloned()
        .ok_or_else(|| AppError::new("CONTAINER_FAILED", "No container status returned."))?;
    if container["Config"]["Labels"]["com.kafikafi.owner"] != "kafi-kafi" {
        return Err(AppError::new(
            "PERMISSION_DENIED",
            "A container with this name belongs to another application. It will not be changed.",
        ));
    }
    Ok(Some(container))
}
impl ContainerManager {
    pub async fn status(&self) -> Result<ContainerStatus> {
        let runtime = runtime().await;
        let state = if let Some(runtime) = &runtime {
            inspect(runtime)
                .await?
                .map(|c| c["State"]["Status"].as_str().unwrap_or("unknown").into())
                .unwrap_or("missing".into())
        } else {
            "unavailable".into()
        };
        Ok(ContainerStatus {
            runtime,
            state,
            image: IMAGE.into(),
        })
    }
    pub async fn start(&self) -> Result<ContainerStatus> {
        let _guard = self.operation.lock().await;
        let runtime = runtime().await.ok_or_else(|| {
            AppError::new(
                "CONTAINER_RUNTIME_NOT_FOUND",
                "Install and start Podman or Docker.",
            )
        })?;
        let existing = inspect(&runtime).await?;
        let out = if existing.is_some() {
            execute(&runtime, &["start", NAME]).await?
        } else {
            execute(
                &runtime,
                &[
                    "run",
                    "-d",
                    "--name",
                    NAME,
                    "--label",
                    LABEL,
                    "-p",
                    "127.0.0.1:9092:9092",
                    "-e",
                    "KAFKA_NODE_ID=1",
                    "-e",
                    "KAFKA_PROCESS_ROLES=broker,controller",
                    "-e",
                    "KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093",
                    "-e",
                    "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092",
                    "-e",
                    "KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER",
                    "-e",
                    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
                    "-e",
                    "KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093",
                    "-e",
                    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
                    "-e",
                    "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1",
                    "-e",
                    "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1",
                    "-e",
                    "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0",
                    IMAGE,
                ],
            )
            .await?
        };
        if !out.status.success() {
            return Err(AppError::new(
                "CONTAINER_FAILED",
                "Cannot start local Kafka. Check runtime permissions and port 9092.",
            ));
        }
        tracing::info!(runtime, "Local Kafka container started");
        self.status().await
    }
    pub async fn stop(&self) -> Result<ContainerStatus> {
        let _guard = self.operation.lock().await;
        let runtime = runtime().await.ok_or_else(|| {
            AppError::new("CONTAINER_RUNTIME_NOT_FOUND", "Start Podman or Docker.")
        })?;
        if inspect(&runtime).await?.is_some() {
            let out = execute(&runtime, &["stop", NAME]).await?;
            if !out.status.success() {
                return Err(AppError::new(
                    "CONTAINER_FAILED",
                    "Cannot stop the owned Kafka container.",
                ));
            }
        }
        tracing::info!(runtime, "Local Kafka container stopped");
        self.status().await
    }
}
