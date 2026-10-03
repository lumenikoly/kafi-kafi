import { useEffect, useState } from "react";
import { ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import { parseProperties } from "../connections/validation";
import { type ProducerTemplate, readTemplate } from "./producerTemplates";
export function Producer({ topic }: { topic: string }) {
  const [templates, setTemplates] = useState<ProducerTemplate[]>([]);
  const [templateId, setTemplateId] = useState("");
  const [key, setKey] = useState("");
  const [nullKey, setNullKey] = useState(true);
  const [value, setValue] = useState("");
  const [partition, setPartition] = useState("");
  const [headers, setHeaders] = useState("");
  const [templateHeaders, setTemplateHeaders] = useState<Record<
    string,
    string
  > | null>(null);
  const [tombstone, setTombstone] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let mounted = true;
    void command("get_producer_templates")
      .then((saved) => {
        if (!mounted) return;
        const parsed = saved.map(readTemplate);
        setTemplates(
          parsed.filter(
            (t): t is ProducerTemplate => t !== null && t.topic === topic,
          ),
        );
        if (parsed.some((t) => t === null))
          setError(
            "Some saved templates have an invalid format and could not be loaded.",
          );
      })
      .catch((e) => {
        if (mounted) setError(errorMessage(e));
      });
    return () => {
      mounted = false;
    };
  }, [topic]);
  const applyTemplate = (id: string) => {
    setTemplateId(id);
    const template = templates.find((t) => t.id === id);
    if (!template) return;
    setKey(template.key ?? "");
    setNullKey(template.key === null);
    setValue(template.value ?? "");
    setPartition(template.partition === null ? "" : String(template.partition));
    setHeaders(
      Object.entries(template.headers)
        .map(([k, v]) => `${k}=${v}`)
        .join("\n"),
    );
    setTemplateHeaders(template.headers);
    setTombstone(template.value === null);
    setError("");
    setNotice("");
  };
  const send = async () => {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const result = await command("produce_message", {
        request: {
          topic,
          key: nullKey ? null : key,
          value: tombstone ? null : value,
          partition: partition === "" ? null : Number(partition),
          headers: Object.entries(
            templateHeaders ?? parseProperties(headers),
          ).map(([key, value]) => ({ key, value })),
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
    <section className="editor producer-editor" aria-label="Produce record">
      <ErrorBanner message={error} />
      {notice && <output>{notice}</output>}
      <Field label="Saved template">
        <select
          aria-label="Saved template"
          value={templateId}
          disabled={busy || templates.length === 0}
          onChange={(e) => applyTemplate(e.target.value)}
        >
          <option value="">
            {templates.length
              ? "Choose template"
              : "No templates for this topic"}
          </option>
          {templates.map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>
      </Field>
      <div className="form-grid">
        <Field label="Key (optional)">
          <input
            aria-label="Key (optional)"
            value={key}
            onChange={(e) => {
              setKey(e.target.value);
              setNullKey(e.target.value === "");
            }}
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
      </div>
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
          onChange={(e) => {
            setHeaders(e.target.value);
            setTemplateHeaders(null);
          }}
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
