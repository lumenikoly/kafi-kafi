import { expect, test } from "@playwright/test";

test("10,000 streamed messages stay virtualized in the visible topic workspace", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/tests/browser/table-harness.html");

  await page.getByRole("button", { name: "Connect", exact: true }).click();
  await page.getByRole("button", { name: "Topics", exact: true }).click();
  await page
    .getByRole("button", {
      name: "browser_viewport_regression",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Start", exact: true }).click();

  await page.evaluate(() => window.__browserTableTest.startFrameSample());
  await page.evaluate(() =>
    window.__browserTableTest.streamRows(10_000, 100, 8),
  );
  await expect(page.locator(".messages output")).toHaveText(
    "running · 10000 retained · 0 evicted",
  );
  const duringStream = await page.evaluate(() =>
    window.__browserTableTest.stopFrameSample(),
  );
  expect(duringStream.frames).toBeGreaterThan(30);
  expect(duringStream.maxGapMs).toBeLessThan(500);

  const measured = await page.evaluate(() => {
    const scroll = document.querySelector<HTMLElement>(
      ".messages .table-scroll",
    );
    const body = document.querySelector(".messages tbody");
    if (!scroll || !body) throw new Error("Message table is not mounted");
    return {
      viewportHeight: scroll.clientHeight,
      viewportBottom: scroll.getBoundingClientRect().bottom,
      windowHeight: window.innerHeight,
      scrollHeight: scroll.scrollHeight,
      rows: body.children.length,
    };
  });
  expect(measured.viewportHeight).toBeGreaterThan(0);
  expect(measured.viewportBottom).toBeLessThanOrEqual(measured.windowHeight);
  expect(measured.scrollHeight).toBeGreaterThan(measured.viewportHeight);
  expect(measured.rows).toBeGreaterThan(0);
  expect(measured.rows).toBeLessThan(100);
  await expect(page.locator(".topic-workspace:not([hidden])")).toHaveCount(1);
  await expect(page.locator(".topic-workspace[hidden]")).toHaveCount(0);

  await page.getByRole("button", { name: "Topics", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Topics", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".topic-workspace:not([hidden])")).toHaveCount(0);
  await expect(page.locator(".topic-workspace[hidden]")).toHaveCount(1);
  await expect
    .poll(() => page.locator(".topic-workspace[hidden] tbody tr").count())
    .toBeLessThan(100);

  await page
    .getByRole("button", {
      name: "browser_viewport_regression",
      exact: true,
    })
    .click();
  await expect(page.locator(".messages output")).toHaveText(
    "running · 10000 retained · 0 evicted",
  );
  await expect(page.locator(".topic-workspace:not([hidden])")).toHaveCount(1);
  await expect(page.locator(".topic-workspace[hidden]")).toHaveCount(0);

  await page.locator(".messages .table-scroll").evaluate((element) => {
    element.scrollTop = element.scrollHeight / 2;
    element.dispatchEvent(new Event("scroll"));
  });
  await expect
    .poll(() => page.locator(".messages tbody tr").count())
    .toBeLessThan(100);

  await page.setViewportSize({ width: 900, height: 560 });
  await expect
    .poll(() =>
      page.locator(".messages .table-scroll").evaluate((el) => el.clientHeight),
    )
    .toBeLessThan(560);
  await expect
    .poll(() =>
      page.locator(".messages .table-scroll").evaluate((el) => {
        const bounds = el.getBoundingClientRect();
        return bounds.bottom <= window.innerHeight;
      }),
    )
    .toBe(true);
  await expect
    .poll(() => page.locator(".messages tbody tr").count())
    .toBeLessThan(100);
  await expect(page.locator(".topic-workspace:not([hidden])")).toHaveCount(1);
  await expect(page.locator(".topic-workspace[hidden]")).toHaveCount(0);
});
