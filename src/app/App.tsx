import { useEffect, useState } from "react";
import { columnsFor, DataTable } from "../components/DataTable";
import { ErrorBanner } from "../components/Primitives";
import { Connections } from "../features/connections/Connections";
import { Groups } from "../features/groups/Groups";
import { Settings } from "../features/settings/Settings";
import { Topics, TopicWorkspace } from "../features/topics/Topics";
import { command, errorMessage } from "../ipc/client";
import type { Settings as AppSettings, Broker, Cluster } from "../ipc/types";

const brokerColumns = columnsFor<Broker>([
  ["id", "Broker"],
  ["host", "Host"],
  ["port", "Port"],
  ["rack", "Rack"],
]);
const defaults: AppSettings = {
  messageBufferLimit: 10000,
  messageBufferBytes: 64 * 1024 * 1024,
  defaultConsumerStartPosition: "latest",
  layout: {},
};
const navigation = [
  ["Connections", "◈"],
  ["Cluster", "◎"],
  ["Brokers", "▤"],
  ["Topics", "≡"],
  ["Consumer Groups", "◉"],
  ["Settings", "⚙"],
] as const;
export function App() {
  const [page, setPage] = useState("Connections");
  const [cluster, setCluster] = useState<Cluster | null>(null);
  const [settings, setSettings] = useState(defaults);
  const [topics, setTopics] = useState<string[]>([]);
  const [activeTopic, setActiveTopic] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [legacy, setLegacy] = useState(false);
  useEffect(() => {
    requestAnimationFrame(() => {
      void command("ui_ready").catch(() => {});
    });
    void command("get_settings")
      .then(setSettings)
      .catch((e) => setError(errorMessage(e)));
    void command("get_legacy_status")
      .then((s) => setLegacy(s.available && !s.imported))
      .catch(() => {});
  }, []);
  const connect = async (id: string) => {
    setBusy(true);
    setError("");
    try {
      const next = await command("connect", { id });
      setCluster(next);
      setTopics([]);
      setActiveTopic(null);
      setPage("Cluster");
    } finally {
      setBusy(false);
    }
  };
  const refresh = async () => {
    setBusy(true);
    try {
      setCluster(await command("get_cluster"));
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const open = (topic: string) => {
    setTopics((old) => (old.includes(topic) ? old : [...old, topic]));
    setActiveTopic(topic);
    setPage("Topic");
  };
  const close = (topic: string) => {
    setTopics((old) => old.filter((t) => t !== topic));
    if (activeTopic === topic) {
      setActiveTopic(null);
      setPage("Topics");
    }
  };
  return (
    <div className="app-shell">
      <nav className="nav-rail" aria-label="Primary navigation">
        <div className="brand" title="Kafi Kafi">
          K
        </div>
        {navigation.map(([name, icon]) => (
          <button
            type="button"
            key={name}
            title={name}
            aria-label={name}
            className={page === name ? "active" : ""}
            onClick={() => {
              setPage(name);
              setActiveTopic(null);
            }}
          >
            <span aria-hidden="true">{icon}</span>
          </button>
        ))}
      </nav>
      <main>
        <header className="status-bar">
          <span className={`status-dot ${cluster ? "connected" : ""}`} />
          <strong>Kafi Kafi</strong>
          <span>
            {cluster
              ? `${cluster.clusterId ?? "Kafka cluster"} · ${cluster.brokers.length} brokers`
              : "Disconnected"}
          </span>
          <span className="grow" />
          {cluster && (
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                void command("disconnect")
                  .then(() => {
                    setCluster(null);
                    setTopics([]);
                    setActiveTopic(null);
                    setPage("Connections");
                  })
                  .catch((e) => setError(errorMessage(e)));
              }}
            >
              Disconnect
            </button>
          )}
          <span>{busy ? "Working…" : "Local desktop"}</span>
        </header>
        <div className="tabs" role="tablist" aria-label="Workspace">
          <button
            type="button"
            role="tab"
            aria-selected={!activeTopic}
            onClick={() => {
              setActiveTopic(null);
              setPage(page === "Topic" ? "Topics" : page);
            }}
          >
            {page === "Topic" ? "Topics" : page}
          </button>
          {topics.map((topic) => (
            <div
              className={`tab ${activeTopic === topic ? "active" : ""}`}
              key={topic}
            >
              <button
                type="button"
                role="tab"
                aria-selected={activeTopic === topic}
                onClick={() => open(topic)}
              >
                {topic}
              </button>
              <button
                type="button"
                aria-label={`Close ${topic}`}
                onClick={() => close(topic)}
              >
                ×
              </button>
            </div>
          ))}
        </div>
        <ErrorBanner message={error} />
        <div className="workspace">
          {page === "Connections" && (
            <Connections
              active={cluster?.profileId ?? null}
              onConnect={connect}
            />
          )}
          {page === "Settings" && (
            <Settings settings={settings} onSave={setSettings} />
          )}
          {!cluster && !["Connections", "Settings"].includes(page) && (
            <div className="empty">
              <h2>Connect to Kafka</h2>
              <p>Select a profile to inspect your cluster.</p>
              <button type="button" onClick={() => setPage("Connections")}>
                Open connections
              </button>
            </div>
          )}
          {cluster && page === "Cluster" && (
            <section className="page">
              <div className="toolbar">
                <h2>Cluster</h2>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void refresh()}
                >
                  Refresh
                </button>
              </div>
              <dl>
                <dt>Cluster ID</dt>
                <dd>{cluster.clusterId ?? "Unavailable"}</dd>
                <dt>Controller</dt>
                <dd>{cluster.controller ?? "Unavailable"}</dd>
                <dt>Connection</dt>
                <dd>{cluster.profileId}</dd>
              </dl>
              <DataTable
                data={cluster.brokers}
                columns={brokerColumns}
                label="Cluster brokers"
              />
            </section>
          )}
          {cluster && page === "Brokers" && (
            <section className="page">
              <div className="toolbar">
                <h2>Brokers</h2>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void refresh()}
                >
                  Refresh
                </button>
              </div>
              <DataTable
                data={cluster.brokers}
                columns={brokerColumns}
                label="Brokers"
              />
            </section>
          )}
          {cluster && page === "Topics" && <Topics onOpen={open} />}
          {cluster && page === "Consumer Groups" && <Groups />}
          {cluster &&
            topics.map((topic) => (
              <div
                key={`${cluster.generation}:${topic}`}
                className="topic-workspace"
                hidden={activeTopic !== topic || page !== "Topic"}
              >
                <TopicWorkspace
                  topic={topic}
                  settings={settings}
                  onDeleted={() => close(topic)}
                />
              </div>
            ))}
          {legacy && page === "Connections" && (
            <div className="legacy-notice">
              Kotlin configuration found.{" "}
              <button
                type="button"
                onClick={() => {
                  setPage("Settings");
                  setLegacy(false);
                }}
              >
                Review import
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
