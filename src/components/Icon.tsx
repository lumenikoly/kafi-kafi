const paths = {
  Connections: "M8 3v4m8-4v4M6 7h12v3a6 6 0 0 1-6 6v5m-6-11V7",
  Cluster: "M12 8v4m-7 4v-4h14v4M9 3h6v5H9zM2 16h6v5H2zM16 16h6v5h-6z",
  Brokers: "M4 3h16v7H4zM4 14h16v7H4zM7 6.5h.01M7 17.5h.01M11 6.5h6M11 17.5h6",
  Topics: "M4 5h16M4 12h16M4 19h16",
  "Consumer Groups":
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  Settings:
    "M4 4v3m0 6v7M12 4v9m0 6v1M20 4v1m0 6v9M1 7h6v6H1zM9 13h6v6H9zM17 5h6v6h-6z",
  Delete: "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7",
  Refresh: "M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 3M18 18a8 8 0 0 1-13-3",
  Plus: "M12 5v14M5 12h14",
} as const;

export function Icon({ name }: { name: keyof typeof paths }) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={paths[name]} />
    </svg>
  );
}
