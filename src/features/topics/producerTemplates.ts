export interface ProducerTemplate {
  id: string;
  name: string;
  topic: string;
  key: string | null;
  value: string | null;
  partition: number | null;
  headers: Record<string, string>;
}

export function readTemplate(input: unknown): ProducerTemplate | null {
  if (!input || typeof input !== "object") return null;
  const t = input as Record<string, unknown>;
  const headers = t.headers ?? {};
  if (
    typeof t.id !== "string" ||
    typeof t.name !== "string" ||
    typeof t.topic !== "string" ||
    (t.key != null && typeof t.key !== "string") ||
    (t.value != null && typeof t.value !== "string") ||
    (t.partition != null &&
      (typeof t.partition !== "number" ||
        !Number.isSafeInteger(t.partition) ||
        t.partition < 0)) ||
    typeof headers !== "object" ||
    Array.isArray(headers) ||
    !Object.values(headers).every((v) => typeof v === "string")
  )
    return null;
  return {
    id: t.id,
    name: t.name,
    topic: t.topic,
    key: (t.key as string | null | undefined) ?? null,
    value: (t.value as string | null | undefined) ?? null,
    partition: (t.partition as number | null | undefined) ?? null,
    headers: headers as Record<string, string>,
  };
}
