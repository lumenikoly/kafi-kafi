use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, Deserialize, Serialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export, export_to = concat!(env!("CARGO_MANIFEST_DIR"), "/../src/ipc/generated/"))]
pub struct AppError {
    pub code: String,
    pub message: String,
    pub details: Option<String>,
    pub retryable: bool,
}
pub type Result<T> = std::result::Result<T, AppError>;
impl AppError {
    pub fn new(code: &str, message: &str) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            details: None,
            retryable: matches!(code, "TIMEOUT" | "CONNECTION_FAILED"),
        }
    }
    pub fn invalid(message: &str) -> Self {
        Self::new("INVALID_INPUT", message)
    }
}
impl From<std::io::Error> for AppError {
    fn from(_: std::io::Error) -> Self {
        Self::new(
            "STORAGE_FAILED",
            "Cannot read or write application storage. Check disk permissions and free space.",
        )
    }
}
impl From<serde_json::Error> for AppError {
    fn from(_: serde_json::Error) -> Self {
        Self::new(
            "STORAGE_FAILED",
            "Storage contains invalid JSON; restore the previous snapshot.",
        )
    }
}
impl From<rdkafka::error::KafkaError> for AppError {
    fn from(error: rdkafka::error::KafkaError) -> Self {
        use rdkafka::error::RDKafkaErrorCode as C;
        let (code, message) = match error.rdkafka_error_code() {
            Some(C::Authentication | C::SaslAuthenticationFailed) => (
                "AUTHENTICATION_FAILED",
                "Kafka authentication failed. Check the mechanism and credentials.",
            ),
            Some(C::SSL) => (
                "TLS_FAILED",
                "TLS negotiation failed. Check certificates and hostname.",
            ),
            Some(
                C::TopicAuthorizationFailed
                | C::GroupAuthorizationFailed
                | C::ClusterAuthorizationFailed,
            ) => (
                "PERMISSION_DENIED",
                "Kafka denied this operation. Check cluster ACLs.",
            ),
            Some(C::UnknownTopicOrPartition) => {
                ("TOPIC_NOT_FOUND", "The topic or partition does not exist.")
            }
            Some(C::OperationTimedOut | C::RequestTimedOut | C::MessageTimedOut) => {
                ("TIMEOUT", "Kafka did not respond before the timeout.")
            }
            _ => (
                "CONNECTION_FAILED",
                "Kafka operation failed. Check the broker address, connection and operation inputs.",
            ),
        };
        Self::new(code, message)
    }
}
