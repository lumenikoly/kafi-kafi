import { useCallback, useEffect, useState } from "react";
import { columnsFor, DataTable } from "../../components/DataTable";
import { Confirmation, ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import type {
  ConsumerGroup,
  GroupDetail,
  GroupOffset,
  ResetPreview,
} from "../../ipc/types";

const groupColumns = columnsFor<ConsumerGroup>([
  ["id", "Group"],
  ["state", "State"],
  ["memberCount", "Members"],
  ["topicCount", "Topics"],
]);
const offsetColumns = columnsFor<GroupOffset>([
  ["topic", "Topic"],
  ["partition", "Partition"],
  ["committedOffset", "Committed"],
  ["endOffset", "End"],
  ["lag", "Lag"],
]);
export function Groups() {
  const [groups, setGroups] = useState<ConsumerGroup[]>([]);
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<GroupDetail | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [remove, setRemove] = useState(false);
  const [topic, setTopic] = useState("");
  const [partition, setPartition] = useState("");
  const [position, setPosition] = useState("latest");
  const [offset, setOffset] = useState("0");
  const [timestamp, setTimestamp] = useState("");
  const [preview, setPreview] = useState<ResetPreview | null>(null);
  const refresh = useCallback(
    () =>
      command("list_consumer_groups")
        .then(setGroups)
        .catch((e) => setError(errorMessage(e))),
    [],
  );
  const select = async (id: string) => {
    setBusy(true);
    setDetail(null);
    setError("");
    try {
      setDetail(await command("describe_consumer_group", { id }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return (
    <section className="page">
      <div className="toolbar">
        <h2>Consumer groups</h2>
        <input
          aria-label="Search groups"
          placeholder="Search groups"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="button" disabled={busy} onClick={() => void refresh()}>
          Refresh
        </button>
      </div>
      <ErrorBanner message={error} />
      <div className="split fill">
        <DataTable
          data={groups.filter((g) =>
            g.id.toLowerCase().includes(query.toLowerCase()),
          )}
          columns={groupColumns}
          label="Consumer groups"
          onSelect={(g) => void select(g.id)}
        />
        {detail && (
          <aside className="inspector group-inspector">
            <h3>
              {detail.id} · {detail.state}
            </h3>
            <button type="button" onClick={() => void select(detail.id)}>
              Refresh group
            </button>
            <h4>Members</h4>
            {detail.members.map((member) => (
              <div key={member.id}>
                <p>
                  {member.clientId} · {member.clientHost}
                </p>
                {member.assignments.map((a) => (
                  <p key={a.topic}>
                    {a.topic}: {a.partitions.join(", ")}
                  </p>
                ))}
              </div>
            ))}
            {!detail.members.length && (
              <p className="muted">No active members</p>
            )}
            <DataTable
              data={detail.offsets}
              columns={offsetColumns}
              label="Group offsets"
            />
            <h4>Reset offsets</h4>
            <Field label="Topic">
              <input
                aria-label="Reset topic"
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
              />
            </Field>
            <Field label="Partition">
              <input
                aria-label="Reset partition"
                type="number"
                min="0"
                placeholder="All partitions"
                value={partition}
                onChange={(e) => setPartition(e.target.value)}
              />
            </Field>
            <Field label="Position">
              <select
                aria-label="Reset position"
                value={position}
                onChange={(e) => setPosition(e.target.value)}
              >
                {["earliest", "latest", "timestamp", "offset"].map((p) => (
                  <option key={p}>{p}</option>
                ))}
              </select>
            </Field>
            {position === "offset" && (
              <Field label="Offset">
                <input
                  aria-label="Reset offset"
                  type="number"
                  min="0"
                  value={offset}
                  onChange={(e) => setOffset(e.target.value)}
                />
              </Field>
            )}
            {position === "timestamp" && (
              <Field label="Timestamp">
                <input
                  aria-label="Reset timestamp"
                  type="datetime-local"
                  value={timestamp}
                  onChange={(e) => setTimestamp(e.target.value)}
                />
              </Field>
            )}
            <div className="toolbar">
              <button
                type="button"
                disabled={busy || detail.members.length > 0}
                onClick={() => {
                  setBusy(true);
                  void command("preview_group_offsets", {
                    request: {
                      group: detail.id,
                      topic,
                      partition: partition === "" ? null : Number(partition),
                      position,
                      offset: position === "offset" ? Number(offset) : null,
                      timestamp:
                        position === "timestamp"
                          ? new Date(timestamp).getTime()
                          : null,
                    },
                  })
                    .then(setPreview)
                    .catch((e) => setError(errorMessage(e)))
                    .finally(() => setBusy(false));
                }}
              >
                Preview reset
              </button>
              <button
                className="danger"
                type="button"
                disabled={detail.members.length > 0}
                onClick={() => setRemove(true)}
              >
                Delete group
              </button>
            </div>
          </aside>
        )}
      </div>
      {preview && (
        <Confirmation
          title="Reset consumer group offsets"
          confirmLabel="Reset offsets"
          busy={busy}
          onCancel={() => setPreview(null)}
          onConfirm={() => {
            setBusy(true);
            void command("reset_group_offsets", {
              token: preview.token,
              confirmed: true,
            })
              .then(() => {
                setPreview(null);
                void select(preview.group);
              })
              .catch((e) => setError(errorMessage(e)))
              .finally(() => setBusy(false));
          }}
        >
          <p>Group: {preview.group}</p>
          <table>
            <thead>
              <tr>
                <th>Topic</th>
                <th>Partition</th>
                <th>Old offset</th>
                <th>New offset</th>
              </tr>
            </thead>
            <tbody>
              {preview.changes.map((c) => (
                <tr key={`${c.topic}:${c.partition}`}>
                  <td>{c.topic}</td>
                  <td>{c.partition}</td>
                  <td>{c.oldOffset ?? "unset"}</td>
                  <td>{c.newOffset}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <ErrorBanner message={error} />
        </Confirmation>
      )}
      {remove && detail && (
        <Confirmation
          title="Delete consumer group"
          confirmLabel="Delete group"
          busy={busy}
          onCancel={() => setRemove(false)}
          onConfirm={() => {
            setBusy(true);
            void command("delete_consumer_group", {
              id: detail.id,
              confirmed: true,
            })
              .then(() => {
                setRemove(false);
                setDetail(null);
                void refresh();
              })
              .catch((e) => setError(errorMessage(e)))
              .finally(() => setBusy(false));
          }}
        >
          <p>Delete {detail.id} and its committed offsets?</p>
          <ErrorBanner message={error} />
        </Confirmation>
      )}
    </section>
  );
}
