// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { startWebViewMockPreview, type MockWebViewCall, type WebViewMockPreviewServer } from "@jetbrains/intellij-webview-testkit"

type Locator = {
  click(): Promise<void>
  fill(value: string): Promise<void>
  inputValue(): Promise<string>
  press(key: string): Promise<void>
}

type Page = {
  goto(url: string): Promise<void>
  getByRole(role: string, options?: { name?: string | RegExp; exact?: boolean }): Locator
  getByPlaceholder(text: string): Locator
  getByText(text: string | RegExp, options?: { exact?: boolean }): Locator
  locator(selector: string): Locator
  evaluate<Result>(pageFunction: () => Result | Promise<Result>): Promise<Result>
  setViewportSize(size: { width: number; height: number }): Promise<void>
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

test("explains pasted attachment capabilities before an agent is activated", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await pasteImageIntoComposer(page)
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

  await composerInput(page).fill("Hello mock")
  await page.getByRole("button", { name: "Send" }).click()

  await expect(page.getByText("Hello mock", { exact: true })).toBeVisible()
  await expect(page.getByText(/Mock response from AI chat: Hello mock/)).toBeVisible()

  await composerInput(page).fill("streaming probe")
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

test("shows the ACP chat list as a sidebar on wide panels", async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 700 })
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", { name: "Loaded session one" })).toBeVisible()
  const layout = await page.evaluate(() => {
    const sidebar = document.querySelector(".acpChatListSidebar")
    const trigger = document.querySelector(".acpChatListDrawerTrigger")
    return {
      sidebarVisible: sidebar != null && getComputedStyle(sidebar).display !== "none" && sidebar.getBoundingClientRect().width >= 239,
      triggerHidden: trigger != null && getComputedStyle(trigger).display === "none",
    }
  })
  expect(layout.sidebarVisible).toBe(true)
  expect(layout.triggerHidden).toBe(true)
})

test("opens the ACP chat list as a drawer on narrow panels", async ({ page }) => {
  await page.setViewportSize({ width: 620, height: 700 })
  await openPreview(page)
  await startMockAgent(page)

  const compactLayout = await page.evaluate(() => {
    const sidebar = document.querySelector(".acpChatListSidebar")
    const trigger = document.querySelector(".acpChatListDrawerTrigger")
    return {
      sidebarHidden: sidebar != null && getComputedStyle(sidebar).display === "none",
      triggerVisible: trigger != null && getComputedStyle(trigger).display !== "none",
    }
  })
  expect(compactLayout.sidebarHidden).toBe(true)
  expect(compactLayout.triggerVisible).toBe(true)

  await page.getByRole("button", { name: "Open chats" }).click()
  await expect(page.getByRole("button", { name: "Loaded session one" })).toBeVisible()
  const drawerOpen = await page.evaluate(() => document.querySelector(".acpChatListOverlay")?.getAttribute("data-open") === "true")
  expect(drawerOpen).toBe(true)
})

test("loads ACP sessions into the assistant-ui thread list and replays selected history", async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 700 })
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", { name: "Loaded session one" })).toBeVisible()
  await page.getByRole("button", { name: "Loaded session one" }).click()

  await expect(page.getByText("Loaded user request", { exact: true })).toBeVisible()
  await expect(page.getByText("Loaded assistant response", { exact: true })).toBeVisible()
  await expect(page.getByRole("button", { name: "Loaded session renamed" })).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.some(message => message.method === "session/list")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/load" && message.params?.sessionId === "loaded-session-1")).toBe(true)
})

test("starts a new ACP chat from the thread list", async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 700 })
  await openPreview(page)
  await startMockAgent(page)

  await page.getByRole("button", { name: "Loaded session one" }).click()
  await expect(page.getByText("Loaded user request", { exact: true })).toBeVisible()

  await page.locator(".acpChatListSidebar .acpChatListNew").click()
  await page.waitForFunction(() => !document.body.textContent?.includes("Loaded user request"))

  await composerInput(page).fill("new chat probe")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText(/Mock response from AI chat: new chat probe/)).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.filter(message => message.method === "session/new").length).toBeGreaterThan(1)
  expect(rpcMessages.some(message => message.method === "session/prompt" && message.params?.sessionId === "mock-session-new-1"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "new chat probe"))).toBe(true)
})

test("deletes ACP sessions through the assistant-ui thread list", async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 700 })
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", { name: "Loaded session two" })).toBeVisible()
  await page.evaluate(() => {
    const items = Array.from(document.querySelectorAll(".acpChatListItem"))
    const item = items.find(candidate => candidate.textContent?.includes("Loaded session two"))
    const button = item?.querySelector<HTMLButtonElement>(".acpChatListDelete")
    if (!button) throw new Error("No delete button found for Loaded session two")
    button.click()
  })
  await page.waitForFunction(() => !document.body.textContent?.includes("Loaded session two"))

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.some(message => message.method === "session/delete" && message.params?.sessionId === "loaded-session-2")).toBe(true)
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

test("renders rich assistant markdown through the chat message renderer", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await composerInput(page).fill("markdown feature probe")
  await page.getByRole("button", { name: "Send" }).click()

  await expect(page.getByText("Markdown feature matrix", { exact: true })).toBeVisible()
  await expect(page.getByText("GFM table", { exact: true })).toBeVisible()
  await expect(page.getByText("Render task lists", { exact: true })).toBeVisible()
  await expect(page.getByText("Raw HTML details", { exact: true })).toBeVisible()
  await page.waitForFunction(() => document.querySelector(".acpMarkdown .footnotes")?.textContent?.includes("Footnote content from ACP chat markdown.") === true)
  await page.waitForSelector(".acpMarkdown .katex")
  await page.waitForSelector(".acpMermaidBlock svg")

  const markdownRenderedSafely = await page.evaluate(() => {
    const markdown = document.querySelector(".acpMsgAssistant .acpMarkdown")
    const link = markdown?.querySelector<HTMLAnchorElement>('a[href="https://example.com"]')
    const unsafeImage = markdown?.querySelector<HTMLImageElement>('img[alt="Unsafe image"]')
    return Boolean(markdown?.querySelector("table"))
      && Boolean(markdown?.querySelector('input[type="checkbox"][checked][disabled]'))
      && Boolean(markdown?.querySelector("pre code .hljs-keyword"))
      && Boolean(markdown?.querySelector("kbd"))
      && Boolean(markdown?.querySelector("mark"))
      && Boolean(markdown?.querySelector("sub"))
      && Boolean(markdown?.querySelector("sup"))
      && link?.target === "_blank"
      && link?.rel === "noreferrer"
      && unsafeImage != null
      && !unsafeImage.hasAttribute("onerror")
      && markdown?.querySelector("script") == null
      && (window as any).__ACP_MARKDOWN_SCRIPT_EXECUTED__ !== true
      && (window as any).__ACP_MARKDOWN_ONERROR_EXECUTED__ !== true
  })
  expect(markdownRenderedSafely).toBe(true)
})

test("sends pasted image resources as ACP prompt content blocks", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await pasteImageIntoComposer(page)
  await expect(page.getByText("pasted.png", { exact: true })).toBeVisible()

  await composerInput(page).fill("attachment probe")
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
  expect(hasImageBlock).toBe(true)
})

test("inserts ACP slash commands into the composer and sends them as prompt prefixes", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()

  const input = composerInput(page)
  await input.fill("/")
  await expect(page.getByRole("option", { name: /\/summarize/ })).toBeVisible()
  await expect(page.getByRole("option", { name: /\/explain/ })).toBeVisible()

  await input.fill("/sum")
  await page.getByRole("option", { name: /\/summarize/ }).click()
  await page.waitForFunction(() => (document.querySelector(".acpComposerInput") as HTMLTextAreaElement | null)?.value === "/summarize ")
  const insertedCommand = await input.inputValue()
  expect(insertedCommand === "/summarize ").toBe(true)

  await input.fill(`${insertedCommand}this file`)
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText(/Mock response from AI chat: \/summarize this file/)).toBeVisible()

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  const rpcMessages = calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
  expect(rpcMessages.some(message => message.method === "session/prompt"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "/summarize this file"))).toBe(true)
})

test("quotes selected assistant text and sends quoted context before the prompt", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()

  await composerInput(page).fill("quote source")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText(/Mock response from AI chat: quote source/)).toBeVisible()

  const selected = await page.evaluate(() => {
    const selectedText = "Mock response from AI chat"
    const markdown = document.querySelector(".acpMsgAssistant .acpMarkdown")
    if (!markdown) return false
    const walker = document.createTreeWalker(markdown, NodeFilter.SHOW_TEXT)
    let node = walker.nextNode()
    while (node) {
      const text = node.nodeValue ?? ""
      const index = text.indexOf(selectedText)
      if (index >= 0) {
        const range = document.createRange()
        range.setStart(node, index)
        range.setEnd(node, index + selectedText.length)
        const selection = window.getSelection()
        selection?.removeAllRanges()
        selection?.addRange(range)
        document.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }))
        return true
      }
      node = walker.nextNode()
    }
    return false
  })
  expect(selected).toBe(true)
  await expect(page.getByRole("button", { name: "Quote" })).toBeVisible()

  await page.getByRole("button", { name: "Quote" }).click()
  await page.waitForSelector(".acpComposerQuote")
  const composerQuoteVisible = await page.evaluate(() => document.querySelector(".acpComposerQuoteText")?.textContent === "Mock response from AI chat")
  expect(composerQuoteVisible).toBe(true)

  await composerInput(page).fill("quote follow-up")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText("quote follow-up", { exact: true })).toBeVisible()
  await page.waitForSelector(".acpMsgUser .acpMessageQuote")
  const sentQuoteVisible = await page.evaluate(() => document.querySelector(".acpMsgUser .acpMessageQuote")?.textContent === "Mock response from AI chat")
  expect(sentQuoteVisible).toBe(true)

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  const rpcMessages = calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
  const promptRequest = rpcMessages.find(message => message.method === "session/prompt"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "quote follow-up"))
  if (!promptRequest) {
    throw new Error("No quote follow-up prompt request was recorded")
  }
  const prompt = promptRequest.params.prompt
  const quoteBlockIndex: number = prompt.findIndex((block: any) => block?.type === "text"
    && typeof block.text === "string"
    && block.text.includes("Quoted context from message assistant-")
    && block.text.includes("> Mock response from AI chat"))
  const promptBlockIndex: number = prompt.findIndex((block: any) => block?.type === "text" && block.text === "quote follow-up")
  expect(quoteBlockIndex >= 0 && promptBlockIndex > quoteBlockIndex).toBe(true)
})

test("keeps the keyboard-highlighted slash command visible while navigating", async ({ page }) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()

  const input = composerInput(page)
  await input.fill("/")
  await expect(page.getByRole("option", { name: /\/summarize/ })).toBeVisible()
  for (let i = 0; i < 11; i++) {
    await input.press("ArrowDown")
  }
  await page.waitForFunction(() => document.querySelector(".acpSlashCommandItem[data-highlighted]")?.textContent?.includes("/commit") === true)

  const highlightedVisible = await page.evaluate(() => {
    const container = document.querySelector(".acpSlashCommandItems")
    const highlighted = document.querySelector(".acpSlashCommandItem[data-highlighted]")
    if (!container || !highlighted) return false
    const containerRect = container.getBoundingClientRect()
    const highlightedRect = highlighted.getBoundingClientRect()
    return highlightedRect.top >= containerRect.top && highlightedRect.bottom <= containerRect.bottom
  })
  expect(highlightedVisible).toBe(true)
})

async function openPreview(page: Page): Promise<void> {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)
}

async function startMockAgent(page: Page): Promise<void> {
  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", { name: "Mock Agent" }).click()
  await expect(page.getByText("Mock Agent")).toBeVisible()
}

async function recordedRpcMessages(page: Page): Promise<JsonRpcMessage[]> {
  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  return calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
}

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
  await composerInput(page).click()
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

function composerInput(page: Page): Locator {
  return page.locator(".acpComposerInput")
}
