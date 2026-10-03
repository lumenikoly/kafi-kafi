import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  Batch,
  MessageDetail,
  MessageRow,
  Settings,
} from "../../ipc/types";
import { Messages, PayloadView } from "./Messages";

const { command, messageChannel, handlers } = vi.hoisted(() => ({
  command: vi.fn(),
  messageChannel: vi.fn(),
  handlers: [] as Array<(batch: Batch) => void>,
}));
vi.mock("../../ipc/client", () => ({
  command,
  errorMessage: (e: unknown) =>
    e instanceof Error ? e.message : "Operation failed",
  messageChannel,
}));
vi.mock("../../components/DataTable", () => ({
  columnsFor: () => [],
  DataTable: ({
    data,
    onSelect,
  }: {
    data: MessageRow[];
    onSelect?: (row: MessageRow) => void;
  }) => (
    <div>
      {data.map((row) => (
        <button type="button" key={row.id} onClick={() => onSelect?.(row)}>
          {row.valuePreview}
        </button>
      ))}
    </div>
  ),
}));

const settings: Settings = {
  messageBufferLimit: 100,
  messageBufferBytes: 1024 * 1024,
  defaultConsumerStartPosition: "latest",
  layout: {},
};
const row: MessageRow = {
  id: "42",
  partition: 0,
  offset: 42,
  timestamp: 0,
  keyPreview: "key",
  valuePreview: "hello",
  keySize: 3,
  valueSize: 5,
  valueType: "text",
  headersCount: 0,
};
const detail: MessageDetail = {
  id: "42",
  topic: "orders",
  partition: 0,
  offset: 42,
  timestamp: 0,
  key: {
    kind: "text",
    size: 3,
    preview: "key",
    text: "key",
    base64: null,
    truncated: false,
  },
  value: {
    kind: "json",
    size: 7,
    preview: '{"a":1}',
    text: '{"a":1}',
    base64: null,
    truncated: false,
  },
  headers: [],
};
const batch = (overrides: Partial<Batch> = {}): Batch => ({
  sessionId: "session-1",
  sequence: 1,
  rows: [row],
  firstRetainedId: "42",
  dropped: 0,
  retained: 1,
  reset: false,
  status: "running",
  error: null,
  ...overrides,
});

describe("Messages", () => {
  beforeEach(() => {
    command.mockReset().mockResolvedValue(null);
    handlers.length = 0;
    messageChannel
      .mockReset()
      .mockImplementation((handler: (value: Batch) => void) => {
        handlers.push(handler);
        return {};
      });
  });

  it("starts the selected consumer and supports Pause, Resume, and Stop", async () => {
    command.mockImplementation(async (name: string) =>
      name === "start_consumer" ? "session-1" : null,
    );
    render(<Messages topic="orders" settings={settings} />);
    fireEvent.change(screen.getByLabelText("Start position"), {
      target: { value: "offset" },
    });
    fireEvent.change(screen.getByLabelText("Offset"), {
      target: { value: "27" },
    });
    fireEvent.change(screen.getByLabelText("Read partition"), {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Start" }));

    await waitFor(() =>
      expect(command).toHaveBeenCalledWith("start_consumer", {
        request: {
          topic: "orders",
          partition: 2,
          position: "offset",
          offset: 27,
          timestamp: null,
        },
        channel: expect.anything(),
      }),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Pause" })).toBeEnabled(),
    );
    expect(command).toHaveBeenCalledWith("set_consumer_filter", {
      sessionId: "session-1",
      filter: { key: "", value: "", partition: null },
    });
    fireEvent.click(screen.getByRole("button", { name: "Pause" }));
    await waitFor(() =>
      expect(command).toHaveBeenCalledWith("pause_consumer", {
        sessionId: "session-1",
      }),
    );

    handlers[0]?.(batch({ sequence: 2, status: "paused" }));
    fireEvent.click(await screen.findByRole("button", { name: "Resume" }));
    await waitFor(() =>
      expect(command).toHaveBeenCalledWith("resume_consumer", {
        sessionId: "session-1",
      }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Stop" }));
    await waitFor(() =>
      expect(command).toHaveBeenCalledWith("stop_consumer", {
        sessionId: "session-1",
      }),
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "stopped · 0 retained",
    );
  });

  it("loads a selected message into the inspector and can switch JSON to raw", async () => {
    command.mockImplementation(async (name: string) =>
      name === "start_consumer"
        ? "session-1"
        : name === "get_message_detail"
          ? detail
          : null,
    );
    render(<Messages topic="orders" settings={settings} />);
    fireEvent.click(screen.getByRole("button", { name: "Start" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Pause" })).toBeEnabled(),
    );
    handlers[0]?.(batch());
    fireEvent.click(await screen.findByRole("button", { name: "hello" }));

    expect(
      await screen.findByRole("heading", { name: "Record · 0:42" }),
    ).toBeInTheDocument();
    expect(command).toHaveBeenCalledWith("get_message_detail", {
      sessionId: "session-1",
      messageId: "42",
      full: false,
    });
    expect(screen.getByText(/"a": 1/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Raw" }));
    expect(screen.getByText('{"a":1}')).toBeInTheDocument();
  });

  it("surfaces start failures and formats payload states", async () => {
    command.mockRejectedValue(new Error("consumer could not start"));
    render(<Messages topic="orders" settings={settings} />);
    fireEvent.click(screen.getByRole("button", { name: "Start" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "consumer could not start",
    );

    const { rerender } = render(<PayloadView payload={null} raw={false} />);
    expect(screen.getByText("null (tombstone)")).toBeInTheDocument();
    rerender(
      <PayloadView
        payload={{
          kind: "binary",
          size: 9,
          preview: "00 ff",
          text: null,
          base64: "AP8=",
          truncated: true,
        }}
        raw={false}
      />,
    );
    expect(screen.getByText("binary · 9 bytes · preview")).toBeInTheDocument();
    expect(screen.getByText("00 ff")).toBeInTheDocument();
  });

  it("stops a session whose start response arrives after its channel reports failure", async () => {
    let resolveStart!: (sessionId: string) => void;
    const startResult = new Promise<string>((resolve) => {
      resolveStart = resolve;
    });
    command.mockImplementation((name: string) =>
      name === "start_consumer" ? startResult : Promise.resolve(null),
    );
    render(<Messages topic="orders" settings={settings} />);
    fireEvent.click(screen.getByRole("button", { name: "Start" }));
    expect(handlers).toHaveLength(1);

    act(() => {
      handlers[0]?.(
        batch({
          status: "failed",
          error: {
            code: "consumer_failed",
            message: "Broker disconnected",
            details: null,
            retryable: true,
          },
        }),
      );
    });
    await act(async () => {
      resolveStart("late-session");
      await startResult;
    });

    expect(screen.getByRole("button", { name: "Pause" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("failed · 1 retained");
    expect(command).toHaveBeenCalledWith("stop_consumer", {
      sessionId: "late-session",
    });
  });
});
