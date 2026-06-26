// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { startWebViewMockPreview, type MockWebViewCall, type WebViewMockPreviewServer } from "@jetbrains/intellij-webview-testkit"

type Locator = {
  click(): Promise<void>
  fill(value: string): Promise<void>
}

type FilePayload = {
  name: string
  mimeType: string
  buffer: Buffer
}

type FileChooser = {
  setFiles(files: FilePayload | FilePayload[]): Promise<void>
}

type Page = {
  goto(url: string): Promise<void>
  getByRole(role: string, options?: { name?: string | RegExp; exact?: boolean }): Locator
  getByPlaceholder(text: string): Locator
  getByText(text: string | RegExp, options?: { exact?: boolean }): Locator
  locator(selector: string): Locator
  evaluate<Result>(pageFunction: () => Result | Promise<Result>): Promise<Result>
  waitForEvent(event: "filechooser"): Promise<FileChooser>
  waitForFunction(pageFunction: () => boolean): Promise<unknown>
  waitForSelector(selector: string): Promise<unknown>
}

type TestApi = {
  (title: string, handler: (fixtures: { page: Page }) => Promise<void> | void): void
  beforeAll(handler: () => Promise<void> | void): void
  afterAll(handler: () => Promise<void> | void): void
}

type ExpectApi = {
  (actual: Locator): { toBeVisible(): Promise<void> }
  (actual: number): { toBeGreaterThan(expected: number): void }
  (actual: boolean): { toBe(expected: boolean): void }
}

type PlaywrightTestModule = {
  expect: ExpectApi
  test: TestApi
}

type MockWindow = Window & {
  __WVI_MOCK__?: {
    calls: {
      byMethod(method: string): readonly MockWebViewCall[]
    }
  }
}

interface JsonRpcMessage {
  method?: string
  params?: any
}

const playwrightTestPackage: string = "@playwright/test"
const { expect, test } = await import(playwrightTestPackage) as unknown as PlaywrightTestModule

const testDir = dirname(fileURLToPath(import.meta.url))
const webviewSrcDir = resolve(testDir, "../..")

let preview: WebViewMockPreviewServer | undefined

test.beforeAll(async () => {
  preview = await startWebViewMockPreview({
    webviewSrcDir,
    viewId: "acp-chat",
    mock: resolve(testDir, "mocks/default.ts"),
  })
})

test.afterAll(async () => {
  await preview?.close()
})

test("explains attachment capabilities before an agent is activated", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await expect(page.getByRole("button", { name: "Attach file" })).toBeVisible()
  await pasteImageIntoComposer(page)
  await expect(page.getByText("Image attachment support can be detected only after an ACP agent is activated.")).toBeVisible()
  await page.getByRole("button", { name: "Attach file" }).click()
  await expect(page.getByText("Image attachment support can be detected only after an ACP agent is activated.")).toBeVisible()
})

test("renders ACP chat in a real browser with a mock agent", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await expect(page.getByText("Mock Agent")).toBeVisible()

  await page.getByPlaceholder("Message the agent…").fill("Hello mock")
  await page.getByRole("button", { name: "Send" }).click()

  await expect(page.getByText("Hello mock", { exact: true })).toBeVisible()
  await expect(page.getByText(/Mock response from AI chat: Hello mock/)).toBeVisible()

  await page.getByPlaceholder("Message the agent…").fill("streaming probe")
  await page.getByRole("button", { name: "Send" }).click()

  await Promise.all([
    page.waitForSelector(".acpMarkdown--streaming"),
    page.waitForSelector(".acpThinkingBody .acpStreamingCaret"),
  ])
  const streamingCaretStylesApplied = await page.evaluate(() => {
    const markdown = document.querySelector(".acpMarkdown--streaming")
    const markdownCaretHost = markdown?.lastElementChild ?? markdown
    const markdownCaret = markdownCaretHost ? getComputedStyle(markdownCaretHost, "::after") : null
    const thinkingCaret = document.querySelector(".acpThinkingBody .acpStreamingCaret")
    const thinkingCaretStyle = thinkingCaret ? getComputedStyle(thinkingCaret) : null
    return markdownCaret?.animationName === "acpStreamingCaretBlink"
      && markdownCaret.display === "inline-block"
      && thinkingCaretStyle?.animationName === "acpStreamingCaretBlink"
      && thinkingCaretStyle.display === "inline-block"
  })
  expect(streamingCaretStylesApplied).toBe(true)

  await expect(page.getByText(/Streaming markdown response for streaming probe/)).toBeVisible()
  await page.waitForFunction(() => {
    return document.querySelector(".acpMarkdown--streaming") == null
      && document.querySelector(".acpThinkingBody .acpStreamingCaret") == null
  })

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  expect(calls.length).toBeGreaterThan(0)
  expect(calls.some((call: MockWebViewCall) => JSON.stringify(call.params).includes("session/prompt"))).toBe(true)
})

test("drives ACP modes, model selection, and config options through the picker", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()

  await expect(page.getByRole("button", { name: "Mode", exact: true })).toBeVisible()
  await expect(page.getByText("Ask", { exact: true })).toBeVisible()
  await expect(page.getByRole("button", { name: "Model", exact: true })).toBeVisible()
  await expect(page.getByText("Gemini 2.5 Flash", { exact: true })).toBeVisible()

  await page.getByRole("button", { name: "Mode", exact: true }).click()
  await expect(page.getByPlaceholder("Search modes...")).toBeVisible()
  await page.getByPlaceholder("Search modes...").fill("code")
  await page.getByRole("option", { name: /Code/ }).click()
  await expect(page.getByText("Code", { exact: true })).toBeVisible()

  await page.getByRole("button", { name: "Model", exact: true }).click()
  await expect(page.getByPlaceholder("Search models...")).toBeVisible()
  await page.getByPlaceholder("Search models...").fill("pro")
  await page.getByRole("option", { name: /Gemini 2.5 Pro/ }).click()
  await expect(page.getByText("Gemini 2.5 Pro", { exact: true })).toBeVisible()

  await page.getByRole("switch", { name: "Autonomy" }).click()
  await page.waitForFunction(() => document.querySelector(".acpConfigSwitch")?.getAttribute("data-state") === "checked")

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  const rpcMessages = calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
  expect(rpcMessages.some(message => message.method === "session/set_mode"
    && message.params?.sessionId === "mock-session"
    && message.params?.modeId === "code")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "gemini_model"
    && message.params?.value === "gemini-2.5-pro")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "autonomy"
    && message.params?.type === "boolean"
    && message.params?.value === true)).toBe(true)
})

test("sends attached image and text resources as ACP prompt content blocks", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await expect(page.getByRole("button", { name: "Attach file" })).toBeVisible()

  const fileChooserPromise = page.waitForEvent("filechooser")
  await page.getByRole("button", { name: "Attach file" }).click()
  const fileChooser = await fileChooserPromise
  await fileChooser.setFiles([
    { name: "notes.txt", mimeType: "text/plain", buffer: Buffer.from("attached text") },
    { name: "pixel.png", mimeType: "image/png", buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47]) },
  ])

  await expect(page.getByText("notes.txt", { exact: true })).toBeVisible()
  await expect(page.getByText("pixel.png", { exact: true })).toBeVisible()

  await page.getByPlaceholder("Message the agent…").fill("attachment probe")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText(/Mock response from AI chat: attachment probe/)).toBeVisible()

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  const rpcMessages = calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
  const promptRequest = rpcMessages.find(message => message.method === "session/prompt"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "attachment probe"))
  if (!promptRequest) {
    throw new Error("No attachment probe prompt request was recorded")
  }
  const prompt = promptRequest.params.prompt
  const hasImageBlock: boolean = prompt.some((block: any) => block?.type === "image"
    && block.mimeType === "image/png"
    && typeof block.data === "string"
    && block.data.length > 0
    && typeof block.uri === "string"
    && block.uri.startsWith("attachment://"))
  const hasTextResourceBlock: boolean = prompt.some((block: any) => block?.type === "resource"
    && block.resource?.mimeType === "text/plain"
    && block.resource?.text === "attached text"
    && typeof block.resource?.uri === "string"
    && block.resource.uri.startsWith("attachment://"))
  expect(hasImageBlock).toBe(true)
  expect(hasTextResourceBlock).toBe(true)
})

function parseMockRpcLine(params: unknown): JsonRpcMessage | null {
  const line = typeof (params as { line?: unknown })?.line === "string" ? (params as { line: string }).line : null
  if (!line) return null
  try {
    const parsed = JSON.parse(line)
    return parsed != null && typeof parsed === "object" ? parsed : null
  }
  catch {
    return null
  }
}

async function pasteImageIntoComposer(page: Page): Promise<void> {
  await page.getByPlaceholder("Message the agent…").click()
  await page.evaluate(() => {
    const input = document.querySelector(".acpComposerInput")
    if (!input) throw new Error("No ACP composer input found")
    const dataTransfer = new DataTransfer()
    dataTransfer.items.add(new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], "pasted.png", { type: "image/png" }))
    const event = new Event("paste", { bubbles: true, cancelable: true })
    Object.defineProperty(event, "clipboardData", { value: dataTransfer })
    input.dispatchEvent(event)
  })
}
