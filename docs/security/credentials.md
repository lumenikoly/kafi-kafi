# Credentials and legacy import

The Tauri application stores passwords in Windows Credential Manager, macOS Keychain or Linux Secret Service. Profile JSON contains credential references. If the credential service rejects a write, the password remains in memory for the current process and the interface warns that it was not saved. There is no plaintext disk fallback.

The application never returns saved passwords to React. Entered passwords use password inputs, are excluded from browser storage and are cleared after Save or Cancel. Native Kafka logging is suppressed; application tracing excludes credentials, private keys, complete payloads and sensitive configuration values. Certificate hostname verification and certificate verification are enabled; the extra-property allowlist cannot disable them.

## Import the Kotlin configuration

Settings detects `~/.lightkafka/storage.json` and offers an explicit import. Supply the original Java machine fingerprint in the form `user.name|os.name|user.home` when encrypted secrets are present. Use the exact original values, including Windows OS version naming and path separators; an incorrect fingerprint fails decryption.

The read-only compatibility module implements PBKDF2-HMAC-SHA256 with 120,000 iterations, a 256-bit key and AES-GCM with a 12-byte nonce and 128-bit authentication tag. It reads the legacy salt and encrypted file and decrypts all entries before writing credentials. Imported references receive a `legacy:` prefix so they cannot overwrite a current profile credential. Import preserves existing profiles with the same ID and appends producer templates. It replaces the application's settings with the imported record limit and start position, using new-runtime defaults for settings the Kotlin format did not contain. Successful imports are marked to prevent duplicates. Old files are not deleted.

Legacy JKS and Java truststore paths remain visible in imported profiles with a conversion warning. librdkafka cannot use JKS directly. Before connecting, replace truststores with a PEM CA file and keystores with PKCS#12, or supply a PEM client certificate and private key. Legacy OAUTHBEARER is retained with an unsupported-mechanism warning; the first Tauri version accepts PLAIN and both SCRAM mechanisms.


The imported truststore password reference is retained for migration, but is not passed to librdkafka: a PEM CA file has no password. Conversion currently uses external tooling. Keep the original files and run these commands with the original passwords at the tool prompts:

```sh
keytool -list -keystore truststore.jks
keytool -exportcert -rfc -alias YOUR_CA_ALIAS -keystore truststore.jks -file ca.pem
keytool -importkeystore -srckeystore client.jks -srcstoretype JKS -destkeystore client.p12 -deststoretype PKCS12
```

Export each required CA alias and combine its PEM certificate into the chosen CA file. Select `ca.pem` as the CA path and `client.p12` as the PKCS#12 path in the imported profile. Enter the destination keystore password in the profile's PKCS#12 password field. Use tools only for this conversion; running the Tauri application does not require a JDK. Verify the converted profile against the intended broker before retiring the Kotlin profile.
## Recover local storage

The new snapshot lives in the platform application-data directory for `com.kafikafi.desktop`. Writes use a temporary file on the same volume, flush it and atomically replace the snapshot. The preceding valid snapshot is retained as `storage.backup.json`; a corrupt primary snapshot is read from that backup. If both are unreadable, startup reports a storage error rather than silently erasing profiles.

Legacy encrypted files are input to the read-only importer, not an active application storage backend. Their encryption does not make the copied salt and machine fingerprint a replacement for a system credential vault. Protect both the original files and the operating-system account during import.
