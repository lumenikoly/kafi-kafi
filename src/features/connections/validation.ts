import type { Profile } from "../../ipc/types";
export function validateProfile(profile: Profile): string {
  if (!profile.name.trim()) return "Enter a connection name.";
  if (
    !profile.bootstrapServers.length ||
    profile.bootstrapServers.some((s) => !s.trim() || !/^.+:\d+$/.test(s))
  )
    return "Enter bootstrap servers as host:port.";
  if (
    profile.securityProtocol.startsWith("SASL") &&
    !profile.sasl?.username.trim()
  )
    return "Enter a SASL username.";
  return "";
}
export function parseProperties(text: string): Record<string, string> {
  const result: Record<string, string> = {};
  for (const line of text.split("\n").filter((s) => s.trim())) {
    const index = line.indexOf("=");
    if (index < 1)
      throw new Error("Properties must use name=value, one per line.");
    result[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  }
  return result;
}
