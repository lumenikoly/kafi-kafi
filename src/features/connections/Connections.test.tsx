import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Connections } from "./Connections";

const { command } = vi.hoisted(() => ({ command: vi.fn() }));
vi.mock("../../ipc/client", () => ({
  command,
  errorMessage: (e: unknown) =>
    e instanceof Error ? e.message : "Operation failed",
}));

const profile = {
  id: "profile-1",
  name: "Development",
  bootstrapServers: ["localhost:9092"],
  clientId: null,
  securityProtocol: "PLAINTEXT",
  sasl: null,
  tls: null,
  extraProperties: {},
};

describe("Connections", () => {
  beforeEach(() => command.mockReset().mockResolvedValue([]));

  it("blocks invalid connection details before invoking IPC", async () => {
    render(<Connections active={null} onConnect={vi.fn()} />);
    await screen.findByRole("heading", { name: "Connection profile" });
    fireEvent.click(screen.getByRole("button", { name: "Test connection" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter a connection name.",
    );
    expect(command).not.toHaveBeenCalledWith(
      "test_connection",
      expect.anything(),
    );

    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "Local" },
    });
    fireEvent.change(screen.getByLabelText("Bootstrap servers"), {
      target: { value: "bad-server" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter bootstrap servers as host:port.",
    );
  });

  it("saves only supplied credentials, properties, and clears secret inputs after success", async () => {
    command.mockImplementation(async (name: string) => {
      if (name === "save_profile")
        return {
          profile: {
            ...profile,
            name: "Local",
            securityProtocol: "SASL_PLAINTEXT",
            sasl: {
              mechanism: "PLAIN",
              username: "alice",
              passwordRef: "secret",
            },
          },
          warnings: [],
        };
      return [];
    });
    render(<Connections active={null} onConnect={vi.fn()} />);
    await screen.findByRole("heading", { name: "Connection profile" });
    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "Local" },
    });
    fireEvent.change(screen.getByLabelText("Security protocol"), {
      target: { value: "SASL_PLAINTEXT" },
    });
    fireEvent.change(screen.getByLabelText("Username"), {
      target: { value: "alice" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "secret" },
    });
    fireEvent.change(screen.getByLabelText("Extra safe properties"), {
      target: { value: "socket.timeout.ms=10000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(command.mock.calls.some(([name]) => name === "save_profile")).toBe(
        true,
      ),
    );
    const saveCall = command.mock.calls.find(
      ([name]) => name === "save_profile",
    );
    expect(saveCall?.[1]).toEqual({
      request: {
        profile: expect.objectContaining({
          name: "Local",
          bootstrapServers: ["localhost:9092"],
          extraProperties: { "socket.timeout.ms": "10000" },
        }),
        secrets: { "sasl-password": "secret" },
      },
    });
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Profile saved.",
    );
    expect(screen.getByLabelText("Password")).toHaveValue("");
  });

  it("keeps the form editable and shows backend errors", async () => {
    command.mockImplementation(async (name: string) => {
      if (name === "get_profiles") return [];
      if (name === "test_connection") throw new Error("broker unreachable");
      return [];
    });
    render(<Connections active={null} onConnect={vi.fn()} />);
    await screen.findByRole("heading", { name: "Connection profile" });
    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "Local" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Test connection" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "broker unreachable",
    );
  });

  it("clears unsaved credentials when switching profiles", async () => {
    const saslProfile = {
      ...profile,
      securityProtocol: "SASL_PLAINTEXT",
      sasl: { mechanism: "PLAIN", username: "user", passwordRef: "saved" },
    };
    command.mockImplementation(async (name: string) =>
      name === "get_profiles" ? [saslProfile] : [],
    );
    render(<Connections active={null} onConnect={vi.fn()} />);
    fireEvent.change(await screen.findByLabelText("Name"), {
      target: { value: "Unsaved" },
    });
    fireEvent.change(screen.getByLabelText("Security protocol"), {
      target: { value: "SASL_PLAINTEXT" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "secret" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Development" }));
    expect(screen.getByLabelText("Name")).toHaveValue("Development");
    expect(screen.getByLabelText("Password")).toHaveValue("");
  });
});
