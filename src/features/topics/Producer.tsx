import { useState } from "react";
import { ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import { parseProperties } from "../connections/validation";
export function Producer({ topic }: { topic: string }) {
  const [key, setKey] = useState("");
  const [value, setValue] = useState("");
  const [partition, setPartition] = useState("");
  const [headers, setHeaders] = useState("");
  const [tombstone, setTombstone] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const send = async () => {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const result = await command("produce_message", {
        request: {
          topic,
          key: key || null,
          value: tombstone ? null : value,
          partition: partition === "" ? null : Number(partition),
          headers: Object.entries(parseProperties(headers)).map(
            ([key, value]) => ({ key, value }),
          ),
        },
      });
      setNotice(
        `Delivered to partition ${result.partition}, offset ${result.offset}, timestamp ${result.timestamp ?? "unavailable"}.`,
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="editor">
      <h3>Produce record</h3>
      <ErrorBanner message={error} />
      {notice && <output>{notice}</output>}
      <Field label="Key (optional)">
        <input
          aria-label="Key (optional)"
          value={key}
          onChange={(e) => setKey(e.target.value)}
        />
      </Field>
      <Field label="Partition (optional)">
        <input
          aria-label="Partition (optional)"
          type="number"
          min="0"
          value={partition}
          onChange={(e) => setPartition(e.target.value)}
        />
      </Field>
      <Field label="Value">
        <textarea
          aria-label="Value"
          className="payload-editor"
          disabled={tombstone}
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </Field>
      <label>
        <input
          type="checkbox"
          checked={tombstone}
          onChange={(e) => setTombstone(e.target.checked)}
        />
        Null value (tombstone)
      </label>
      <Field label="Headers (name=value)">
        <textarea
          aria-label="Headers (name=value)"
          value={headers}
          onChange={(e) => setHeaders(e.target.value)}
        />
      </Field>
      <button
        type="button"
        className="primary"
        disabled={busy}
        onClick={() => void send()}
      >
        Send record
      </button>
    </section>
  );
}
