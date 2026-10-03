---
workflow: product-launch-video
flow: automation
storyboard: no
message: "Show the current Kafi Kafi 1.1.0 native interface with real Kafka records."
destination: GitHub README
aspect: "8:5"
language: en
length: 16s
---

# Kafi Kafi README demo

Show four unaltered screenshots of the current native application, four seconds each, with hard cuts and no audio. The GIF is an inline README preview; the MP4 preserves the 1600 × 1000 canvas. Do not reconstruct the interface or edit labels or data inside the captures.

## Capture provenance

All four frames were captured on 2026-10-03 through the release application's WebView2 CDP endpoint, with a 1600 × 1000 viewport. The executable was built from the current Rust/Tauri and React source with application version 1.1.0. Its SHA-256 was `b908acd7400989168fdce7250813e3ddebf4e0ad439fca4eeeb4a7b4be16a624`.

- `assets/connections.png`: copied unchanged from `.tmp/release-1.1.0-captures/connections.png`; a successful native connection test against the local broker `127.0.0.1:19092`, using the temporary **Local Kafka** profile.
- `assets/message-inspector.png`: copied unchanged from `.tmp/release-1.1.0-captures/message-inspector.png`; record `0:0` in the temporary topic `orders.readme-v1-1-0`, with key `order-1001`, a formatted JSON order value and the `source=readme-demo` header.
- `assets/producer.png`: copied unchanged from `.tmp/release-1.1.0-captures/producer.png`; the Produce form after sending key `order-1025`, a JSON order value and a header to partition 0, offset 24. The template selector is empty because this temporary topic has no imported templates.
- `assets/message-stream.png`: copied unchanged from `.tmp/release-1.1.0-captures/message-stream.png`; the same topic in the light theme, showing 25 retained records and zero evictions.

The first 24 demonstration records were produced through the native `produce_message` IPC command into the real local Kafka broker; record 25 was sent with the **Send record** button. Records were consumed and inspected through the native Kafka runtime. Neither the interface nor the Kafka responses were mocked. These are separate captured states assembled as a montage, not a continuous screen recording or a performance benchmark. The temporary profile and topic were removed and the previous settings restored after capture. The earlier native screenshots under `.tmp/native-release-*.png` and `.tmp/native-stream-final.png` were left unchanged.

The local `.tmp/release-1.1.0-captures/provenance.json` records the executable, broker, topic, record count and screenshot paths. The source implementation is in `src/` and `src-tauri/`; [1.1.0 release notes](../../docs/releases/1.1.0.md) distinguish implemented changes from remaining publication gates.

## Reproduce the montage

From this directory with Node 22+ and FFmpeg available:

```sh
npm run check
npm run render -- --quality delivery --fps 15 --workers 2 --output ../../docs/assets/kafi-kafi-demo.mp4
ffmpeg -y -i ../../docs/assets/kafi-kafi-demo.mp4 -filter_complex "fps=5,scale=1200:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 ../../docs/assets/kafi-kafi-demo.gif
```

The project pins HyperFrames 0.8.115. Replace the screenshot assets with fresh native captures when the interface changes, retain the original captures and update their provenance. Capture only demonstration profiles and records, without passwords or user data.
