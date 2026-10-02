use crate::error::{AppError, Result};
use rdkafka::{
    admin::AdminClient, client::ClientContext, config::ClientConfig, producer::FutureProducer,
};
use std::sync::{Arc, Mutex};
#[derive(Clone, Default)]
pub struct QuietContext {
    security_error: Arc<Mutex<Option<AppError>>>,
}
impl ClientContext for QuietContext {
    fn log(&self, _: rdkafka::config::RDKafkaLogLevel, _: &str, _: &str) {}
    fn error(&self, error: rdkafka::error::KafkaError, _: &str) {
        let error = AppError::from(error);
        if matches!(error.code.as_str(), "AUTHENTICATION_FAILED" | "TLS_FAILED") {
            *self.security_error.lock().expect("security error lock") = Some(error);
        }
    }
}
pub struct Connection {
    pub profile_id: String,
    pub generation: String,
    pub config: ClientConfig,
    pub admin: AdminClient<QuietContext>,
    pub producer: FutureProducer<QuietContext>,
}
impl Connection {
    pub fn open(profile_id: String, mut config: ClientConfig) -> Result<Self> {
        // Silence native logs: broker supplied error strings may include credentials.
        config.set("log_level", "0");
        let context = QuietContext::default();
        // The producer polls native error events while the metadata call waits.
        // Keep only safe error codes, never broker-provided authentication text.
        let producer = config.create_with_context(context.clone())?;
        let admin: AdminClient<_> = config.create_with_context(context.clone())?;
        if let Err(error) = admin.inner().fetch_metadata(None, super::TIMEOUT) {
            return Err(context
                .security_error
                .lock()
                .expect("security error lock")
                .clone()
                .unwrap_or_else(|| error.into()));
        }
        Ok(Self {
            profile_id,
            generation: uuid::Uuid::new_v4().to_string(),
            config,
            admin,
            producer,
        })
    }
}
