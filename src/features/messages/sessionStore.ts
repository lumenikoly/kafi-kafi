import type { Batch, MessageRow } from "../../ipc/types";
export interface SessionSnapshot {
  rows: MessageRow[];
  dropped: number;
  retained: number;
  status: string;
  error: string;
}
export class SessionStore {
  private snapshot: SessionSnapshot = {
    rows: [],
    dropped: 0,
    retained: 0,
    status: "stopped",
    error: "",
  };
  private listeners = new Set<() => void>();
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  getSnapshot = () => this.snapshot;
  apply(batch: Batch) {
    const floor = batch.firstRetainedId ? BigInt(batch.firstRetainedId) : null;
    const previous = batch.reset ? [] : this.snapshot.rows;
    const retained =
      floor === null ? [] : previous.filter((row) => BigInt(row.id) >= floor);
    this.snapshot = {
      rows: [...retained, ...batch.rows],
      dropped: batch.dropped,
      retained: batch.retained,
      status: batch.status,
      error: batch.error?.message ?? "",
    };
    for (const listener of this.listeners) listener();
  }
  clear() {
    this.snapshot = {
      rows: [],
      dropped: 0,
      retained: 0,
      status: "stopped",
      error: "",
    };
    for (const listener of this.listeners) listener();
  }
}
