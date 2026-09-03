# Run and connect

## Prerequisites

- JDK 25.
- Network access to a Kafka bootstrap server.
- Podman or Docker only if you want to use the built-in local Kafka KRaft launcher.

## Run from source

```bash
./gradlew :app-desktop:run
```

Run the unit tests with:

```bash
./gradlew test
```

The integration tests use Testcontainers and require a running Docker-compatible environment:

```bash
./gradlew :core-kafka:integrationTest
```

## Connect to a cluster

1. Open `Connections` in the sidebar.
2. Click `+` to create a profile, then enter a name and a comma-separated list of `Bootstrap Servers`.
3. Select the required security protocol and enter any SASL or SSL settings.
4. Click `Test Connection`.
5. Click `Save`, then click `Connect`.

The application marks the profile as connected only after it successfully lists the cluster's topics. If the attempt fails, the current active connection remains unchanged.

## Start a local Kafka cluster

The `Local Kafka` section appears at the bottom of the sidebar. Click `Start` to launch a single-node KRaft container on `localhost:9092`; the launcher uses Podman when available and otherwise uses Docker. After the status reports that the container is running, create a profile for `localhost:9092` and test the connection.
