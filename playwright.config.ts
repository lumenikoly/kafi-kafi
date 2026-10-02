import { defineConfig, devices } from "@playwright/test";

const isCi = Boolean(
  (globalThis as typeof globalThis & { process?: { env?: { CI?: string } } })
    .process?.env?.CI,
);

export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: false,
  forbidOnly: isCi,
  retries: 0,
  workers: 1,
  timeout: 90_000,
  reporter: "list",
  outputDir: ".tmp/playwright-results",
  use: {
    baseURL: "http://127.0.0.1:1420",
    trace: "retain-on-failure",
    ...devices["Desktop Chrome"],
  },
  webServer: {
    command: "pnpm dev",
    url: "http://127.0.0.1:1420",
    reuseExistingServer: !isCi,
    timeout: 30_000,
  },
});
