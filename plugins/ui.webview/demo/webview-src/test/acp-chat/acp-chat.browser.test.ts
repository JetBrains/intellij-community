// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { expect, test } from "@playwright/test"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { startWebViewMockPreview, type MockWebViewCall, type WebViewMockPreviewServer } from "@jetbrains/intellij-webview-testkit"

const testDir = dirname(fileURLToPath(import.meta.url))
const webviewSrcDir = resolve(testDir, "../..")

let preview: WebViewMockPreviewServer

test.beforeAll(async () => {
  preview = await startWebViewMockPreview({
    webviewSrcDir,
    viewId: "acp-chat",
    mock: resolve(testDir, "mocks/default.ts"),
  })
})

test.afterAll(async () => {
  await preview.close()
})

test("renders ACP chat in a real browser with a mock agent", async ({ page }) => {
  await page.goto(preview.url)

  await page.getByRole("button", { name: /select an agent/i }).click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await expect(page.getByRole("button", { name: "Mock Agent" })).toBeVisible()

  await page.getByPlaceholder("Message the agent…").fill("Hello mock")
  await page.getByRole("button", { name: "Send" }).click()

  await expect(page.getByText("Hello mock")).toBeVisible()
  await expect(page.getByText(/Mock response from AI chat: Hello mock/)).toBeVisible()

  const calls = await page.evaluate(() => window.__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? [])
  expect(calls.length).toBeGreaterThan(0)
  expect(calls.some((call: MockWebViewCall) => JSON.stringify(call.params).includes("session/prompt"))).toBe(true)
})
