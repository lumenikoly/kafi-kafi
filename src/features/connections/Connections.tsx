import { useCallback, useEffect, useState } from "react";
import { Icon } from "../../components/Icon";
import { Confirmation, ErrorBanner, Field } from "../../components/Primitives";
import { command, errorMessage } from "../../ipc/client";
import type { Profile, SaveProfile } from "../../ipc/types";
import { parseProperties, validateProfile } from "./validation";

const fresh = (): Profile => ({
  id: crypto.randomUUID(),
  name: "",
  bootstrapServers: ["localhost:9092"],
  clientId: null,
  securityProtocol: "PLAINTEXT",
  sasl: null,
  tls: null,
  extraProperties: {},
});
export function Connections({
  onConnect,
  active,
}: {
  onConnect: (id: string) => Promise<void>;
  active: string | null;
}) {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [profile, setProfile] = useState(fresh);
  const [password, setPassword] = useState("");
  const [keyPassword, setKeyPassword] = useState("");
  const [storePassword, setStorePassword] = useState("");
  const [properties, setProperties] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [remove, setRemove] = useState<Profile | null>(null);
  const reload = useCallback(
    () =>
      command("get_profiles")
        .then(setProfiles)
        .catch((e) => setError(errorMessage(e))),
    [],
  );
  useEffect(() => {
    void reload();
  }, [reload]);
  const clearSecrets = () => {
    setPassword("");
    setKeyPassword("");
    setStorePassword("");
  };
  const edit = (p: Profile) => {
    clearSecrets();
    setProfile(structuredClone(p));
    setProperties(
      Object.entries(p.extraProperties)
        .map(([k, v]) => `${k}=${v}`)
        .join("\n"),
    );
    setNotice("");
    setError("");
  };
  const request = (): SaveProfile => ({
    profile: { ...profile, extraProperties: parseProperties(properties) },
    secrets: {
      ...(password ? { "sasl-password": password } : {}),
      ...(keyPassword ? { "key-password": keyPassword } : {}),
      ...(storePassword ? { "keystore-password": storePassword } : {}),
    },
  });
  const action = async (save: boolean) => {
    setError("");
    setNotice("");
    const invalid = validateProfile(profile);
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    try {
      if (save) {
        const result = await command("save_profile", { request: request() });
        setProfile(result.profile);
        clearSecrets();
        setNotice(result.warnings.join(" ") || "Profile saved.");
        await reload();
      } else {
        const result = await command("test_connection", { request: request() });
        setNotice(
          `Connected to ${result.clusterId ?? "cluster"}: ${result.brokers.length} broker${result.brokers.length === 1 ? "" : "s"}.`,
        );
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const tls = profile.tls ?? {
    caPath: null,
    certificatePath: null,
    privateKeyPath: null,
    pkcs12Path: null,
    keyPasswordRef: null,
    keystorePasswordRef: null,
    legacyTruststorePasswordRef: null,
  };
  const choose = async (
    field: "caPath" | "certificatePath" | "privateKeyPath" | "pkcs12Path",
  ) => {
    try {
      const path = await command("choose_certificate");
      if (path) setProfile({ ...profile, tls: { ...tls, [field]: path } });
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  return (
    <div className="split">
      <aside className="resource-list">
        <div className="toolbar">
          <h2>Connections</h2>
          <button
            type="button"
            className="ghost"
            onClick={() => {
              edit(fresh());
            }}
          >
            <Icon name="Plus" />
            New
          </button>
        </div>
        {profiles.map((p) => (
          <div
            className={`profile-row ${profile.id === p.id ? "selected" : ""}`}
            key={p.id}
          >
            <button
              type="button"
              aria-label={p.name}
              title={p.name}
              onClick={() => edit(p)}
            >
              {p.name}
              {active === p.id ? " ●" : ""}
              <small>{p.bootstrapServers.join(", ")}</small>
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy}
              onClick={() => {
                setBusy(true);
                void onConnect(p.id)
                  .catch((e) => setError(errorMessage(e)))
                  .finally(() => setBusy(false));
              }}
            >
              Connect
            </button>
            <button
              type="button"
              aria-label={`Delete ${p.name}`}
              className="icon-button danger"
              title={`Delete ${p.name}`}
              onClick={() => setRemove(p)}
            >
              <Icon name="Delete" />
            </button>
          </div>
        ))}
      </aside>
      <section className="editor">
        <h2>Connection profile</h2>
        <ErrorBanner message={error} />
        {notice && <output>{notice}</output>}
        <Field label="Name">
          <input
            aria-label="Name"
            value={profile.name}
            onChange={(e) => setProfile({ ...profile, name: e.target.value })}
          />
        </Field>
        <Field label="Bootstrap servers">
          <input
            aria-label="Bootstrap servers"
            value={profile.bootstrapServers.join(", ")}
            onChange={(e) =>
              setProfile({
                ...profile,
                bootstrapServers: e.target.value
                  .split(",")
                  .map((s) => s.trim()),
              })
            }
          />
        </Field>
        <div className="form-grid">
          <Field label="Client ID">
            <input
              aria-label="Client ID"
              value={profile.clientId ?? ""}
              onChange={(e) =>
                setProfile({ ...profile, clientId: e.target.value || null })
              }
            />
          </Field>
          <Field label="Security protocol">
            <select
              aria-label="Security protocol"
              value={profile.securityProtocol}
              onChange={(e) =>
                setProfile({
                  ...profile,
                  securityProtocol: e.target.value,
                  sasl: e.target.value.startsWith("SASL")
                    ? (profile.sasl ?? {
                        mechanism: "PLAIN",
                        username: "",
                        passwordRef: null,
                      })
                    : null,
                  tls: e.target.value.endsWith("SSL") ? tls : null,
                })
              }
            >
              {["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"].map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
        </div>
        {profile.sasl && (
          <>
            <Field label="SASL mechanism">
              <select
                aria-label="SASL mechanism"
                value={profile.sasl.mechanism}
                onChange={(e) =>
                  setProfile({
                    ...profile,
                    sasl: {
                      ...(profile.sasl ?? {
                        mechanism: "PLAIN",
                        username: "",
                        passwordRef: null,
                      }),
                      mechanism: e.target.value,
                    },
                  })
                }
              >
                {["PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"].map((p) => (
                  <option key={p}>{p}</option>
                ))}
              </select>
            </Field>
            <Field label="Username">
              <input
                aria-label="Username"
                value={profile.sasl.username}
                onChange={(e) =>
                  setProfile({
                    ...profile,
                    sasl: {
                      ...(profile.sasl ?? {
                        mechanism: "PLAIN",
                        username: "",
                        passwordRef: null,
                      }),
                      username: e.target.value,
                    },
                  })
                }
              />
            </Field>
            <Field label="Password">
              <input
                aria-label="Password"
                type="password"
                autoComplete="off"
                placeholder={
                  profile.sasl.passwordRef
                    ? "Saved credential; leave blank to keep"
                    : ""
                }
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
          </>
        )}
        {profile.securityProtocol.endsWith("SSL") && (
          <>
            <p className="muted">
              Hostname verification is enabled. Leave CA blank to use system
              trust.
            </p>
            {(
              [
                ["caPath", "CA (PEM)"],
                ["certificatePath", "Client certificate (PEM)"],
                ["privateKeyPath", "Private key (PEM)"],
                ["pkcs12Path", "Keystore (PKCS#12)"],
              ] as const
            ).map(([key, label]) => (
              <Field label={label} key={key}>
                <div className="toolbar">
                  <input
                    aria-label={label}
                    value={tls[key] ?? ""}
                    onChange={(e) =>
                      setProfile({
                        ...profile,
                        tls: { ...tls, [key]: e.target.value || null },
                      })
                    }
                  />
                  <button type="button" onClick={() => void choose(key)}>
                    Choose
                  </button>
                </div>
              </Field>
            ))}
            <Field label="Private key password">
              <input
                aria-label="Private key password"
                type="password"
                autoComplete="off"
                value={keyPassword}
                onChange={(e) => setKeyPassword(e.target.value)}
              />
            </Field>
            <Field label="Keystore password">
              <input
                aria-label="Keystore password"
                type="password"
                autoComplete="off"
                value={storePassword}
                onChange={(e) => setStorePassword(e.target.value)}
              />
            </Field>
          </>
        )}
        <Field label="Extra safe properties">
          <textarea
            aria-label="Extra safe properties"
            value={properties}
            onChange={(e) => setProperties(e.target.value)}
            placeholder="socket.timeout.ms=10000"
          />
        </Field>
        <div className="toolbar">
          <button
            type="button"
            disabled={busy}
            onClick={() => void action(false)}
          >
            Test connection
          </button>
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={() => void action(true)}
          >
            Save
          </button>
          <button
            type="button"
            onClick={() => {
              edit(fresh());
            }}
          >
            Cancel
          </button>
        </div>
      </section>
      {remove && (
        <Confirmation
          title="Delete connection profile"
          confirmLabel="Delete profile"
          onCancel={() => setRemove(null)}
          busy={busy}
          onConfirm={() => {
            setBusy(true);
            void command("delete_profile", { id: remove.id })
              .then(() => {
                setRemove(null);
                void reload();
              })
              .catch((e) => setError(errorMessage(e)))
              .finally(() => setBusy(false));
          }}
        >
          <p>Delete {remove.name} and its saved credentials?</p>
        </Confirmation>
      )}
    </div>
  );
}
