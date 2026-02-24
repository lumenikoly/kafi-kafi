## Technical Specification (TS) for an AI Agent

**Project:** Desktop Kafka Client (modern Kafka Tool / IntelliJ-like Kafka UI)
**Tech stack:** Kotlin + Compose Multiplatform (Desktop/JVM)
**Goal:** Build a modern cross-platform desktop tool to browse and produce Kafka messages with UX similar to Kafka tooling in IntelliJ IDEA.

---

# 1. Scope and goals

## 1.1. Primary objective

Deliver a desktop application for **Windows/macOS/Linux** that allows users to:

* connect to Kafka clusters via connection profiles;
* browse topics/partitions and view metadata;
* **consume** messages (tail/from offset/by time), filter/search/export;
* **produce** messages (key/value/headers/partition), with templates/history;
* run fully locally, without any server component.

## 1.2. UX reference

UI and capabilities should be **similar to the Kafka tool experience in IntelliJ**:

* left: clusters/topics navigation,
* center: messages table,
* right: message inspector,
* quick filters, keyboard shortcuts, context menus, split panes.

## 1.3. Non-goals (for V1)

* full monitoring platform (Prometheus/Grafana class);
* high-risk cluster operations (topic delete, alter configs) — **disabled by default**, optionally behind a feature flag later;
* implementing our own Schema Registry/broker/proxy.

---

# 2. Platforms and environment

## 2.1. Target platforms

* Windows 10+ (x64)
* macOS 12+ (arm64/x64)
* Linux (x64, glibc)

## 2.2. Build and runtime

* JDK: 17 (fixed for the project)
* Gradle Kotlin DSL
* Distributions: `.msi/.exe`, `.dmg`, `.deb/.rpm/.tar.gz` via `jpackage` (or Compose packaging)

---

# 3. Functional requirements (V1)

## 3.1. Connection profiles

### 3.1.1. Entities

* **Cluster Profile**:

  * name
  * bootstrap servers (list)
  * security: PLAINTEXT / SSL / SASL_SSL / SASL_PLAINTEXT
  * SASL mechanism: PLAIN / SCRAM-SHA-256 / SCRAM-SHA-512 / OAUTHBEARER (V1 minimum: PLAIN + SCRAM)
  * username/password (if applicable)
  * SSL: truststore/keystore paths + passwords (or PEM paths)
  * client.id (optional)
  * additional properties (key/value) with validation

### 3.1.2. CRUD

* Create/edit/delete profile
* “Test connection”
* Quick switch for active profile

### 3.1.3. Secret storage

* Secrets must not be stored in plaintext.
* Requirement: **Secure storage**:

  * preferred: OS keychain/credential store (if feasible on JVM);
  * fallback: local encryption (AES-GCM) with a user master password or machine-bound key.
* V1 may ship with the fallback, but **plaintext storage is forbidden**.

---

## 3.2. Cluster navigation

* Topics list (with search/filter)
* Topic details:

  * partitions count
  * replication factor (if available)
  * partition leaders / ISR (via AdminClient when possible)
  * configs (read-only)
* Consumer groups list (V1: read-only)

  * view lag/offsets if feasible without heavy load

---

## 3.3. Message browsing (Consumer)

### 3.3.1. Read modes

* Tail (stream new records)
* From offset:

  * earliest / latest
  * specific offset
  * by time (timestamp → offsetsForTimes)
* Selection:

  * topic
  * partitions: all or selected
  * consumer group id (optional)
  * auto commit: on/off
  * manual commit (button)
  * max records per poll
  * poll interval / throttle (simple controls)

### 3.3.2. Message presentation

Main table:

* time (timestamp)
* partition
* offset
* key (preview)
* value (preview)
* headers (icon/count)
* size (bytes)

Inspector (right pane):

* raw key/value (bytes + hex/base64)
* decoded key/value (string/json)
* headers (list of key/value bytes)
* metadata (topic/partition/offset/timestamp)
* actions: “Copy field”, “Copy JSON”, “Save to file”

### 3.3.3. Filters and search

* filter by partition
* key contains / regex
* value contains / regex
* headers key/value filters
* time range filter (from/to) for locally loaded data
* search within current loaded set (client-side)
* saved filters per profile/topic

### 3.3.4. Limits and performance

* ring buffer for loaded messages (e.g., 5k–50k, configurable)
* lazy/virtual rendering for large lists
* backpressure: if UI can’t keep up, slow down/pause consumer
* controls: Pause / Resume / Clear

### 3.3.5. Export

* export current loaded set:

  * JSON Lines
  * CSV (minimum)
* “Export selected” / “Export all loaded”

---

## 3.4. Producing messages (Producer)

### 3.4.1. Send fields

* topic
* partition: auto / fixed
* key: string / base64 / hex
* value: string / json / base64 / hex
* headers: list (key + value as string/base64/hex)
* acks (0/1/all)
* compression (none/gzip/snappy/lz4/zstd) — optional
* idempotence — optional

### 3.4.2. Templates and history

* Templates:

  * save a message form preset (no secrets)
  * quick select
* History (last N sends):

  * show status + timestamp
  * resend action

### 3.4.3. Validation

* JSON validation when JSON mode is selected
* warning for large messages (e.g., > 1MB)

---

## 3.5. Decoding and formatting (V1 + extensibility)

### 3.5.1. Built-in decoders (V1)

* String (UTF-8, with “replace invalid” option)
* JSON viewer:

  * pretty print
  * collapsible tree
* Bytes viewers:

  * hex
  * base64

### 3.5.2. Pluggable decoder architecture (foundation)

Define SPI/interface:

* `Decoder` with:

  * `canDecode(contentType, headers, topic)` (optional)
  * `decode(bytes): DecodedResult`
* register via ServiceLoader or explicit registry module.
  V1 should implement the infrastructure plus 2–3 built-in decoders.

---

# 4. Non-functional requirements

## 4.1. UX/UI

* 3-pane layout with resizable split panes
* Light/dark themes (system default + manual toggle)
* Keyboard shortcuts (minimum):

  * Ctrl/Cmd+K — topic search
  * Ctrl/Cmd+F — search within messages
  * Space — pause/resume consumer
  * Ctrl/Cmd+Enter — send message
* Context menus:

  * Copy cell / Copy row / Copy JSON
  * Jump to offset (if feasible)
* Empty states, connection errors, hints.

## 4.2. Reliability and error handling

* All network/IO operations isolated from UI thread; errors shown via toast/banner + details in a “Logs” panel.
* Limited retries for transient errors.
* Timeouts on all operations.

## 4.3. Security

* Never log secrets.
* Mask secrets in UI.
* Optional future: redact values on copy/export (not required in V1, but leave a hook).

## 4.4. Local persistence

Persist:

* connection profiles (without secrets)
* UI preferences (theme, split sizes)
* saved filters/templates/history
  Format:
* versioned JSON with migrations (simple strategy).
  Location:
* OS-conventional user app data directory.

---

# 5. Architecture and project structure

## 5.1. Approach

* **Desktop-only runtime** (JVM) for V1.
* Layers:

  1. `core-kafka` — wrappers over Kafka clients (Admin/Consumer/Producer), domain models
  2. `core-storage` — local persistence (profiles/templates/history)
  3. `ui` — Compose UI, navigation, screen state
  4. `app-desktop` — entrypoint, DI, packaging

## 5.2. UI pattern

* Unidirectional Data Flow (UDF) / lightweight MVI:

  * `State` + `Intent` + `Reducer`/`ViewModel`
* Async:

  * Kotlin Coroutines + Flow
  * dedicated scope for consumer streaming

## 5.3. Kafka layer services

Implement:

* `KafkaAdminService`

  * listTopics(), describeTopic(), listGroups(), describeGroup(), fetchOffsets(), etc.
* `KafkaConsumerService`

  * startSession(params)
  * pause/resume
  * stop
  * commit (manual)
  * events: `MessageReceived`, `Error`, `Stats`
* `KafkaProducerService`

  * send(message) → result with metadata/error

Must: properly close clients when switching profile/topic or on app exit.

---

# 6. Screen model (minimum V1)

## 6.1. Main window

* Top bar:

  * active profile selector
  * topic search
  * settings
* Left panel:

  * navigation: Profiles → Topics
  * optional “Groups” tab
* Center:

  * IDE-like tabs: each tab is a topic session
  * messages table + filter bar
* Right inspector:

  * decoded/raw views, headers, copy actions

## 6.2. Connection manager (dialog/screen)

* profiles list
* create/edit wizard
* test connection
* advanced properties editor

## 6.3. Producer (panel/dialog)

* send form
* templates list
* history list

## 6.4. Settings

* theme
* buffer size
* default poll batch size
* export defaults

---

# 7. Logging and diagnostics

* App logs (INFO/WARN/ERROR) to file + read-only viewer in UI.
* Kafka client logs separately; ability to reduce verbosity.
* In UI: “Diagnostics” panel:

  * last error
  * consumer status (running/paused)
  * approximate messages/sec

---

# 8. Testing and quality

## 8.1. Unit tests

* storage serialization/migrations
* filters/search/regex parsing
* producer message building + validation

## 8.2. Integration tests (minimum)

* Testcontainers Kafka:

  * start Kafka
  * create topic
  * send messages
  * read from earliest/latest, verify domain model
* CI runs unit + integration (where feasible)

## 8.3. Acceptance criteria (V1)

1. User can create a profile, test connection, and connect.
2. User can see topics list, open a topic tab, and tail messages.
3. User can read from earliest/latest/specific offset/timestamp.
4. Filters (contains/regex) work for key/value.
5. User can produce a message (key/value/headers) and see it in the consumer view.
6. Secrets are not stored in plaintext.
7. App builds and runs on Win/macOS/Linux; installers/packages exist.

---

# 9. Work plan for the AI agent (task breakdown)

## Stage A — Repository bootstrap

* Create Gradle multi-module project
* Add Compose Multiplatform Desktop
* Configure formatting/linting (optional: ktlint/detekt)
* Setup packaging (jpackage)

## Stage B — Core Kafka

* Implement `KafkaAdminService` (topics, describe)
* Implement `KafkaProducerService` (send + results)
* Implement `KafkaConsumerService` (session, offsets, streaming Flow)
* Handle cancel/close, timeouts, errors

## Stage C — Storage

* Models for profiles/templates/history
* JSON store + versioning
* Secure secrets: baseline implementation + interface for future OS keychain

## Stage D — UI V1

* Main layout (left/topics, center/messages, right/inspector)
* Virtualized messages table/list
* Filters/search, pause/resume
* Producer panel + templates/history
* Connection manager UI

## Stage E — Export + Diagnostics

* Export JSONL/CSV
* Logs to file + UI viewer

## Stage F — Tests + Release

* Unit + testcontainers integration
* CI workflow
* Release builds for 3 OS

---

# 10. Implementation constraints and principles

* No network/IO on UI thread.
* Message streaming must tolerate UI lag (backpressure).
* No consumer/producer leaks when closing tabs.
* Avoid storing huge payloads without limits: previews capped (e.g., 4–16 KB). Full payload display is best-effort, otherwise show truncated.
* JSON pretty print must be safe with size limits.

---

# 11. Deliverables

* Source code repository
* README:

  * install/run
  * build packages
  * connection/security configuration
* Release artifacts for 3 OS
* Test suite + CI configuration

---

# 12. Assumptions (to keep development unblocked)

* V1 targets Kafka broker 2.x/3.x via a recent `kafka-clients`.
* Schema Registry/Avro/Protobuf are **post-V1**, but decoder SPI is included in V1.
* V1 secure storage may use encrypted local storage as a fallback; OS keychain integrations can be a V1.1 milestone.
