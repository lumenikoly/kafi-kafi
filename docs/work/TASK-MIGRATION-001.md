<!-- toudocu
id: TASK-MIGRATION-001
status: draft
taskType: maintenance
updated: 2026-10-02
-->

# TASK-MIGRATION-001: Replace the desktop runtime with Tauri and preserve Kafka workflows

<!-- toudocu:section result -->
## Result

Run Kafi Kafi through Tauri, Rust and React with direct Kafka access, bounded message inspection and system credentials. The project owner has requested removal of the previous Kotlin implementation before the remaining platform and performance checks pass. Tauri/Rust/React is now the only application source; read-only legacy data import remains supported.

<!-- toudocu:section scope -->
## Scope

- `src/`, `src-tauri/`, `scripts/`, frontend and Rust toolchain configuration.
- Tauri CI/release workflows and canonical documentation.
- Rust compatibility fixtures for legacy storage formats and read-only credential import.

<!-- toudocu:section out-of-scope -->
## Out of scope

- An automatic updater, cloud service, telemetry or separate HTTP backend.
- Modifying or deleting legacy user storage automatically.

<!-- toudocu:section acceptance-criteria -->
## Acceptance criteria

- [x] `AC-01` Frontend validation, session controls, inspectors and confirmations pass behavioral tests.
- [x] `AC-02` Rust validation, byte/count bounds, filters, storage recovery and legacy decryptor pass unit tests.
- [x] `AC-03` Real Kafka verifies connections, topics, producer, start positions, groups, lag and reset.
- [ ] `AC-04` System credentials and SSL/SASL profiles work on each target platform; JKS users have an explicit conversion path.
- [ ] `AC-05` All four target builds produce installable packages and SHA-256 manifests after checks pass.
- [ ] `AC-06` Target-system measurements establish startup, package size, responsive inspection and bounded memory. Historical baseline comparisons remain evidence, not a runnable repository dependency.

<!-- toudocu:section plan -->
## Plan

1. Audit legacy formats and preserve fixtures.
2. Implement the native runtime, interface and bounded stream.
3. Verify unit and real Kafka behavior; fix regressions.
4. Validate source documentation and target packages.
5. Measure target-system performance and complete cross-platform release verification.

<!-- toudocu:section verification -->
## Verification

- `AC-01` -> `pnpm test`
- `AC-02` -> `cargo test --manifest-path src-tauri/Cargo.toml --lib --test storage_and_secrets --test consumer_buffer`
- `AC-03` -> `cargo test --manifest-path src-tauri/Cargo.toml --test kafka_integration` with `KAFI_TEST_KAFKA_BOOTSTRAP` set to the isolated fixture.
- `AC-04` -> Secure broker fixtures and system-keyring checks on each supported operating system.
- `AC-05` -> The Tauri release matrix and installation checks on Windows, Linux, Intel macOS and ARM macOS.
- `AC-06` -> Recorded cold-start, process-tree RSS, package size and sustained-stream measurements on each target system.
- Local evidence: 19 frontend tests, 34 Rust DTO export tests and 24 Rust storage/buffer tests passed. All three real Kafka integration scenarios passed, including an asserted two-record consumer-group lag and offset reset, as did the SASL_SSL security scenario for all three mechanisms and the opt-in Windows system credential round-trip. Native Windows UI smoke verified a real Kafka create/produce/read/pause/resume/stop/delete cycle. Generated TypeScript contracts pass typecheck; Rust clippy passes with warnings denied.
- The buffer microbenchmark plateaued at 10,000 records for 1 KiB payloads and 64 records for 1 MiB payloads. This is Rust buffer evidence, not full UI throughput evidence.
- The native Windows stream run verified exactly 100,000 real Kafka records, 10,000 retained rows, 90,000 evictions and 26 mounted table rows. The browser regression passes stream, viewport, resize and hidden-workspace session checks. See [releasing](../releasing.md) for sampling limits and memory observations.
- The opt-in Windows/Podman local-manager test passed: runtime detection, owned container creation, connection to `localhost:9092`, stop and retention of the stopped container. Other-platform local-manager behavior remains unverified.
- The Windows startup/package sample and failed idle-memory target are recorded in [releasing](../releasing.md). Other-platform builds and system-keyring checks, and controlled performance parity remain unverified.
- `DOCS` -> `toudocu check docs --repository-root .`

<!-- toudocu:section documentation-impact -->
## Documentation impact

Update the architecture question map, message and credential boundaries, guides, development and releasing instructions. Keep implementation evidence separate from unverified release/performance requirements.
