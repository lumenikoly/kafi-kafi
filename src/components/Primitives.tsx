import {
  Children,
  cloneElement,
  isValidElement,
  type ReactElement,
  type ReactNode,
  useEffect,
  useId,
  useRef,
} from "react";
export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  const id = useId();
  let labelled = false;
  const attach = (nodes: ReactNode): ReactNode =>
    Children.map(nodes, (node) => {
      if (!isValidElement(node)) return node;
      const element = node as ReactElement<{
        id?: string;
        children?: ReactNode;
      }>;
      if (
        !labelled &&
        ["input", "textarea", "select"].includes(String(element.type))
      ) {
        labelled = true;
        return cloneElement(element, { id });
      }
      return element.props.children
        ? cloneElement(element, {}, attach(element.props.children))
        : element;
    });
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <span>{attach(children)}</span>
    </label>
  );
}
export function ErrorBanner({ message }: { message: string }) {
  return message ? (
    <div role="alert" className="error">
      {message}
    </div>
  ) : null;
}
export function Confirmation({
  title,
  children,
  onConfirm,
  onCancel,
  busy = false,
}: {
  title: string;
  children: ReactNode;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
}) {
  const panel = useRef<HTMLElement>(null);
  const cancel = useRef(onCancel);
  cancel.current = onCancel;
  useEffect(() => {
    const previous = document.activeElement;
    panel.current?.querySelector<HTMLButtonElement>("button")?.focus();
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) {
        event.preventDefault();
        cancel.current();
      }
      if (event.key === "Tab") {
        const controls = panel.current?.querySelectorAll<HTMLElement>(
          'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]',
        );
        const first = controls?.[0];
        const last = controls?.[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    };
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("keydown", key);
      if (previous instanceof HTMLElement) previous.focus();
    };
  }, [busy]);
  return (
    <div className="modal-backdrop">
      <section
        ref={panel}
        className="modal"
        role="alertdialog"
        aria-label={title}
        aria-modal="true"
      >
        <h2>{title}</h2>
        {children}
        <div className="toolbar">
          <button type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            className="danger"
            type="button"
            onClick={onConfirm}
            disabled={busy}
          >
            Confirm
          </button>
        </div>
      </section>
    </div>
  );
}
