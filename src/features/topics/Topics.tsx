import { useCallback, useEffect, useState } from "react";
import { columnsFor, DataTable } from "../../components/DataTable";
import { Icon } from "../../components/Icon";
import { Confirmation, ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import type {
  ConfigEntry,
  Partition,
  Settings,
  Topic,
  TopicDetail,
} from "../../ipc/types";
import { parseProperties } from "../connections/validation";
import { Messages } from "../messages/Messages";
import { Producer } from "./Producer";

const topicColumns = columnsFor<Topic>([
  ["name", "Topic"],
  ["partitions", "Partitions"],
  ["internal", "Internal"],
]);
const partitionColumns = columnsFor<Partition>([
  ["id", "Partition"],
  ["leader", "Leader"],
  ["replicas", "Replicas"],
  ["isr", "ISR"],
  ["earliestOffset", "Earliest"],
  ["latestOffset", "Latest"],
]);
const configColumns = columnsFor<ConfigEntry>([
  ["name", "Parameter"],
  ["value", "Value"],
  ["isDefault", "Default"],
  ["readOnly", "Read only"],
  ["sensitive", "Sensitive"],
]);
export function filterTopics(
  topics: Topic[],
  query: string,
  internal: boolean,
): Topic[] {
  return topics.filter(
    (t) =>
      (internal || !t.internal) &&
      t.name.toLowerCase().includes(query.toLowerCase()),
  );
}
export function Topics({ onOpen }: { onOpen: (topic: string) => void }) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [query, setQuery] = useState("");
  const [internal, setInternal] = useState(false);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState("");
  const [partitions, setPartitions] = useState(1);
  const [replication, setReplication] = useState(1);
  const [config, setConfig] = useState("");
  const refresh = useCallback(() => {
    setBusy(true);
    return command("list_topics")
      .then(setTopics)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setBusy(false));
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const create = async () => {
    setError("");
    if (
      !/^[a-zA-Z0-9._-]{1,249}$/.test(name) ||
      name === "." ||
      name === ".." ||
      partitions < 1 ||
      replication < 1
    ) {
      setError(
        "Enter a valid topic name and positive partition / replication counts.",
      );
      return;
    }
    setBusy(true);
    try {
      await command("create_topic", {
        request: {
          name,
          partitions,
          replicationFactor: replication,
          config: parseProperties(config),
        },
      });
      setCreating(false);
      await refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="page">
      <div className="toolbar">
        <h2>Topics</h2>
        <input
          aria-label="Search topics"
          placeholder="Search topics"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <label>
          <input
            type="checkbox"
            checked={internal}
            onChange={(e) => setInternal(e.target.checked)}
          />
          Internal topics
        </label>
        <button type="button" disabled={busy} onClick={() => void refresh()}>
          Refresh
        </button>
        <button type="button" onClick={() => setCreating(true)}>
          Create topic
        </button>
      </div>
      <ErrorBanner message={error} />
      <DataTable
        data={filterTopics(topics, query, internal)}
        columns={topicColumns}
        label="Topics"
        onSelect={(t) => onOpen(t.name)}
      />
      {creating && (
        <div className="modal-backdrop">
          <section role="dialog" aria-label="Create topic" className="modal">
            <h2>Create topic</h2>
            <Field label="Topic name">
              <input
                aria-label="Topic name"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Field label="Partitions">
              <input
                aria-label="Partitions"
                type="number"
                min="1"
                value={partitions}
                onChange={(e) => setPartitions(Number(e.target.value))}
              />
            </Field>
            <Field label="Replication factor">
              <input
                aria-label="Replication factor"
                type="number"
                min="1"
                value={replication}
                onChange={(e) => setReplication(Number(e.target.value))}
              />
            </Field>
            <Field label="Configuration">
              <textarea
                aria-label="Configuration"
                value={config}
                onChange={(e) => setConfig(e.target.value)}
              />
            </Field>
            <ErrorBanner message={error} />
            <div className="toolbar">
              <button type="button" onClick={() => setCreating(false)}>
                Cancel
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => void create()}
              >
                Create
              </button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
export function TopicWorkspace({
  topic,
  settings,
  onDeleted,
}: {
  topic: string;
  settings: Settings;
  onDeleted: () => void;
}) {
  const [section, setSection] = useState("Messages");
  const [detail, setDetail] = useState<TopicDetail | null>(null);
  const [selected, setSelected] = useState<ConfigEntry | null>(null);
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [busy, setBusy] = useState(false);
  const refresh = useCallback(
    () =>
      command("describe_topic", { topic })
        .then(setDetail)
        .catch((e) => setError(errorMessage(e))),
    [topic],
  );
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return (
    <section className="page">
      <div className="toolbar page-heading">
        <h2>{topic}</h2>
        <button type="button" onClick={() => void refresh()}>
          <Icon name="Refresh" />
          Refresh
        </button>
        <button
          className="danger"
          type="button"
          onClick={() => setDeleting(true)}
        >
          <Icon name="Delete" />
          Delete topic
        </button>
      </div>
      <fieldset className="section-tabs" aria-label="Topic sections">
        {["Messages", "Partitions", "Configuration", "Produce"].map((s) => (
          <button
            className={section === s ? "active" : ""}
            aria-pressed={section === s}
            type="button"
            key={s}
            onClick={() => setSection(s)}
          >
            {s}
          </button>
        ))}
      </fieldset>
      <ErrorBanner message={error} />
      <div className="fill" hidden={section !== "Messages"}>
        <Messages topic={topic} settings={settings} />
      </div>
      {section === "Partitions" && detail && (
        <DataTable
          data={detail.partitions}
          columns={partitionColumns}
          label="Partitions"
        />
      )}
      {section === "Configuration" && detail && (
        <div className="split fill">
          <DataTable
            data={detail.config}
            columns={configColumns}
            label="Topic configuration"
            onSelect={(entry) => {
              setSelected(entry);
              setValue(entry.value ?? "");
            }}
          />
          {selected && (
            <aside className="inspector">
              <h3>{selected.name}</h3>
              <textarea
                aria-label="Configuration value"
                value={value}
                disabled={selected.readOnly || selected.sensitive}
                onChange={(e) => setValue(e.target.value)}
              />
              <button
                type="button"
                disabled={selected.readOnly || selected.sensitive || busy}
                onClick={() => {
                  setBusy(true);
                  void command("update_topic_config", {
                    topic,
                    updates: { [selected.name]: value },
                  })
                    .then(refresh)
                    .catch((e) => setError(errorMessage(e)))
                    .finally(() => setBusy(false));
                }}
              >
                Apply
              </button>
            </aside>
          )}
        </div>
      )}
      {section === "Produce" && <Producer topic={topic} />}
      {deleting && (
        <Confirmation
          title="Delete topic"
          confirmLabel="Delete topic"
          busy={busy}
          onCancel={() => setDeleting(false)}
          onConfirm={() => {
            setBusy(true);
            void command("delete_topic", { topic, confirmed: true })
              .then(onDeleted)
              .catch((e) => setError(errorMessage(e)))
              .finally(() => setBusy(false));
          }}
        >
          <p>Delete {topic} and all its messages? This cannot be undone.</p>
        </Confirmation>
      )}
    </section>
  );
}
