import { useEffect, useState } from "react";
import { Confirmation, ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import type {
  Settings as AppSettings,
  ContainerStatus,
  LegacyStatus,
} from "../../ipc/types";
export function Settings({
  settings,
  onSave,
}: {
  settings: AppSettings;
  onSave: (settings: AppSettings) => void;
}) {
  const [draft, setDraft] = useState(settings);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [legacy, setLegacy] = useState<LegacyStatus | null>(null);
  const [fingerprint, setFingerprint] = useState("");
  const [importing, setImporting] = useState(false);
  const [container, setContainer] = useState<ContainerStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const changeTheme = async (theme: string) => {
    const next = { ...settings, layout: { ...settings.layout, theme } };
    setBusy(true);
    setError("");
    try {
      await command("save_settings", { settings: next });
      setDraft((previous) => ({
        ...previous,
        layout: { ...previous.layout, theme },
      }));
      onSave(next);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  useEffect(() => {
    void command("get_legacy_status")
      .then(setLegacy)
      .catch((e) => setError(errorMessage(e)));
  }, []);
  const local = async (
    action: "local_kafka_status" | "start_local_kafka" | "stop_local_kafka",
  ) => {
    setBusy(true);
    setError("");
    try {
      setContainer(await command(action));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="editor settings-editor">
      <h2>Settings</h2>
      <ErrorBanner message={error} />
      {notice && <output>{notice}</output>}
      <Field label="Theme">
        <select
          aria-label="Theme"
          value={settings.layout.theme === "light" ? "light" : "dark"}
          disabled={busy}
          onChange={(e) => void changeTheme(e.target.value)}
        >
          <option value="dark">Dark</option>
          <option value="light">Light</option>
        </select>
      </Field>
      <h3>Messages</h3>
      <div className="form-grid">
        <Field label="Message buffer records">
          <input
            aria-label="Message buffer records"
            type="number"
            min="100"
            max="100000"
            value={draft.messageBufferLimit}
            onChange={(e) =>
              setDraft({ ...draft, messageBufferLimit: Number(e.target.value) })
            }
          />
        </Field>
        <Field label="Message buffer MiB">
          <input
            aria-label="Message buffer MiB"
            type="number"
            min="1"
            max="512"
            value={draft.messageBufferBytes / (1024 * 1024)}
            onChange={(e) =>
              setDraft({
                ...draft,
                messageBufferBytes: Number(e.target.value) * 1024 * 1024,
              })
            }
          />
        </Field>
      </div>
      <Field label="Default start position">
        <select
          aria-label="Default start position"
          value={draft.defaultConsumerStartPosition}
          onChange={(e) =>
            setDraft({ ...draft, defaultConsumerStartPosition: e.target.value })
          }
        >
          <option value="latest">Latest</option>
          <option value="earliest">Earliest</option>
        </select>
      </Field>
      <button
        type="button"
        className="primary"
        disabled={busy}
        onClick={() => {
          setBusy(true);
          void command("save_settings", { settings: draft })
            .then(() => {
              onSave(draft);
              setNotice(
                "Settings saved; buffer changes apply to new sessions.",
              );
            })
            .catch((e) => setError(errorMessage(e)))
            .finally(() => setBusy(false));
        }}
      >
        Save settings
      </button>
      <h3>Local Kafka</h3>
      <p className="muted">localhost:9092 · Podman or Docker</p>
      {container && (
        <output>
          {container.runtime ?? "No runtime"} · {container.state} ·{" "}
          {container.image}
        </output>
      )}
      <div className="toolbar">
        <button
          type="button"
          disabled={busy}
          onClick={() => void local("local_kafka_status")}
        >
          Check runtime
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void local("start_local_kafka")}
        >
          Start local Kafka
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void local("stop_local_kafka")}
        >
          Stop local Kafka
        </button>
      </div>
      {legacy?.available && !legacy.imported && (
        <>
          <h3>Import existing profiles</h3>
          <p>{legacy.root}</p>
          <p className="muted">
            The old files are retained. JKS certificates require conversion to
            PEM / PKCS#12.
          </p>
          <Field label="Original machine fingerprint">
            <input
              aria-label="Original machine fingerprint"
              placeholder="user.name|os.name|user.home"
              value={fingerprint}
              onChange={(e) => setFingerprint(e.target.value)}
            />
          </Field>
          <button type="button" onClick={() => setImporting(true)}>
            Import profiles
          </button>
        </>
      )}
      {importing && (
        <Confirmation
          title="Import existing profiles"
          confirmLabel="Import profiles"
          destructive={false}
          busy={busy}
          onCancel={() => setImporting(false)}
          onConfirm={() => {
            setBusy(true);
            void command("import_legacy", { fingerprint })
              .then((result) => {
                setNotice(
                  `Imported ${result.profiles} profiles. ${result.warnings.join(" ")}`,
                );
                setImporting(false);
                setFingerprint("");
                void command("get_legacy_status").then(setLegacy);
                void command("get_settings").then((s) => {
                  setDraft(s);
                  onSave(s);
                });
              })
              .catch((e) => setError(errorMessage(e)))
              .finally(() => setBusy(false));
          }}
        >
          <p>
            Import profiles, settings, message templates and passwords from{" "}
            {legacy?.root}?
          </p>
          <ErrorBanner message={error} />
        </Confirmation>
      )}
    </section>
  );
}
