import { describe, expect, it, vi } from "vitest";
import type { Batch, MessageRow } from "../../ipc/types";
import { SessionStore } from "./sessionStore";

const row = (id: string): MessageRow => ({
  id,
  partition: 0,
  offset: Number(id),
  timestamp: null,
  keyPreview: "",
  valuePreview: "",
  keySize: 0,
  valueSize: 0,
  valueType: "text",
  headersCount: 0,
});
const batch = (overrides: Partial<Batch> = {}): Batch => ({
  sessionId: "s1",
  sequence: 1,
  rows: [],
  firstRetainedId: null,
  dropped: 0,
  retained: 0,
  reset: false,
  status: "running",
  error: null,
  ...overrides,
});

describe("SessionStore", () => {
  it("evicts rows below the bounded buffer floor and reports counts and failures", () => {
    const store = new SessionStore();
    store.apply(
      batch({
        rows: [row("1"), row("2"), row("3")],
        firstRetainedId: "1",
        retained: 3,
      }),
    );
    store.apply(
      batch({
        sequence: 2,
        rows: [row("4")],
        firstRetainedId: "3",
        retained: 2,
        dropped: 2,
        status: "failed",
        error: {
          code: "consumer_failed",
          message: "Broker disconnected",
          details: null,
          retryable: true,
        },
      }),
    );

    expect(store.getSnapshot()).toMatchObject({
      rows: [row("3"), row("4")],
      retained: 2,
      dropped: 2,
      status: "failed",
      error: "Broker disconnected",
    });
  });

  it("resets old rows when a batch starts a new stream and clears all state on stop", () => {
    const store = new SessionStore();
    store.apply(batch({ rows: [row("8")], firstRetainedId: "8", retained: 1 }));
    store.apply(
      batch({
        reset: true,
        rows: [row("1")],
        firstRetainedId: "1",
        retained: 1,
      }),
    );
    expect(store.getSnapshot().rows).toEqual([row("1")]);

    const listener = vi.fn();
    store.subscribe(listener);
    store.clear();
    expect(store.getSnapshot()).toEqual({
      rows: [],
      dropped: 0,
      retained: 0,
      status: "stopped",
      error: "",
    });
    expect(listener).toHaveBeenCalledOnce();
  });
});
