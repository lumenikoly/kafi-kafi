<!-- toudocu
architectureQuestion: How does the desktop interface request work without gaining general system access?
-->

# Desktop interface boundary

React requests named operations through Tauri commands. Rust validates inputs and owns Kafka clients, storage, credentials and container processes. The WebView receives no general filesystem or shell permission.

## Scope

This document explains the trust and ownership boundary. Exact request and response shapes are generated from Rust into `src/ipc/generated/`; `src/ipc/client.ts` exposes the command signatures used by the interface.

The command adapters in `src-tauri/src/ipc/` call the application layer. The application layer chooses the active connection, rejects stale offset-reset previews and manages session ownership. The Kafka layer encapsulates librdkafka, including a small native DescribeCluster adapter for controller and rack metadata. Domain profile validation permits an explicit list of additional Kafka properties; arbitrary properties cannot replace managed authentication, TLS verification or consumer isolation settings.

Errors use a code, a safe message, optional details and a retry flag. Native Kafka error text, payloads and credentials are not returned as error details. Blocking broker operations, keyring access and disk writes run on the background pool. Runtime locks protect short in-memory changes; Kafka requests execute after those locks are released. A separate storage lock serializes snapshot mutation and disk writes on background workers.

Certificate selection and record export use Rust-mediated native dialogs. The export command writes only the file selected in the save dialog. Local Kafka commands invoke Podman or Docker with argument arrays and verify container ownership before mutation. Production content is bundled locally. The Content Security Policy excludes remote scripts and frames; a separate WebView guard restricts document navigation to the local application origin.

See [credential ownership](../security/credentials.md) and [message delivery](message-streaming.md) for the related boundaries.
