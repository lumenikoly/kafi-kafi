---
workflow: product-launch-video
flow: automation
storyboard: no
message: "Show actual Kafi Kafi screens from native Kafka checks."
destination: GitHub README
aspect: "8:5"
language: en
length: 12s
---

# Kafi Kafi README demo

Show the existing application interface as captured. Use three unaltered screenshots, four seconds each, with hard cuts and no audio. Do not reconstruct the UI or synthesize records. The GIF is an inline README preview; the MP4 preserves the 1600 × 1000 canvas.

## Capture provenance

- `assets/connections.png`: copied unchanged from `.tmp/native-release-connection-tested.png`; native connection-test screen, local broker `127.0.0.1:19092`.
- `assets/message-inspector.png`: copied unchanged from `.tmp/native-release-message-inspector.png`; native record inspector, topic `codex_smoke_20261002_frontend`, value `native smoke payload`.
- `assets/message-stream.png`: copied unchanged from `.tmp/native-stream-final.png`; native bounded message table, topic `codex_final_stream_20261002_perf`. The existing `.tmp/native-stream-performance.json` records this run and its retention counters.

The captures already existed in the workspace. Native Kafka smoke and stream verification are recorded in [migration acceptance](../../docs/work/TASK-MIGRATION-001.md). These are separate test states, not a continuous recording or a newly executed Kafka session. The screenshots show the real React UI implemented in `src/app/App.tsx`, `src/features/connections/Connections.tsx`, `src/features/topics/Topics.tsx`, `src/features/messages/Messages.tsx`, and `src/styles/global.css`.

## Reproduce the montage

From this directory with Node 22+ and FFmpeg available:

```sh
npm run check
npm run render -- --quality delivery --fps 15 --output ../../docs/assets/kafi-kafi-demo.mp4
ffmpeg -y -i ../../docs/assets/kafi-kafi-demo.mp4 -filter_complex "fps=5,scale=1200:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 ../../docs/assets/kafi-kafi-demo.gif
```

Replace the screenshot assets with fresh captures from the application when its UI changes. Preserve the original screenshots and update the provenance above; do not edit labels or data inside the captures.
