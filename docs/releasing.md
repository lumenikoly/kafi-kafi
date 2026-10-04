# Build and release the Tauri application

Start the `Tauri release` workflow manually from `main` with a new SemVer tag, `vX.Y.Z`. The tag must match `package.json`, `src-tauri/Cargo.toml` and `src-tauri/tauri.conf.json`. The workflow rejects other branches, malformed tags, version mismatches and tags that already exist. Do not create a migration release until the [migration acceptance criteria](work/TASK-MIGRATION-001.md) are verified.

1. Merge the release changes into `main`, including the matching version in all three manifests.
2. Open GitHub **Actions → Tauri release → Run workflow**.
3. Select **main**, enter the **tag** (for example `v1.1.0`), and click **Run workflow**. Do not create or push the tag beforehand.

The next prepared version is [1.1.0](releases/1.1.0.md). Its release notes distinguish implemented changes from the remaining publication gates.

After all checks and platform packages succeed, the workflow creates the tag on the exact commit selected when the run started and publishes a GitHub Release with the packages, checksum manifests and generated release notes. Pushing a tag does not start a release. Release runs are serialized; a new run does not cancel one already in progress. The workflow must be merged into the repository's default branch before GitHub shows the manual launch button.

Ordinary CI and release checks require frontend validation, Rust lint and unit tests, and native compilation on all four target platforms. Real Kafka integration tests run locally using the commands in [development](development.md); CI does not start brokers or execute these tests. The release workflow then builds Windows x86_64 NSIS and portable ZIP packages, Linux x86_64 AppImage, and separate Intel and Apple Silicon macOS DMGs on their respective operating systems. Each artifact directory includes a SHA-256 manifest. Publishing depends on every platform build succeeding.

For a local package:

```sh
pnpm tauri build
```

Platform prerequisites are described in [development](development.md). The Windows portable executable uses ordinary application-data storage. Windows uses the system WebView2 runtime; the installer can bootstrap it when missing. macOS uses the system WebKit and Linux packages use WebKitGTK. No separate Chromium or JVM is bundled.

Desktop package icons are listed explicitly in `bundle.icon` in `src-tauri/tauri.conf.json`. Keep the referenced files under `src-tauri/icons`: square PNGs for Linux, ICO for Windows and ICNS for macOS. AppImage packaging requires a square PNG in that list; having icon files in the directory alone does not supply them to the bundler.

The first workflow does not configure code-signing credentials, notarization or an automatic updater. Unsigned packages may require operating-system approval to launch. Add signing through repository secrets before a signed public release. GitHub Releases remains the distribution channel.

## Performance release gate

Record cold start to interactive UI, idle RSS including WebView child processes, package size, sustained message delivery and buffer memory plateau on each target system. The targets are under 1.5 s startup, approximately 150 MiB idle RSS, and preferably under 40 MiB packages; these are measurements to collect, not verified claims or hard shared CI thresholds. Historical baseline observations below are evidence from earlier builds, not a runnable implementation retained in this repository.

The `Tauri release` workflow is the only application release workflow. The previous Gradle packaging and release workflows have been removed by the project owner's request. Read-only user-data import and certificate conversion remain supported; removing the previous source does not mark unverified release checks as passed.

The Windows measurement helper launches a built executable, records its `ui_ready` marker and the idle working set of its process tree, then stops the launched process:

```powershell
./scripts/measure-desktop.ps1 -Executable ./src-tauri/target/release/kafi-kafi.exe -Output .tmp/tauri-desktop-measurement.json
```

The Rust buffer microbenchmark reports retained bytes, evictions, ingestion time and projected IPC size for 1 KiB and 1 MiB records:

```sh
cargo run --release --manifest-path src-tauri/Cargo.toml --example stream-benchmark
```

This microbenchmark does not measure broker throughput, Channel transport, WebView rendering or Kotlin parity. Record those independently before accepting the performance gate.
### Windows development-host sample (2026-10-02)

A local x64 build measured 1,352 ms from the Rust entrypoint to React's first animation frame, a 12.59 MiB executable, a 3.52 MiB NSIS installer and a 4.71 MiB portable ZIP. The earlier Kotlin baseline build produced a 115.55 MiB MSI and a 187.30 MiB unpacked distribution. These package measurements exclude the system WebView2 runtime.

The idle process-tree working set after 15 seconds was approximately 350 MiB for Tauri and 193 MiB for Kotlin. The Tauri result misses the memory target. A separate native smoke-test sample attributed about 32 MiB to the Rust host; the browser, renderer, GPU and utility processes account for most of the remaining working set. Summed working sets include shared pages, but the release gate still requires this full process-tree measurement. This result needs further profiling rather than acceptance as a memory improvement.

The startup sample was taken on a development host with warmed filesystem caches and other build activity; it is not a controlled cold-start comparison. Kotlin UI readiness was not instrumented. These observations do not satisfy the cross-platform or performance acceptance criteria.

A subsequent native WebView2 run of the final Windows executable consumed exactly 100,000 records of 1 KiB from an isolated Kafka topic; the broker end offset was 100,000. The interface retained 10,000 records and reported 90,000 evictions. Only 26 table rows were mounted in a 565 px viewport. The producer reported approximately 8,700 records per second. A frame sample spanning active production and additional idle time observed a maximum inter-frame gap of 60.5 ms; it is not an isolated frame-rate average during production. The process-tree working set was approximately 570 MiB with the buffer full. This demonstrates bounded retention and effective virtualization in that run, but does not establish a long-duration memory plateau or Kotlin throughput parity.

The browser regression separately streams 10,000 mocked Channel rows, switches away from and back to the topic, and resizes the viewport. It checks that the session is retained, the scroller stays inside the window and fewer than 100 rows are mounted. This regression is part of frontend CI.
