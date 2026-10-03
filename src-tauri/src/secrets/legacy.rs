//! Read-only compatibility with Kotlin PBKDF2WithHmacSHA256 + AES/GCM/NoPadding.
use crate::error::{AppError, Result};
use aes_gcm::{Aes256Gcm, KeyInit, Nonce, aead::Aead};
use base64::{Engine, engine::general_purpose::STANDARD};
use zeroize::Zeroizing;

pub fn decrypt(
    fingerprint: &str,
    salt: &[u8],
    iv: &str,
    ciphertext: &str,
) -> Result<Zeroizing<String>> {
    let mut key = Zeroizing::new([0u8; 32]);
    pbkdf2::pbkdf2_hmac::<sha2::Sha256>(fingerprint.as_bytes(), salt, 120_000, &mut *key);
    let invalid = || {
        AppError::new(
            "MIGRATION_FAILED",
            "Cannot decrypt legacy secrets. Use the original machine fingerprint or re-enter credentials.",
        )
    };
    let nonce = STANDARD.decode(iv).map_err(|_| invalid())?;
    if nonce.len() != 12 {
        return Err(invalid());
    }
    let bytes = STANDARD.decode(ciphertext).map_err(|_| invalid())?;
    let cipher = Aes256Gcm::new_from_slice(&*key).map_err(|_| invalid())?;
    let plaintext = Zeroizing::new(
        cipher
            .decrypt(Nonce::from_slice(&nonce), bytes.as_ref())
            .map_err(|_| invalid())?,
    );
    String::from_utf8(plaintext.to_vec())
        .map(Zeroizing::new)
        .map_err(|_| invalid())
}
