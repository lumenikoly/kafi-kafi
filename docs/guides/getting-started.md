# Install, run, and connect

Use a native release for normal use or run the application from source for development. Either option requires a Kafka bootstrap address and any credentials required by that cluster.

## Install a release

Published releases provide native files on the [GitHub Releases](https://github.com/lumenikoly/kafi-kafi/releases) page:

- Linux: download the AppImage, make it executable, and run it.
- Windows: use the MSI installer or extract the portable ZIP.
- macOS: open the DMG image and launch Kafi Kafi.

Linux AppImage example:

```bash
chmod +x KafiKafi-*.AppImage
./KafiKafi-*.AppImage
```

## Run from source

Install JDK 25 and run the included Gradle wrapper:

```bash
./gradlew :app-desktop:run
```

On Windows, use:

```powershell
gradlew.bat :app-desktop:run
```

The wrapper downloads the configured Gradle version automatically. Podman or Docker is optional unless you use the local Kafka launcher.

## Connect to a cluster

1. Open **Connections** from the plug icon in the left navigation rail. Hover over an icon to see its name.
2. Click the **+** icon in the profile pane and enter a profile name and one or more comma-separated **Bootstrap Servers**, such as `broker-1:9092,broker-2:9092`.
3. Select the security protocol required by the cluster. For SASL or SSL, complete the credential and certificate fields that appear.
4. Click **Test Connection**. A successful result shows the topic count and connection latency.
5. Click **Save**, then **Connect**.

Kafi Kafi changes the active connection only after it can list topics from the selected cluster. If the connection fails, the previous active connection remains available and the editor shows the error.

## Start a local Kafka cluster

The control at the bottom of the navigation rail can run a single-node Apache Kafka KRaft container on Windows, macOS, or Linux. Its status dot shows whether the local broker is running; hover over the control to see the available action. The launcher requires the `podman` or `docker` command to be available on `PATH`; it does not require a separate shell.

1. Start Podman or Docker.
2. Click the play icon at the bottom of the navigation rail.
3. Wait until the panel reports that the container is running.
4. Create and connect a profile for `localhost:9092`.

Kafi Kafi prefers Podman when both engines are installed. The container is named `kafka-kraft`; clicking the stop icon stops it without deleting it, so a later start reuses the same container.

## Troubleshooting

- **Neither podman nor docker is available:** install and start either container engine, or connect to an existing Kafka cluster instead.
- **Connection test fails:** verify the bootstrap addresses, security protocol, credentials, certificate paths, network access, and Kafka ACLs.
- **The application cannot read saved credentials:** restore both files in the original `~/.lightkafka/secrets` directory while keeping the same username, operating system name, and home-directory path, or re-enter the password fields and save the profile again.
