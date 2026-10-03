import { createRoot } from "react-dom/client";
import type { Batch, MessageRow, Profile, Settings } from "../../src/ipc/types";
import "../../src/styles/global.css";

const profile: Profile = {
  id: "browser-profile",
  name: "Browser regression profile",
  bootstrapServers: ["127.0.0.1:19092"],
  clientId: null,
  securityProtocol: "PLAINTEXT",
  sasl: null,
  tls: null,
  extraProperties: {},
};

let settings: Settings = {
  messageBufferLimit: 10_000,
  messageBufferBytes: 64 * 1024 * 1024,
  defaultConsumerStartPosition: "earliest",
  layout: {},
};

const topic = "browser_viewport_regression";
let channel: { onmessage: (batch: Batch) => void } | null = null;
let sequence = 0;
let sent = 0;

const callbacks = new Map<number, (value: unknown) => void>();
let callbackId = 0;
const internals = {
  transformCallback(callback: (value: unknown) => void) {
    const id = ++callbackId;
    callbacks.set(id, callback);
    return id;
  },
  unregisterCallback(id: number) {
    callbacks.delete(id);
  },
  async invoke(name: string, args: Record<string, unknown> = {}) {
    switch (name) {
      case "get_profiles":
        return [profile];
      case "get_settings":
        return settings;
      case "save_settings":
        settings = args.settings as Settings;
        return null;
      case "get_producer_templates":
        return [
          {
            id: "imported-template",
            name: "Order event",
            topic,
            partition: 0,
            key: "order-7",
            value: '{"ok":true}',
            headers: { source: "legacy" },
          },
          {
            id: "tombstone-template",
            name: "Delete order",
            topic,
            partition: null,
            key: "order-7",
            value: null,
            headers: {},
          },
          {
            id: "other-topic-template",
            name: "Other topic",
            topic: "other",
            key: null,
            value: "other",
            partition: null,
            headers: {},
          },
        ];
      case "produce_message":
        return { partition: 0, offset: sent, timestamp: 1700000000000 };
      case "get_legacy_status":
        return { available: false, root: "", imported: false };
      case "connect":
        return {
          clusterId: "browser-mock",
          controller: 1,
          profileId: profile.id,
          generation: "browser-generation",
          brokers: [{ id: 1, host: "127.0.0.1", port: 19092, rack: null }],
        };
      case "list_topics":
        return [{ name: topic, partitions: 1, internal: false }];
      case "describe_topic":
        return {
          name: topic,
          partitions: [
            {
              id: 0,
              leader: 1,
              replicas: [1],
              isr: [1],
              earliestOffset: 0,
              latestOffset: sent,
            },
          ],
          config: [],
        };
      case "start_consumer":
        channel = args.channel as typeof channel;
        sent = 0;
        sequence = 0;
        return "browser-session";
      case "acknowledge_batch":
      case "stop_consumer":
      case "set_consumer_filter":
      case "disconnect":
        return null;
      default:
        throw new Error(`Unexpected mocked IPC command: ${name}`);
    }
  },
};

declare global {
  interface Window {
    __TAURI_INTERNALS__: typeof internals;
    __browserTableTest: {
      startFrameSample: () => void;
      stopFrameSample: () => { frames: number; maxGapMs: number };
      streamRows: (
        count: number,
        batchSize: number,
        intervalMs: number,
      ) => Promise<void>;
    };
  }
}

window.__TAURI_INTERNALS__ = internals;

let sampling = false;
let lastFrame = 0;
const frameGaps: number[] = [];
function sampleFrame(now: number) {
  if (!sampling) return;
  if (lastFrame) frameGaps.push(now - lastFrame);
  lastFrame = now;
  requestAnimationFrame(sampleFrame);
}

window.__browserTableTest = {
  startFrameSample() {
    frameGaps.length = 0;
    lastFrame = 0;
    sampling = true;
    requestAnimationFrame(sampleFrame);
  },
  stopFrameSample() {
    sampling = false;
    return {
      frames: frameGaps.length,
      maxGapMs: Number(Math.max(0, ...frameGaps).toFixed(1)),
    };
  },
  async streamRows(count, batchSize, intervalMs) {
    if (!channel) throw new Error("Consumer channel has not started");
    for (let start = 0; start < count; start += batchSize) {
      const end = Math.min(start + batchSize, count);
      const rows: MessageRow[] = Array.from(
        { length: end - start },
        (_, index) => {
          const offset = start + index;
          return {
            id: String(offset),
            partition: 0,
            offset,
            timestamp: 1_700_000_000_000 + offset,
            keyPreview: "",
            valuePreview: "x".repeat(1024),
            keySize: 0,
            valueSize: 1024,
            valueType: "text",
            headersCount: 0,
          };
        },
      );
      sent = end;
      channel.onmessage({
        sessionId: "browser-session",
        sequence: sequence++,
        rows,
        firstRetainedId: String(Math.max(0, sent - 10_000)),
        dropped: Math.max(0, sent - 10_000),
        retained: Math.min(sent, 10_000),
        reset: start === 0,
        status: "running",
        error: null,
      });
      if (intervalMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, intervalMs));
      }
    }
  },
};

const { App } = await import("../../src/app/App");
const root = document.getElementById("root");
if (!root) throw new Error("The browser harness root is missing");
createRoot(root).render(<App />);
