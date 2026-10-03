import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { columnsFor, DataTable } from "../../components/DataTable";
import { ErrorBanner } from "../../components/Primitives";
import { command, errorMessage, messageChannel } from "../../ipc/client";
import type {
  MessageDetail,
  MessageRow,
  Payload,
  Settings,
} from "../../ipc/types";
import { SessionStore } from "./sessionStore";

const messageColumns = columnsFor<MessageRow>([
  ["partition", "Partition"],
  ["offset", "Offset"],
  ["timestamp", "Timestamp"],
  ["keyPreview", "Key"],
  ["valuePreview", "Value"],
  ["valueType", "Type"],
  ["valueSize", "Bytes"],
]);
export function PayloadView({
  payload,
  raw,
}: {
  payload: Payload | null;
  raw: boolean;
}) {
  if (!payload) return <pre>null (tombstone)</pre>;
  let content = payload.text ?? payload.preview;
  if (
    payload.kind === "json" &&
    !raw &&
    payload.text &&
    !payload.truncated &&
    payload.size <= 65536
  ) {
    try {
      content = JSON.stringify(JSON.parse(payload.text), null, 2);
    } catch {
      /* Display original bytes if parsing fails. */
    }
  }
  return (
    <>
      <p className="muted">
        {payload.kind} · {payload.size.toLocaleString()} bytes
        {payload.truncated ? " · preview" : ""}
      </p>
      <pre>{content}</pre>
    </>
  );
}
export function Messages({
  topic,
  settings,
}: {
  topic: string;
  settings: Settings;
}) {
  const [store] = useState(() => new SessionStore());
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot);
  const session = useRef<string | null>(null);
  const mounted = useRef(true);
  const operation = useRef(0);
  const [position, setPosition] = useState(
    settings.defaultConsumerStartPosition,
  );
  const [partition, setPartition] = useState("");
  const [offset, setOffset] = useState("0");
  const [timestamp, setTimestamp] = useState("");
  const [keyFilter, setKeyFilter] = useState("");
  const [valueFilter, setValueFilter] = useState("");
  const [filterPartition, setFilterPartition] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [detail, setDetail] = useState<MessageDetail | null>(null);
  const [raw, setRaw] = useState(false);
  const [running, setRunning] = useState(false);
  const selected = useRef<string | null>(null);
  const selection = useRef(0);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      operation.current++;
      if (session.current)
        void command("stop_consumer", { sessionId: session.current }).catch(
          () => {},
        );
      session.current = null;
    };
  }, []);
  useEffect(() => {
    if (!session.current) return;
    const id = session.current;
    const timer = setTimeout(() => {
      void command("set_consumer_filter", {
        sessionId: id,
        filter: {
          key: keyFilter,
          value: valueFilter,
          partition: filterPartition === "" ? null : Number(filterPartition),
        },
      }).catch((e) => setError(errorMessage(e)));
    }, 180);
    return () => clearTimeout(timer);
  }, [keyFilter, valueFilter, filterPartition]);
  const stop = async () => {
    operation.current++;
    const id = session.current;
    session.current = null;
    setRunning(false);
    setDetail(null);
    selected.current = null;
    selection.current++;
    store.clear();
    if (id) await command("stop_consumer", { sessionId: id });
  };
  const start = async () => {
    setError("");
    setBusy(true);
    const ticket = ++operation.current;
    try {
      const channel = messageChannel((batch) => {
        if (!mounted.current || ticket !== operation.current) {
          void command("stop_consumer", { sessionId: batch.sessionId }).catch(
            () => {},
          );
          return;
        }
        store.apply(batch);
        void command("acknowledge_batch", {
          sessionId: batch.sessionId,
          sequence: batch.sequence,
        }).catch(() => {});
        if (batch.status === "failed") {
          operation.current++;
          session.current = null;
          setRunning(false);
          setDetail(null);
        }
        if (
          selected.current &&
          batch.firstRetainedId &&
          BigInt(selected.current) < BigInt(batch.firstRetainedId)
        ) {
          selected.current = null;
          setDetail(null);
        }
      });
      const id = await command("start_consumer", {
        request: {
          topic,
          partition: partition === "" ? null : Number(partition),
          position,
          offset: position === "offset" ? Number(offset) : null,
          timestamp:
            position === "timestamp" ? new Date(timestamp).getTime() : null,
        },
        channel,
      });
      if (!mounted.current || ticket !== operation.current) {
        await command("stop_consumer", { sessionId: id });
        return;
      }
      session.current = id;
      setRunning(true);
      await command("set_consumer_filter", {
        sessionId: id,
        filter: {
          key: keyFilter,
          value: valueFilter,
          partition: filterPartition === "" ? null : Number(filterPartition),
        },
      });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const select = async (row: MessageRow, full = false) => {
    const id = session.current;
    if (!id) return;
    const ticket = ++selection.current;
    selected.current = row.id;
    setDetail(null);
    try {
      const result = await command("get_message_detail", {
        sessionId: id,
        messageId: row.id,
        full,
      });
      if (ticket === selection.current && mounted.current) setDetail(result);
    } catch (e) {
      if (ticket === selection.current) setError(errorMessage(e));
    }
  };
  const control = async () => {
    if (!session.current) return;
    try {
      await command(
        snapshot.status === "paused" ? "resume_consumer" : "pause_consumer",
        { sessionId: session.current },
      );
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  return (
    <section className="messages">
      <div className="toolbar">
        <select
          aria-label="Start position"
          disabled={running}
          value={position}
          onChange={(e) => setPosition(e.target.value)}
        >
          {["latest", "earliest", "offset", "timestamp"].map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <input
          aria-label="Read partition"
          type="number"
          min="0"
          placeholder="All partitions"
          disabled={running}
          value={partition}
          onChange={(e) => setPartition(e.target.value)}
        />
        {position === "offset" && (
          <input
            aria-label="Offset"
            type="number"
            min="0"
            value={offset}
            disabled={running}
            onChange={(e) => setOffset(e.target.value)}
          />
        )}{" "}
        {position === "timestamp" && (
          <input
            aria-label="Timestamp"
            type="datetime-local"
            value={timestamp}
            disabled={running}
            onChange={(e) => setTimestamp(e.target.value)}
          />
        )}
        <button
          type="button"
          className="primary"
          disabled={running || busy}
          onClick={() => void start()}
        >
          Start
        </button>
        <button
          type="button"
          disabled={!running}
          onClick={() => void control()}
        >
          {snapshot.status === "paused" ? "Resume" : "Pause"}
        </button>
        <button
          type="button"
          disabled={!running && !busy}
          onClick={() => void stop().catch((e) => setError(errorMessage(e)))}
        >
          Stop
        </button>
        <output>
          {snapshot.status} · {snapshot.retained} retained · {snapshot.dropped}{" "}
          evicted
        </output>
      </div>
      <div className="toolbar">
        <input
          aria-label="Filter key"
          placeholder="Filter key"
          value={keyFilter}
          onChange={(e) => setKeyFilter(e.target.value)}
        />
        <input
          aria-label="Filter value"
          placeholder="Filter value"
          value={valueFilter}
          onChange={(e) => setValueFilter(e.target.value)}
        />
        <input
          aria-label="Filter partition"
          type="number"
          min="0"
          placeholder="Partition filter"
          value={filterPartition}
          onChange={(e) => setFilterPartition(e.target.value)}
        />
      </div>
      <ErrorBanner message={error || snapshot.error} />
      <div className="split fill">
        <DataTable
          data={snapshot.rows}
          columns={messageColumns}
          label="Messages"
          onSelect={(row) => void select(row)}
        />
        {detail && (
          <aside className="inspector">
            <h3>
              Record · {detail.partition}:{detail.offset}
            </h3>
            <p>
              {detail.topic} ·{" "}
              {detail.timestamp === null
                ? "No timestamp"
                : new Date(detail.timestamp).toISOString()}
            </p>
            <div className="toolbar">
              <button type="button" onClick={() => setRaw(!raw)}>
                {raw ? "Formatted JSON" : "Raw"}
              </button>
              {(detail.key?.truncated || detail.value?.truncated) && (
                <button
                  type="button"
                  onClick={() =>
                    void select({ id: detail.id } as MessageRow, true)
                  }
                >
                  Load full value
                </button>
              )}
            </div>
            <h4>Key</h4>
            <PayloadView payload={detail.key} raw={raw} />
            <h4>Value</h4>
            <PayloadView payload={detail.value} raw={raw} />
            <h4>Headers</h4>
            {detail.headers.map((h, i) => (
              <pre key={`${h.key}:${i}`}>
                {h.key}: {h.value ?? "null"}
              </pre>
            ))}
            <div className="toolbar">
              {["key", "value"].map((field) => (
                <button
                  type="button"
                  key={field}
                  disabled={!detail[field as "key" | "value"]}
                  onClick={() => {
                    if (session.current)
                      void command("export_message", {
                        sessionId: session.current,
                        messageId: detail.id,
                        field,
                      }).catch((e) => setError(errorMessage(e)));
                  }}
                >
                  Export {field} bytes
                </button>
              ))}
            </div>
          </aside>
        )}
      </div>
    </section>
  );
}
