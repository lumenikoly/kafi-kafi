import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type SortingState,
  useReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useRef, useState } from "react";
export function DataTable<T>({
  data,
  columns,
  onSelect,
  label,
}: {
  data: T[];
  columns: ColumnDef<T>[];
  onSelect?: (row: T) => void;
  label: string;
}) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [selected, setSelected] = useState<T | null>(null);
  const parent = useRef<HTMLDivElement>(null);
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });
  const rows = table.getRowModel().rows;
  const virtual = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parent.current,
    estimateSize: () => 32,
    overscan: 8,
  });
  const width = columns
    .map((column) => (column.size ? `${column.size}px` : "minmax(100px, 1fr)"))
    .join(" ");
  const minWidth = columns.reduce(
    (sum, column) => sum + (column.size ?? 100),
    0,
  );
  return (
    <div className="table table-scroll" ref={parent}>
      <table
        aria-label={label}
        style={{ display: "grid", width: "100%", minWidth }}
      >
        <thead
          style={{ display: "grid", position: "sticky", top: 0, zIndex: 1 }}
        >
          {table.getHeaderGroups().map((group) => (
            <tr
              className="table-row table-head"
              key={group.id}
              style={{ gridTemplateColumns: width }}
            >
              {group.headers.map((header) => (
                <th
                  scope="col"
                  key={header.id}
                  aria-sort={
                    header.column.getIsSorted() === "asc"
                      ? "ascending"
                      : header.column.getIsSorted() === "desc"
                        ? "descending"
                        : "none"
                  }
                >
                  <button
                    type="button"
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext(),
                    )}
                    {header.column.getIsSorted() === "asc"
                      ? " ↑"
                      : header.column.getIsSorted() === "desc"
                        ? " ↓"
                        : ""}
                  </button>
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody
          style={{
            display: "grid",
            height: virtual.getTotalSize(),
            position: "relative",
          }}
        >
          {virtual.getVirtualItems().map((item) => {
            const row = rows[item.index];
            if (!row) return null;
            return (
              <tr
                key={row.id}
                className={`table-row ${onSelect ? "selectable" : ""} ${selected === row.original ? "selected" : ""}`}
                style={{
                  position: "absolute",
                  top: 0,
                  transform: `translateY(${item.start}px)`,
                  height: item.size,
                  width: "100%",
                  gridTemplateColumns: width,
                }}
              >
                {row.getVisibleCells().map((cell, index) => (
                  <td key={cell.id} title={String(cell.getValue() ?? "")}>
                    {onSelect && index === 0 ? (
                      <button
                        type="button"
                        onClick={() => {
                          setSelected(row.original);
                          onSelect(row.original);
                        }}
                      >
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </button>
                    ) : (
                      flexRender(cell.column.columnDef.cell, cell.getContext())
                    )}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      {!rows.length && <p className="empty">No rows to display</p>}
    </div>
  );
}
export function columnsFor<T>(fields: [keyof T, string][]): ColumnDef<T>[] {
  return fields.map(([key, label]) => ({
    accessorKey: String(key),
    header: label,
    cell: (info) => {
      const value = info.getValue();
      return Array.isArray(value)
        ? value.join(", ")
        : value === null
          ? "—"
          : String(value);
    },
  }));
}
