pub mod legacy;
use crate::error::{AppError, Result};
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
};
use zeroize::Zeroizing;

pub trait CredentialBackend: Send + Sync {
    fn write(&self, id: &str, value: &str) -> Result<()>;
    fn read(&self, id: &str) -> Result<Zeroizing<String>>;
    fn remove(&self, id: &str) -> Result<()>;
}
struct SystemCredentials;
impl CredentialBackend for SystemCredentials {
    fn write(&self, id: &str, value: &str) -> Result<()> {
        keyring::Entry::new("com.kafikafi.desktop", id)
            .and_then(|e| e.set_password(value))
            .map_err(|_| {
                AppError::new(
                    "SECRET_UNAVAILABLE",
                    "The system credential service could not save the password.",
                )
            })
    }
    fn read(&self, id: &str) -> Result<Zeroizing<String>> {
        keyring::Entry::new("com.kafikafi.desktop",id).and_then(|e|e.get_password()).map(Zeroizing::new).map_err(|_|AppError::new("SECRET_UNAVAILABLE","Enter the password again; the system credential store is unavailable or has no credential."))
    }
    fn remove(&self, id: &str) -> Result<()> {
        match keyring::Entry::new("com.kafikafi.desktop", id).and_then(|e| e.delete_credential()) {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(_) => Err(AppError::new(
                "SECRET_UNAVAILABLE",
                "Cannot remove the saved credential from the system store.",
            )),
        }
    }
}
pub struct SecretStore {
    ephemeral: Mutex<HashMap<String, Zeroizing<String>>>,
    backend: Arc<dyn CredentialBackend>,
}
impl Default for SecretStore {
    fn default() -> Self {
        Self::with_backend(Arc::new(SystemCredentials))
    }
}
impl SecretStore {
    pub fn with_backend(backend: Arc<dyn CredentialBackend>) -> Self {
        Self {
            ephemeral: Mutex::new(HashMap::new()),
            backend,
        }
    }
    pub fn put_ephemeral(&self, id: &str, value: String) {
        self.ephemeral
            .lock()
            .expect("secret lock")
            .insert(id.into(), Zeroizing::new(value));
    }
    pub fn remove_ephemeral(&self, id: &str) {
        self.ephemeral.lock().expect("secret lock").remove(id);
    }
    pub fn put(&self, id: &str, value: String) -> bool {
        let value = Zeroizing::new(value);
        let stored = self.backend.write(id, &value).is_ok();
        if stored {
            self.remove_ephemeral(id);
        } else {
            self.ephemeral
                .lock()
                .expect("secret lock")
                .insert(id.into(), value);
        }
        stored
    }
    pub fn get(&self, id: &str) -> Result<Zeroizing<String>> {
        if let Some(value) = self.ephemeral.lock().expect("secret lock").get(id) {
            return Ok(value.clone());
        }
        self.backend.read(id)
    }
    pub fn delete(&self, id: &str) -> Result<()> {
        // A failed overwrite may have left an older password in the vault.
        // Removing the memory fallback must also remove that persistent entry.
        self.remove_ephemeral(id);
        self.backend.remove(id)
    }
}
