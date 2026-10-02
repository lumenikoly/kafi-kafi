<!-- toudocu
architectureQuestion: How does message inspection keep memory and interface work bounded?
-->

# Bounded message inspection

Rust retains complete Kafka records in a per-session deque. React retains only row projections and the currently requested detail. Acknowledged Tauri Channel batches bound outstanding delivery independently of the Kafka record buffer.

## Scope

This document explains ownership, pressure and cancellation. The [message guide](../guides/messages.md) describes the user controls.

The application creates one inspection consumer per started topic view. Consumers use direct partition assignment, unique technical group IDs, disabled automatic commits and disabled automatic offset storage. Ordinary inspection does not join or advance a user's consumer group.

Each session limits both record count and retained bytes. The defaults are 10,000 records and 64 MiB; Settings affects newly created sessions. When either limit is exceeded, the oldest records leave the buffer and the eviction count increases. A record larger than the entire byte budget cannot be retained.

The stream projects at most 200 rows per batch on a 33 ms interval. These initial values live in `kafka/consumer.rs` and remain subject to benchmark tuning. At most two unacknowledged batches are sent. While delivery falls behind, Rust continues consuming into its bounded buffer; records evicted before delivery are counted, rather than stored in an unbounded IPC backlog. Each batch carries the first retained record ID so the interface can discard stale rows. Filters run against complete bytes in Rust and reset the projected snapshot without restarting Kafka.

Small records expose bounded previews. Detail requests avoid automatically transferring values larger than 64 KiB; the user explicitly requests their full content or exports bytes through Rust. JSON formatting is limited to small values. Complete payloads are never written to browser storage or application logs.

Closing a topic tab, stopping the session, or successfully switching profiles cancels the owning task. Stop waits for task termination and removes the session. Native poll failures end the session and report a safe error; the user can start it again. A failed connection attempt preserves the active connection and its sessions.

TanStack Virtual limits table rows in the DOM to the viewport and eight nearby rows. The stream store is local to a topic view, rather than a global payload history.
