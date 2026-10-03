# Develop the Tauri application

Install Node 22.18.0, pnpm 11.25.0 and the Rust toolchain pinned in `rust-toolchain.toml`. Native build machines need a C/C++ compiler, CMake and Perl. Windows needs the MSVC build tools and native Strawberry Perl; Cygwin/MSYS Perl cannot build the vendored OpenSSL library. Linux also needs WebKitGTK 4.1, appindicator, librsvg, patchelf, D-Bus and libcurl development packages. On Ubuntu, install `libcurl4-openssl-dev`: the locked librdkafka build configuration requires `curl/curl.h` even when its CURL transport is disabled. macOS needs Xcode command-line tools.

On Windows, dot-source `scripts/native-env.ps1` in PowerShell before the build commands. It loads the installed Visual Studio x64 environment and places native Strawberry Perl and CMake before MSYS/Cygwin tools in the process PATH. The optional local portable Perl location is `.tmp/toolchain/strawberry-perl/perl/bin`.

```sh
pnpm install --frozen-lockfile
pnpm tauri dev
```

The development Vite server serves the interface only. Production bundles the compiled interface inside Tauri and does not run a local HTTP backend. librdkafka and TLS libraries are built from the locked sources; users do not install Rust, Node or Kafka native libraries.

## Verify a change

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm exec playwright install chromium
pnpm test:browser
pnpm build
cargo fmt --manifest-path src-tauri/Cargo.toml --check
cargo clippy --manifest-path src-tauri/Cargo.toml --locked --all-targets --all-features -- -D warnings
cargo test --manifest-path src-tauri/Cargo.toml --locked
toudocu check docs --repository-root .
```

Rust DTO export tests generate the TypeScript contract. Commit `src/ipc/generated/` with the Rust changes; CI rejects generated changes that were not committed. Real Kafka integration tests are opt-in local checks and are not executed by CI or the release workflow. They require an explicit bootstrap environment variable; an ordinary local test run skips them.

```sh
bash scripts/kafka-fixture.sh start
KAFI_TEST_KAFKA_BOOTSTRAP=127.0.0.1:19092 cargo test --manifest-path src-tauri/Cargo.toml --locked --test kafka_integration
bash scripts/kafka-fixture.sh stop
```

Set `KAFI_CONTAINER_RUNTIME=podman` for Podman. The script checks the fixture's ownership label before deletion. Windows can run the same test target with a separately started fixture and `$env:KAFI_TEST_KAFKA_BOOTSTRAP='127.0.0.1:19092'`. Both fixtures bind their published ports to IPv4 loopback and advertise `127.0.0.1` to clients. Use that address for the test bootstrap to avoid resolving `localhost` to the unpublished IPv6 loopback address.

The separate security fixture verifies SASL_SSL with PLAIN, SCRAM-SHA-256 and SCRAM-SHA-512, including rejection of incorrect passwords and untrusted certificates. Its certificates and password are public test data; never use them outside the isolated fixture.

```sh
bash scripts/kafka-security-fixture.sh start
KAFI_TEST_KAFKA_SECURE_BOOTSTRAP=127.0.0.1:19094 KAFI_TEST_KAFKA_SECURE_USERNAME=test-user KAFI_TEST_KAFKA_SECURE_PASSWORD=kafi-test-password cargo test --manifest-path src-tauri/Cargo.toml --locked --test kafka_security_integration
bash scripts/kafka-security-fixture.sh stop
```

The default security-fixture runtime is Podman; set `KAFI_CONTAINER_RUNTIME=docker` for Docker. On Windows, explicitly set `$env:KAFI_TEST_SYSTEM_CREDENTIALS='1'` to run the system credential round-trip test in `storage_and_secrets`. It writes a unique dummy credential and deletes it after checking retrieval.

The Windows/Podman local-manager test is also opt-in. It requires the fixed `kafi-kafi-kraft` name to be absent and refuses to touch a pre-existing container. It starts Kafka through the production manager, connects to `localhost:9092`, then stops and retains the owned container. After checking the result, remove only that test-created container if you want to run the test again.

```powershell
$env:KAFI_TEST_LOCAL_KAFKA='1'
cargo test --manifest-path src-tauri/Cargo.toml --locked --test local_kafka_integration
```

Tauri/Rust/React is the only application source. Development and CI do not require Gradle or a JDK. The read-only legacy importer and its Rust fixtures remain to preserve migration of existing user data.
