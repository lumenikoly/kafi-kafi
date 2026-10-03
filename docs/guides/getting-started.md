# Install, run, and connect

Use the Tauri source build for development. Check release notes when downloading an older published version for its runtime and supported migration formats.

## Run the new application

Follow [development prerequisites](../development.md), then run:

```sh
pnpm install --frozen-lockfile
pnpm tauri dev
```

Production packages contain the interface and native Kafka client. They use the operating system's WebView; a JVM is not required.

## Connect to a cluster

1. Open **Connections**, then **New**.
2. Enter a name and comma-separated bootstrap servers such as `broker-1:9092,broker-2:9092`.
3. Select PLAINTEXT, SSL, SASL_PLAINTEXT or SASL_SSL. For SASL, choose PLAIN, SCRAM-SHA-256 or SCRAM-SHA-512 and enter a username and password.
4. For TLS, choose a PEM CA, PEM client certificate and key, or a PKCS#12 keystore. Leaving CA blank uses system trust. JKS requires manual conversion.
5. Click **Test connection**. The result reports the cluster and broker count.
6. Click **Save**, then **Connect** beside the saved profile.

A failed switch preserves the active connection. Successful switching closes old message sessions. Credentials go to the system vault; if it is unavailable, the editor warns that the password is usable only for this process. Saved passwords are not returned to the editor.

## Choose the interface theme

Open **Settings → Theme** and select **Light** or **Dark**. The choice takes effect and saves immediately, including for the next launch. Existing installations default to Dark until a theme is selected. Changing the theme does not save other edits to message settings; use **Save settings** for those.

## Start local Kafka

1. Start Podman or Docker and ensure its command is on PATH.
2. Open **Settings** and click **Check runtime**.
3. Click **Start local Kafka**. Wait for the container to run, then allow Kafka a few seconds to finish startup.
4. Create and connect a PLAINTEXT profile for `localhost:9092`.

The application prefers reachable Podman, then Docker. It owns `kafi-kafi-kraft`, labelled `com.kafikafi.owner=kafi-kafi`, and uses `apache/kafka:3.9.1`. **Stop local Kafka** stops and retains that container. A container with the same name but a different label is never changed.

## Import or recover

Settings offers import when `~/.lightkafka/storage.json` exists. See [credential migration](../security/credentials.md) for the exact fingerprint and certificate conversion requirements. Old files are retained. If a connection fails, check bootstrap addresses, security settings, certificate paths, network access and Kafka ACLs. If saved passwords cannot be loaded, re-enter them and save the profile again.
