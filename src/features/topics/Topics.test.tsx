import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { filterTopics, Topics } from "./Topics";

const { command } = vi.hoisted(() => ({ command: vi.fn() }));
vi.mock("../../ipc/client", () => ({
  command,
  errorMessage: (e: unknown) =>
    e instanceof Error ? e.message : "Operation failed",
}));
vi.mock("../../components/DataTable", () => ({
  columnsFor: () => [],
  DataTable: ({
    data,
    onSelect,
  }: {
    data: Array<{ name: string }>;
    onSelect?: (row: { name: string }) => void;
  }) => (
    <div>
      {data.map((row) => (
        <button type="button" key={row.name} onClick={() => onSelect?.(row)}>
          {row.name}
        </button>
      ))}
    </div>
  ),
}));

const topics = [
  { name: "orders", partitions: 3, internal: false },
  { name: "__consumer_offsets", partitions: 12, internal: true },
  { name: "payments", partitions: 2, internal: false },
];

describe("Topics", () => {
  beforeEach(() => command.mockReset().mockResolvedValue(topics));

  it("searches case-insensitively and hides internal topics until enabled", () => {
    expect(filterTopics(topics, "PAY", false).map((t) => t.name)).toEqual([
      "payments",
    ]);
    expect(filterTopics(topics, "", false).map((t) => t.name)).toEqual([
      "orders",
      "payments",
    ]);
    expect(filterTopics(topics, "", true)).toHaveLength(3);
  });

  it("filters the visible list from search and internal controls", async () => {
    render(<Topics onOpen={vi.fn()} />);
    expect(
      await screen.findByRole("button", { name: "orders" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "__consumer_offsets" }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Search topics" }), {
      target: { value: "PAY" },
    });
    expect(
      screen.getByRole("button", { name: "payments" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "orders" }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Search topics" }), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByLabelText("Internal topics"));
    expect(
      screen.getByRole("button", { name: "__consumer_offsets" }),
    ).toBeInTheDocument();
  });

  it("validates new topic details before sending the create command", async () => {
    render(<Topics onOpen={vi.fn()} />);
    fireEvent.click(
      await screen.findByRole("button", { name: "Create topic" }),
    );
    fireEvent.change(screen.getByLabelText("Topic name"), {
      target: { value: ".." },
    });
    fireEvent.click(screen.getByRole("button", { name: /^Create$/ }));
    expect(
      screen
        .getAllByRole("alert")
        .some((alert) =>
          alert.textContent?.includes("Enter a valid topic name"),
        ),
    ).toBe(true);
    expect(command).not.toHaveBeenCalledWith("create_topic", expect.anything());
  });

  it("creates a valid topic, refreshes the list, and surfaces IPC errors", async () => {
    command.mockImplementation(async (name: string) => {
      if (name === "create_topic") return null;
      if (name === "list_topics") return topics;
      return null;
    });
    render(<Topics onOpen={vi.fn()} />);
    fireEvent.click(
      await screen.findByRole("button", { name: "Create topic" }),
    );
    fireEvent.change(screen.getByLabelText("Topic name"), {
      target: { value: "events.v1" },
    });
    fireEvent.change(screen.getByLabelText("Configuration"), {
      target: { value: "cleanup.policy=compact" },
    });
    fireEvent.click(screen.getByRole("button", { name: /^Create$/ }));
    await waitFor(() =>
      expect(command).toHaveBeenCalledWith("create_topic", {
        request: {
          name: "events.v1",
          partitions: 1,
          replicationFactor: 1,
          config: { "cleanup.policy": "compact" },
        },
      }),
    );
    await waitFor(() =>
      expect(
        command.mock.calls.filter(([name]) => name === "list_topics"),
      ).toHaveLength(2),
    );

    command.mockImplementation(async (name: string) => {
      if (name === "list_topics") throw new Error("broker unavailable");
      return null;
    });
    fireEvent.click(screen.getByRole("button", { name: "Refresh" }));
    await waitFor(() =>
      expect(
        screen
          .getAllByRole("alert")
          .some((alert) => alert.textContent?.includes("broker unavailable")),
      ).toBe(true),
    );
  });
});
