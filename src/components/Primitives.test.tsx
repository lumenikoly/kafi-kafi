import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Confirmation, Field } from "./Primitives";

describe("Confirmation", () => {
  it("lets the user cancel without confirming", () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    render(
      <Confirmation
        title="Delete item"
        onCancel={onCancel}
        onConfirm={onConfirm}
      >
        Delete it?
      </Confirmation>,
    );

    expect(
      screen.getByRole("alertdialog", { name: "Delete item" }),
    ).toHaveTextContent("Delete it?");
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("confirms the action and prevents either choice while busy", () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    const { rerender } = render(
      <Confirmation
        title="Delete item"
        onCancel={onCancel}
        onConfirm={onConfirm}
      >
        Delete it?
      </Confirmation>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    expect(onConfirm).toHaveBeenCalledOnce();

    rerender(
      <Confirmation
        title="Delete item"
        onCancel={onCancel}
        onConfirm={onConfirm}
        busy
      >
        Delete it?
      </Confirmation>,
    );
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });

  it("focuses Cancel and traps Tab in both directions", () => {
    render(
      <Confirmation title="Delete item" onCancel={vi.fn()} onConfirm={vi.fn()}>
        Delete it?
      </Confirmation>,
    );
    const cancel = screen.getByRole("button", { name: "Cancel" });
    const confirm = screen.getByRole("button", { name: "Confirm" });
    expect(document.activeElement).toBe(cancel);

    confirm.focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(document.activeElement).toBe(cancel);
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(confirm);
  });

  it("cancels on Escape unless the dialog is busy", () => {
    const onCancel = vi.fn();
    const { rerender } = render(
      <Confirmation title="Delete item" onCancel={onCancel} onConfirm={vi.fn()}>
        Delete it?
      </Confirmation>,
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledOnce();

    rerender(
      <Confirmation
        title="Delete item"
        onCancel={onCancel}
        onConfirm={vi.fn()}
        busy
      >
        Delete it?
      </Confirmation>,
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledOnce();
  });
});

describe("Field", () => {
  it("associates its label with the first nested form control", () => {
    render(
      <Field label="Broker address">
        <div className="control-wrap">
          <input aria-label="Broker address input" />
          <button type="button">Browse</button>
        </div>
      </Field>,
    );
    const input = screen.getByLabelText("Broker address input");
    const label = screen.getByText("Broker address").closest("label");
    expect(label).toHaveAttribute("for", input.id);
    expect(screen.getByLabelText("Broker address")).toBe(input);
  });
});
