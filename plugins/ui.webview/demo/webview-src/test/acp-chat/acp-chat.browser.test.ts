// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {startWebViewMockPreview, type MockWebViewCall, type WebViewMockPreviewServer} from "@jetbrains/intellij-webview-testkit"

type Locator = {
  boundingBox(): Promise<BoundingBox | null>
  click(): Promise<void>
  dispatchEvent(type: string, eventInit?: Record<string, unknown>): Promise<void>
  fill(value: string): Promise<void>
  hover(): Promise<void>
  inputValue(): Promise<string>
  press(key: string): Promise<void>
}

type BoundingBox = {
  x: number
  y: number
  width: number
  height: number
}

type Mouse = {
  down(): Promise<void>
  move(x: number, y: number): Promise<void>
  up(): Promise<void>
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
  mouse: Mouse
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
const {expect, test} = await import(playwrightTestPackage) as unknown as PlaywrightTestModule

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

test("explains pasted attachment capabilities before an agent is activated", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await pasteImageIntoComposer(page)
  await expect(page.getByText("Image attachment support can be detected only after an ACP agent is activated.")).toBeVisible()
})

test("shows hardcoded Junie first and opens acp.json from the agent selector", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  const selectorState = await page.evaluate(() => {
    const options = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'))
    const junieOption = options.find(option => option.textContent?.trim() === "Junie")
    const junieIcon = junieOption?.querySelector<HTMLElement>("[src*='acpChatJunie.svg']")
    return {
      firstOptionText: options[0]?.textContent?.trim(),
      lastOptionText: options[options.length - 1]?.textContent?.trim(),
      junieIconTagName: junieIcon?.tagName.toLowerCase(),
      junieIconSrc: junieIcon?.getAttribute("src"),
    }
  })
  expect(selectorState.firstOptionText === "Junie"
    && selectorState.lastOptionText === "Open acp.json"
    && selectorState.junieIconTagName === "jb-icon"
    && selectorState.junieIconSrc?.includes("/__ij-icons/AcpChatIcons/") === true
    && selectorState.junieIconSrc?.endsWith("/webview/views/acp-chat/assets/acpChatJunie.svg") === true).toBe(true)

  await page.getByRole("option", {name: "Junie"}).click()
  await page.waitForFunction(() => {
    return Array.from(document.querySelectorAll<HTMLElement>(".acpAgentSelect [src*='acpChatJunie.svg']"))
      .some(element => element.tagName.toLowerCase() === "jb-icon")
  })

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Open acp.json"}).click()
  const openConfigCalls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/openAcpConfig") ?? []
  })
  expect(openConfigCalls.length).toBeGreaterThan(0)
})

test("keeps the agent selector open after pointer click", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  const trigger = page.locator(".acpAgentSelect")
  await clickCenter(page, trigger)
  await expect(page.getByRole("option", {name: "Mock Agent"})).toBeVisible()
  await expect(page.getByRole("option", {name: "Open acp.json"})).toBeVisible()

  await waitForTwoAnimationFrames(page)
  await expect(page.getByRole("option", {name: "Mock Agent"})).toBeVisible()
  await expect(page.getByRole("option", {name: "Open acp.json"})).toBeVisible()
  const selectorStillOpen = await page.evaluate(() => {
    const trigger = document.querySelector(".acpAgentSelect")
    return trigger?.getAttribute("data-state") === "open"
      && trigger?.getAttribute("aria-expanded") === "true"
  })
  expect(selectorStillOpen).toBe(true)
})

test("keeps config option selectors out of label activation wrappers", async ({page}) => {
  await openPreview(page)
  await startMockAgent(page)

  const configControlsUseNeutralContainers = await page.evaluate(() => {
    return Array.from(document.querySelectorAll<HTMLElement>(".acpControlWithHint"))
      .every(control => control.tagName.toLowerCase() !== "label"
        && control.querySelector("button, input, select, [role='combobox']")?.closest("label") == null)
  })
  expect(configControlsUseNeutralContainers).toBe(true)

  const trigger = page.locator('[data-config-id="mode"] .acpConfigOptionSelect')
  await clickCenter(page, trigger)
  await expect(page.getByRole("option", {name: "Auto"})).toBeVisible()
  await expect(page.getByRole("option", {name: /Code/})).toBeVisible()

  await waitForTwoAnimationFrames(page)
  await expect(page.getByRole("option", {name: "Auto"})).toBeVisible()
  await expect(page.getByRole("option", {name: /Code/})).toBeVisible()
  const selectorStillOpen = await page.evaluate(() => {
    const trigger = document.querySelector('[data-config-id="mode"] .acpConfigOptionSelect')
    return trigger?.getAttribute("data-state") === "open"
      && trigger?.getAttribute("aria-expanded") === "true"
  })
  expect(selectorStillOpen).toBe(true)
})

test("closes the model selector when WebView focus leaves", async ({page}) => {
  await openPreview(page)
  await startMockAgent(page)

  await page.locator('[data-config-id="model"] .acpModelSelectorTrigger').click()
  await expect(page.getByPlaceholder("Search models...")).toBeVisible()

  await page.evaluate(() => window.dispatchEvent(new Event("wvi-focus-leave")))
  await waitForTwoAnimationFrames(page)
  const modelSelectorClosed = await page.evaluate(() => {
    return document.querySelector(".acpModelSelectorContent") == null
      && document.querySelector('[data-config-id="model"] .acpModelSelectorTrigger')?.getAttribute("data-state") !== "open"
  })
  expect(modelSelectorClosed).toBe(true)
})

test("renders ACP chat in a real browser with a mock agent", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()
  await expect(page.getByText("Mock Agent")).toBeVisible()

  await composerInput(page).fill("Hello mock")
  await page.getByRole("button", {name: "Send"}).click()

  await expect(page.getByText("Hello mock", {exact: true})).toBeVisible()
  await expect(page.getByText(/Mock response from AI chat: Hello mock/)).toBeVisible()

  await composerInput(page).fill("streaming probe")
  await page.getByRole("button", {name: "Send"}).click()

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

test("shows env auth as an inline transcript card and authenticates from it", async ({page}) => {
  await openPreview(page)

  await selectAgentByName(page, "Env Auth Agent")
  await expect(page.locator(".acpThreadViewport .acpAuth")).toBeVisible()
  const authCardInline = await page.evaluate(() => {
    const thread = document.querySelector(".acpThreadViewport")
    const auth = document.querySelector(".acpAuth")
    return thread?.contains(auth) === true && document.querySelector(".acpApprovalOverlay .acpAuth") == null
  })
  expect(authCardInline).toBe(true)

  await page.getByPlaceholder("value").fill("env-secret-token")
  await page.getByRole("button", {name: "Authenticate"}).click()
  await expect(page.getByText("Authentication complete", {exact: true})).toBeVisible()

  const envWasPassedToHost = await page.evaluate(() => {
    const calls = (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/startAgent") ?? []
    return calls.some(call => (call.params as any)?.extraEnv?.JUNIE_TOKEN === "env-secret-token")
  })
  expect(envWasPassedToHost).toBe(true)

  const rpcMethods = (await recordedRpcMessages(page)).map(message => message.method)
  expect(rpcMethods.includes("initialize") && rpcMethods.includes("session/new") && rpcMethods.includes("authenticate")).toBe(true)
})

test("handles oauth auth updates from the transcript card", async ({page}) => {
  await openPreview(page)

  await selectAgentByName(page, "OAuth Auth Agent")
  await expect(page.locator(".acpThreadViewport .acpAuth")).toBeVisible()
  await page.getByRole("button", {name: "Authenticate"}).click()
  await expect(page.getByText(/https:\/\/example\.com\/oauth\/device/)).toBeVisible()
  await expect(page.getByText("Authentication complete", {exact: true})).toBeVisible()

  await composerInput(page).fill("oauth auth prompt")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.getByText(/Mock response from AI chat: oauth auth prompt/)).toBeVisible()

  const rpcMethods = (await recordedRpcMessages(page)).map(message => message.method)
  expect(rpcMethods.includes("initialize")
    && rpcMethods.includes("session/new")
    && rpcMethods.includes("authenticate")
    && rpcMethods.includes("session/prompt")).toBe(true)
})

test("retries prompt-time auth in the same visible user turn", async ({page}) => {
  await openPreview(page)
  await startMockAgent(page)

  await composerInput(page).fill("prompt auth probe")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.locator(".acpThreadViewport .acpAuth")).toBeVisible()
  await page.getByPlaceholder("value").fill("prompt-secret-token")
  await page.getByRole("button", {name: "Authenticate"}).click()
  await expect(page.getByText(/Prompt auth retry completed: prompt auth probe/)).toBeVisible()

  const visibleTurnState = await page.evaluate(() => {
    const matchingUserMessages = Array.from(document.querySelectorAll(".acpMsgUser"))
      .filter(element => element.textContent?.trim() === "prompt auth probe")
    return {
      userMessageCount: matchingUserMessages.length,
      authInsideThread: document.querySelector(".acpThreadViewport .acpAuth") != null,
    }
  })
  expect(visibleTurnState.userMessageCount === 1 && visibleTurnState.authInsideThread).toBe(true)

  const rpcMessages = await recordedRpcMessages(page)
  const promptAuthRequests = rpcMessages.filter(message => message.method === "session/prompt"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "prompt auth probe"))
  expect(promptAuthRequests.length === 2).toBe(true)
})

test("updates repeated prompt auth_required in one transcript card", async ({page}) => {
  await openPreview(page)
  await startMockAgent(page)

  await composerInput(page).fill("repeat prompt auth probe")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.locator(".acpThreadViewport .acpAuth")).toBeVisible()
  await page.getByPlaceholder("value").fill("first-prompt-secret")
  await page.getByRole("button", {name: "Authenticate"}).click()

  await page.waitForFunction(() => {
    const authCards = document.querySelectorAll(".acpThreadViewport .acpAuth")
    return authCards.length === 1
      && authCards[0]?.textContent?.includes("Authentication is required before this operation can be performed.") === true
      && authCards[0]?.textContent?.includes("Use repeated prompt token") === true
  })
  const afterFirstAuth = await page.evaluate(() => {
    return {
      authCardCount: document.querySelectorAll(".acpThreadViewport .acpAuth").length,
      userMessageCount: Array.from(document.querySelectorAll(".acpMsgUser"))
        .filter(element => element.textContent?.trim() === "repeat prompt auth probe").length,
    }
  })
  expect(afterFirstAuth.authCardCount === 1 && afterFirstAuth.userMessageCount === 1).toBe(true)

  await page.getByPlaceholder("value").fill("second-prompt-secret")
  await page.getByRole("button", {name: "Authenticate"}).click()
  await expect(page.getByText(/Repeated prompt auth retry completed: repeat prompt auth probe/)).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  const promptRequests = rpcMessages.filter(message => message.method === "session/prompt"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "repeat prompt auth probe"))
  const authenticateRequests = rpcMessages.filter(message => message.method === "authenticate"
    && message.params?.methodId === "repeat-prompt-env")
  expect(promptRequests.length === 3 && authenticateRequests.length === 2).toBe(true)
})

test("shows unsupported auth inline with retry and acp.json actions", async ({page}) => {
  await openPreview(page)

  await selectAgentByName(page, "No Auth Methods Agent")
  await expect(page.locator(".acpThreadViewport .acpAuthUnsupported")).toBeVisible()
  const unsupportedInline = await page.evaluate(() => {
    const thread = document.querySelector(".acpThreadViewport")
    const auth = document.querySelector(".acpAuthUnsupported")
    return thread?.contains(auth) === true && document.querySelector(".acpApprovalOverlay .acpAuth") == null
  })
  expect(unsupportedInline).toBe(true)

  await page.getByRole("button", {name: "Open acp.json"}).click()
  const openConfigCalled = await page.evaluate(() => {
    return ((window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/openAcpConfig") ?? []).length > 0
  })
  expect(openConfigCalled).toBe(true)

  await page.getByRole("button", {name: "Retry"}).click()
  await expect(page.locator(".acpThreadViewport .acpAuthUnsupported")).toBeVisible()
  const sessionNewCount = (await recordedRpcMessages(page)).filter(message => message.method === "session/new").length
  expect(sessionNewCount > 1).toBe(true)
})

test("renders ACP tool calls as compact collapsed cards", async ({ page }) => {
  await openPreview(page)
  await startMockAgent(page)

  await composerInput(page).fill("tool call compact probe")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText("Run compact tool probe", { exact: true })).toBeVisible()

  const collapsedTool = await page.evaluate(() => {
    const tool = Array.from(document.querySelectorAll<HTMLDetailsElement>("details.acpTool"))
      .find(element => element.textContent?.includes("Run compact tool probe"))
    const header = tool?.querySelector<HTMLElement>(".acpToolHeader")
    const icon = tool?.querySelector<SVGElement>(".acpToolIcon svg")
    const status = tool?.querySelector<HTMLElement>(".acpToolStatus")
    const statusIcon = status?.querySelector<SVGElement>("svg")
    const output = tool?.querySelector<HTMLElement>(".acpToolText")
    const iconRect = icon?.getBoundingClientRect()
    const toolRect = tool?.getBoundingClientRect()
    const headerRect = header?.getBoundingClientRect()
    return {
      found: tool != null,
      collapsed: tool?.open === false,
      headerHasTitle: header?.textContent?.includes("Run compact tool probe") === true,
      headerHidesKind: header?.textContent?.toLocaleLowerCase().includes("execute") === false,
      iconSized: iconRect != null && iconRect.width === 16 && iconRect.height === 16,
      statusIconOnly: status != null
        && status.getAttribute("aria-label") === "completed"
        && status.textContent?.trim() === ""
        && statusIcon != null
        && statusIcon.getClientRects().length > 0,
      outputPresent: output != null,
      detailsCollapsed: toolRect != null && headerRect != null && toolRect.height <= headerRect.height + 4,
    }
  })
  expect(collapsedTool.found
    && collapsedTool.collapsed
    && collapsedTool.headerHasTitle
    && collapsedTool.headerHidesKind
    && collapsedTool.iconSized
    && collapsedTool.statusIconOnly
    && collapsedTool.outputPresent
    && collapsedTool.detailsCollapsed).toBe(true)

  await page.getByText("Run compact tool probe", { exact: true }).click()
  const expandedTool = await page.evaluate(() => {
    const tool = Array.from(document.querySelectorAll<HTMLDetailsElement>("details.acpTool"))
      .find(element => element.textContent?.includes("Run compact tool probe"))
    const output = tool?.querySelector<HTMLElement>(".acpToolText")
    return {
      expanded: tool?.open === true,
      detailsVisible: output != null
        && output.getClientRects().length > 0
        && output.textContent?.includes("long compact tool output line 24") === true,
    }
  })
  expect(expandedTool.expanded && expandedTool.detailsVisible).toBe(true)
})

test("renders ACP tool call statuses as icons", async ({ page }) => {
  await openPreview(page)
  await startMockAgent(page)

  await composerInput(page).fill("tool status icons probe")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText("Completed status probe", { exact: true })).toBeVisible()
  await expect(page.getByText("Running status probe", { exact: true })).toBeVisible()
  await expect(page.getByText("Failed status probe", { exact: true })).toBeVisible()

  const statusIcons = await page.evaluate(() => {
    function statusFor(title: string): HTMLElement | null {
      const tool = Array.from(document.querySelectorAll<HTMLElement>(".acpTool"))
        .find(element => element.textContent?.includes(title))
      return tool?.querySelector<HTMLElement>(".acpToolStatus") ?? null
    }

    const completed = statusFor("Completed status probe")
    const running = statusFor("Running status probe")
    const failed = statusFor("Failed status probe")
    const runningIcon = running?.querySelector<SVGElement>(".acpToolStatusSpinner")
    const runningAnimation = runningIcon ? getComputedStyle(runningIcon).animationName : null
    return {
      completedIcon: completed?.getAttribute("aria-label") === "completed"
        && completed.classList.contains("acpToolStatus--completed")
        && completed.textContent?.trim() === ""
        && completed.querySelector("svg") != null,
      runningIcon: running?.getAttribute("aria-label") === "in progress"
        && running.classList.contains("acpToolStatus--in_progress")
        && running.textContent?.trim() === ""
        && runningAnimation === "acpToolStatusSpin",
      failedIcon: failed?.getAttribute("aria-label") === "failed"
        && failed.classList.contains("acpToolStatus--failed")
        && failed.textContent?.trim() === ""
        && failed.querySelector("svg") != null,
    }
  })
  expect(statusIcons.completedIcon && statusIcons.runningIcon && statusIcons.failedIcon).toBe(true)
})

test("keeps ACP tool calls in the assistant event order", async ({ page }) => {
  await openPreview(page)
  await startMockAgent(page)

  await composerInput(page).fill("tool call order probe")
  await page.getByRole("button", { name: "Send" }).click()
  await expect(page.getByText("Before interleaved tool.", { exact: true })).toBeVisible()
  await expect(page.getByText("Run ordered tool probe", { exact: true })).toBeVisible()
  await expect(page.getByText("After interleaved tool.", { exact: true })).toBeVisible()

  const renderedInEventOrder = await page.evaluate(() => {
    const assistantMessage = Array.from(document.querySelectorAll<HTMLElement>(".acpMsgAssistant"))
      .find(message => {
        const text = message.textContent ?? ""
        return text.includes("Before interleaved tool.")
          && text.includes("Run ordered tool probe")
          && text.includes("After interleaved tool.")
      })
    if (!assistantMessage) return false
    const before = Array.from(assistantMessage.querySelectorAll<HTMLElement>(".acpMarkdown"))
      .find(element => element.textContent?.includes("Before interleaved tool."))
    const tool = assistantMessage.querySelector<HTMLElement>(".acpTool")
    const after = Array.from(assistantMessage.querySelectorAll<HTMLElement>(".acpMarkdown"))
      .find(element => element.textContent?.includes("After interleaved tool."))
    return before != null
      && tool != null
      && after != null
      && (before.compareDocumentPosition(tool) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
      && (tool.compareDocumentPosition(after) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
  })
  expect(renderedInEventOrder).toBe(true)
})

test("shows the ACP chat list as a sidebar on wide panels", async ({page}) => {
  await page.setViewportSize({width: 1000, height: 700})
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", {name: "Loaded session one"})).toBeVisible()
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

test("opens the ACP chat list as a drawer on narrow panels", async ({page}) => {
  await page.setViewportSize({width: 620, height: 700})
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

  await page.getByRole("button", {name: "Open chats"}).click()
  await expect(page.getByRole("button", {name: "Loaded session one"})).toBeVisible()
  const drawerOpen = await page.evaluate(() => document.querySelector(".acpChatListOverlay")?.getAttribute("data-open") === "true")
  expect(drawerOpen).toBe(true)
})

test("loads ACP sessions into the assistant-ui thread list and replays selected history", async ({page}) => {
  await page.setViewportSize({width: 1000, height: 700})
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", {name: "Loaded session one"})).toBeVisible()
  await page.getByRole("button", {name: "Loaded session one"}).click()

  await expect(page.getByText("Loaded user request", {exact: true})).toBeVisible()
  await expect(page.getByText("Loaded assistant response", {exact: true})).toBeVisible()
  await expect(page.getByRole("button", {name: "Loaded session renamed"})).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.some(message => message.method === "session/list")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/load" && message.params?.sessionId === "loaded-session-1")).toBe(true)
})

test("starts a new ACP chat from the thread list", async ({page}) => {
  await page.setViewportSize({width: 1000, height: 700})
  await openPreview(page)
  await startMockAgent(page)

  await page.getByRole("button", {name: "Loaded session one"}).click()
  await expect(page.getByText("Loaded user request", {exact: true})).toBeVisible()

  await page.getByRole("button", {name: "New chat"}).click()
  await page.waitForFunction(() => !document.body.textContent?.includes("Loaded user request"))
  await waitForLateMockUpdates(page)
  await page.waitForFunction(() => !document.body.textContent?.includes("Late stale loaded session request"))

  await composerInput(page).fill("new chat probe")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.getByText(/Mock response from AI chat: new chat probe/)).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.filter(message => message.method === "session/new").length === 2).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/close")).toBe(false)
  expect(rpcMessages.some(message => message.method === "session/prompt" && message.params?.sessionId === "mock-session-restarted-1"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "new chat probe"))).toBe(true)
})

test("starts a new ACP chat from the drawer on narrow panels", async ({page}) => {
  await page.setViewportSize({width: 620, height: 700})
  await openPreview(page)
  await startMockAgent(page)

  await page.getByRole("button", {name: "Open chats"}).click()
  await page.getByRole("button", {name: "Loaded session one"}).click()
  await expect(page.getByText("Loaded user request", {exact: true})).toBeVisible()

  await page.getByRole("button", {name: "Open chats"}).click()
  await page.getByRole("button", {name: "New chat"}).click()
  await page.waitForFunction(() => document.querySelector(".acpChatListOverlay")?.getAttribute("data-open") === "false")
  await page.waitForFunction(() => !document.body.textContent?.includes("Loaded user request"))
  await waitForLateMockUpdates(page)
  await page.waitForFunction(() => !document.body.textContent?.includes("Late stale loaded session request"))

  await composerInput(page).fill("new drawer chat probe")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.getByText(/Mock response from AI chat: new drawer chat probe/)).toBeVisible()

  const rpcMessages = await recordedRpcMessages(page)
  expect(rpcMessages.filter(message => message.method === "session/new").length === 2).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/close")).toBe(false)
  expect(rpcMessages.some(message => message.method === "session/prompt" && message.params?.sessionId === "mock-session-restarted-1"
    && Array.isArray(message.params?.prompt)
    && message.params.prompt.some((block: any) => block?.type === "text" && block.text === "new drawer chat probe"))).toBe(true)
})

test("deletes ACP sessions through the assistant-ui thread list", async ({page}) => {
  await page.setViewportSize({width: 1000, height: 700})
  await openPreview(page)
  await startMockAgent(page)

  await expect(page.getByRole("button", {name: "Loaded session two"})).toBeVisible()
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

test("drives ACP composer config controls through the picker", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()

  const controlsLayout = await page.evaluate(() => {
    const composer = document.querySelector(".acpComposer")
    const composerControls = document.querySelector(".acpComposerControls")
    const composerInput = document.querySelector(".acpComposerInput")
    const composerSend = document.querySelector(".acpComposerSend")
    const agentSelector = document.querySelector(".acpAgentSelector")
    const agentIcon = document.querySelector(".acpAgentSelectorIcon")
    const agentJbIcon = document.querySelector(".acpAgentSelectorIcon > *")
    const agentSelect = document.querySelector(".acpAgentSelect")
    const controlIds = ["mode", "model", "effort", "brave_mode", "think_more", "debug_mode"]
    const allControlsInsideComposer = composer != null && controlIds.every(id => {
      const control = document.querySelector(`[data-config-id="${id}"]`)
      return control != null && composer.contains(control)
    })
    const composerRect = composer?.getBoundingClientRect()
    const controlsRect = composerControls?.getBoundingClientRect()
    const inputRect = composerInput?.getBoundingClientRect()
    const sendRect = composerSend?.getBoundingClientRect()
    const agentRect = agentSelector?.getBoundingClientRect()
    return {
      legacyModeHidden: document.querySelector('[data-control-id="legacy-mode"]') == null
        && !document.body.textContent?.includes("No modes"),
      emptySelectHidden: document.querySelector('[data-config-id="empty_selector"]') == null,
      agentTitleReplacedWithIcon: document.querySelector(".acpAgentSelectorLabel") == null
        && agentJbIcon != null
        && agentJbIcon.tagName.toLocaleLowerCase() === "jb-icon"
        && !Array.from(agentSelector?.children ?? []).some(child => child.classList.contains("acpAgentSelectorLabel") && child.textContent?.trim() === "Agent"),
      agentIconWidth: agentIcon ? getComputedStyle(agentIcon).width : null,
      agentIconSvgWidth: agentJbIcon ? getComputedStyle(agentJbIcon).width : null,
      agentIconSrc: agentJbIcon?.getAttribute("src"),
      agentSelectAriaLabel: agentSelect?.getAttribute("aria-label"),
      allControlsInsideComposer,
      controlsBelowInput: inputRect != null && controlsRect != null && controlsRect.top >= inputRect.bottom,
      sendPinnedBottomRight: composerRect != null && sendRect != null
        && Math.abs(composerRect.right - sendRect.right - 5) <= 2
        && Math.abs(composerRect.bottom - sendRect.bottom - 5) <= 2,
      agentSelectorBelowComposer: composerRect != null && agentRect != null && agentRect.top >= composerRect.bottom,
      selectBackedToggleRendered: document.querySelector('[data-config-id="think_more"] .acpConfigSwitch') != null
        && document.querySelector('[data-config-id="think_more"] .acpConfigOptionSelect') == null,
    }
  })
  expect(controlsLayout.legacyModeHidden).toBe(true)
  expect(controlsLayout.emptySelectHidden).toBe(true)
  expect(controlsLayout.agentTitleReplacedWithIcon
    && controlsLayout.agentIconWidth === "16px"
    && controlsLayout.agentIconSvgWidth === "16px"
    && controlsLayout.agentIconSrc?.includes("/__ij-icons/AcpChatIcons/") === true
    && controlsLayout.agentIconSrc?.endsWith("/webview/views/acp-chat/assets/acpChatAgent.svg") === true
    && controlsLayout.agentSelectAriaLabel === "Agent: Mock Agent").toBe(true)
  expect(controlsLayout.allControlsInsideComposer).toBe(true)
  expect(controlsLayout.controlsBelowInput).toBe(true)
  expect(controlsLayout.sendPinnedBottomRight).toBe(true)
  expect(controlsLayout.agentSelectorBelowComposer).toBe(true)
  expect(controlsLayout.selectBackedToggleRendered).toBe(true)

  await expect(page.locator('[data-config-id="mode"] .acpConfigOptionSelect')).toBeVisible()
  await page.waitForFunction(() => document.querySelector('[data-config-id="mode"] .acpConfigOptionSelect')?.textContent?.includes("Auto") === true)
  await expect(page.locator('[data-config-id="model"] .acpModelSelectorTrigger')).toBeVisible()
  await page.waitForFunction(() => document.querySelector('[data-config-id="model"] .acpModelSelectorTrigger')?.textContent?.includes("Gemini 2.5 Flash") === true)
  await expect(page.locator('[data-config-id="effort"] .acpConfigOptionSelect')).toBeVisible()
  await page.waitForFunction(() => document.querySelector('[data-config-id="effort"] .acpConfigOptionSelect')?.textContent?.includes("Medium effort") === true)

  const controlPresentation = await page.evaluate(() => {
    const modeSelect = document.querySelector('[data-config-id="mode"] .acpConfigOptionSelect')
    const modeControl = document.querySelector('[data-config-id="mode"]')
    const modelTrigger = document.querySelector('[data-config-id="model"] .acpModelSelectorTrigger')
    const modelControl = document.querySelector('[data-config-id="model"]')
    const effortControl = document.querySelector('[data-config-id="effort"]')
    const controlIcon = document.querySelector('[data-config-id="model"] .acpControlIcon')
    const controlJbIcon = document.querySelector('[data-config-id="model"] .acpControlIcon > *')
    const effortJbIcon = document.querySelector('[data-config-id="effort"] .acpControlIcon > *')
    const thinkMoreJbIcon = document.querySelector('[data-config-id="think_more"] .acpControlIcon > *')
    return {
      textLabelsHidden: document.querySelector(".acpModelPickerLabel, .acpConfigToggleLabel") == null,
      sessionModeHint: document.querySelector('[data-config-id="mode"]')?.getAttribute("data-hint"),
      modelHint: document.querySelector('[data-config-id="model"]')?.getAttribute("data-hint"),
      effortHint: document.querySelector('[data-config-id="effort"]')?.getAttribute("data-hint"),
      braveHint: document.querySelector('[data-config-id="brave_mode"]')?.getAttribute("data-hint"),
      thinkMoreHint: document.querySelector('[data-config-id="think_more"]')?.getAttribute("data-hint"),
      debugHint: document.querySelector('[data-config-id="debug_mode"]')?.getAttribute("data-hint"),
      modeAriaLabel: document.querySelector('[data-config-id="mode"] .acpConfigOptionSelect')?.getAttribute("aria-label"),
      modelAriaLabel: document.querySelector('[data-config-id="model"] .acpModelSelectorTrigger')?.getAttribute("aria-label"),
      effortAriaLabel: document.querySelector('[data-config-id="effort"] .acpConfigOptionSelect')?.getAttribute("aria-label"),
      modeControlBorderWidth: modeControl ? getComputedStyle(modeControl).borderTopWidth : null,
      modelControlBorderWidth: modelControl ? getComputedStyle(modelControl).borderTopWidth : null,
      effortControlBorderWidth: effortControl ? getComputedStyle(effortControl).borderTopWidth : null,
      modeSelectBorderStyle: modeSelect ? getComputedStyle(modeSelect).borderTopStyle : null,
      modelTriggerBorderStyle: modelTrigger ? getComputedStyle(modelTrigger).borderTopStyle : null,
      modeSelectMinWidth: modeSelect ? getComputedStyle(modeSelect).minWidth : null,
      modelTriggerMinWidth: modelTrigger ? getComputedStyle(modelTrigger).minWidth : null,
      controlIconWidth: controlIcon ? getComputedStyle(controlIcon).width : null,
      controlIconSvgWidth: controlJbIcon ? getComputedStyle(controlJbIcon).width : null,
      controlIconTagName: controlJbIcon?.tagName.toLocaleLowerCase(),
      modelIcon: controlJbIcon?.getAttribute("src"),
      effortIcon: effortJbIcon?.getAttribute("src"),
      thinkMoreIcon: thinkMoreJbIcon?.getAttribute("src"),
    }
  })
  expect(controlPresentation.textLabelsHidden
    && controlPresentation.sessionModeHint === "Session mode"
    && controlPresentation.modelHint === "Model"
    && controlPresentation.effortHint === "Effort"
    && controlPresentation.braveHint === "Brave Mode"
    && controlPresentation.thinkMoreHint === "Think More"
    && controlPresentation.debugHint === "Debug Mode"
    && controlPresentation.modeAriaLabel === "Session mode: Auto"
    && controlPresentation.modelAriaLabel === "Model: Gemini 2.5 Flash"
    && controlPresentation.effortAriaLabel === "Effort: Medium effort"
    && controlPresentation.modeControlBorderWidth === "1px"
    && controlPresentation.modelControlBorderWidth === "1px"
    && controlPresentation.effortControlBorderWidth === "1px"
    && controlPresentation.modeSelectBorderStyle === "none"
    && controlPresentation.modelTriggerBorderStyle === "none"
    && controlPresentation.modeSelectMinWidth === "0px"
    && controlPresentation.modelTriggerMinWidth === "0px"
    && controlPresentation.controlIconWidth === "16px"
    && controlPresentation.controlIconSvgWidth === "16px"
    && controlPresentation.controlIconTagName === "jb-icon"
    && controlPresentation.modelIcon?.includes("/__ij-icons/AcpChatIcons/") === true
    && controlPresentation.modelIcon?.endsWith("/webview/views/acp-chat/assets/acpChatProcessor.svg") === true
    && controlPresentation.effortIcon?.endsWith("/webview/views/acp-chat/assets/acpChatEffort.svg") === true
    && controlPresentation.thinkMoreIcon?.endsWith("/webview/views/acp-chat/assets/acpChatBrain.svg") === true).toBe(true)

  const iconResourcesLoad = await page.evaluate(async () => {
    const iconSources = [
      document.querySelector(".acpAgentSelectorIcon > *")?.getAttribute("src"),
      document.querySelector('[data-config-id="model"] .acpControlIcon > *')?.getAttribute("src"),
      document.querySelector('[data-config-id="effort"] .acpControlIcon > *')?.getAttribute("src"),
      document.querySelector('[data-config-id="think_more"] .acpControlIcon > *')?.getAttribute("src"),
    ].filter((src): src is string => typeof src === "string" && src.length > 0)
    if (iconSources.length !== 4) return false
    const responses = await Promise.all(iconSources.map(src => fetch(src)))
    return responses.every(response => response.ok && response.headers.get("content-type")?.includes("image/svg+xml") === true)
  })
  expect(iconResourcesLoad).toBe(true)

  await page.locator('[data-config-id="model"] .acpModelSelectorTrigger').hover()
  const tooltipAfterSelectorHover = await page.evaluate(async () => {
    await new Promise(resolve => setTimeout(resolve, 350))
    return document.querySelector(".acpControlTooltip") != null
  })
  expect(tooltipAfterSelectorHover).toBe(false)

  await page.locator('[data-config-id="brave_mode"] .acpControlIcon').hover()
  await page.waitForSelector(".acpControlTooltip")
  await expect(page.getByText("Brave Mode", {exact: true})).toBeVisible()

  await page.locator('[data-config-id="mode"] .acpConfigOptionSelect').click()
  await page.getByRole("option", {name: /Code/}).click()
  await page.waitForFunction(() => document.querySelector('[data-config-id="mode"] .acpConfigOptionSelect')?.textContent?.includes("Code") === true)

  await page.locator('[data-config-id="model"] .acpModelSelectorTrigger').click()
  await expect(page.getByPlaceholder("Search models...")).toBeVisible()
  await page.getByPlaceholder("Search models...").fill("pro")
  await page.getByRole("option", {name: /Gemini 2.5 Pro/}).click()
  await expect(page.getByText("Gemini 2.5 Pro", {exact: true})).toBeVisible()

  await page.locator('[data-config-id="effort"] .acpConfigOptionSelect').click()
  await page.getByRole("option", {name: /High effort/}).click()
  await page.waitForFunction(() => document.querySelector('[data-config-id="effort"] .acpConfigOptionSelect')?.textContent?.includes("High effort") === true)

  await page.getByRole("switch", {name: "Brave Mode"}).click()
  await page.waitForFunction(() => document.querySelector('[data-config-id="brave_mode"] .acpConfigSwitch')?.getAttribute("data-state") === "checked")
  await page.getByRole("switch", {name: "Think More"}).click()
  await page.waitForFunction(() => document.querySelector('[data-config-id="think_more"] .acpConfigSwitch')?.getAttribute("data-state") === "checked")
  await page.getByRole("switch", {name: "Debug Mode"}).click()
  await page.waitForFunction(() => document.querySelector('[data-config-id="debug_mode"] .acpConfigSwitch')?.getAttribute("data-state") === "checked")

  await page.setViewportSize({width: 620, height: 700})
  const controlsStayInOneRow = await page.evaluate(() => {
    const composerRect = document.querySelector(".acpComposer")?.getBoundingClientRect()
    const picker = document.querySelector(".acpModelPicker")
    if (!composerRect || !picker) return false
    const pickerStyle = getComputedStyle(picker)
    const controls = Array.from(document.querySelectorAll<HTMLElement>(".acpComposerControls .acpControlWithHint"))
    const visibleControls = controls.filter(control => getComputedStyle(control).display !== "none")
    const hiddenControlCount = controls.length - visibleControls.length
    const rowTop = visibleControls[0]?.getBoundingClientRect().top
    return pickerStyle.flexWrap === "nowrap" && hiddenControlCount > 0 && visibleControls.every(control => {
      const rect = control.getBoundingClientRect()
      return Math.abs(rect.top - (rowTop ?? rect.top)) <= 1
        && rect.left >= composerRect.left
        && rect.right <= composerRect.right
    })
  })
  expect(controlsStayInOneRow).toBe(true)

  const calls = await page.evaluate(() => {
    return (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/sendStdin") ?? []
  })
  const rpcMessages = calls
    .map((call: MockWebViewCall) => parseMockRpcLine(call.params))
    .filter((message: JsonRpcMessage | null): message is JsonRpcMessage => message != null)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "mode"
    && message.params?.value === "code")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "model"
    && message.params?.value === "gemini-2.5-pro")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "effort"
    && message.params?.value === "high")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "brave_mode"
    && message.params?.type === "boolean"
    && message.params?.value === true)).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "think_more"
    && message.params?.type !== "boolean"
    && message.params?.value === "on")).toBe(true)
  expect(rpcMessages.some(message => message.method === "session/set_config_option"
    && message.params?.sessionId === "mock-session"
    && message.params?.configId === "debug_mode"
    && message.params?.type === "boolean"
    && message.params?.value === true)).toBe(true)
})

test("renders rich assistant markdown through the chat message renderer", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()
  await composerInput(page).fill("markdown feature probe")
  await page.getByRole("button", {name: "Send"}).click()

  await expect(page.getByText("Markdown feature matrix", {exact: true})).toBeVisible()
  await expect(page.getByText("GFM table", {exact: true})).toBeVisible()
  await expect(page.getByText("Render task lists", {exact: true})).toBeVisible()
  await expect(page.getByText("Raw HTML details", {exact: true})).toBeVisible()
  await page.waitForFunction(() => document.querySelector(".acpMarkdown .footnotes")?.textContent?.includes("Footnote content from ACP chat markdown.") === true)
  await page.waitForSelector(".acpMarkdown .katex")
  await page.waitForSelector(".acpMermaidBlock svg")
  await verifyAcpMermaidViewport(page)
  await expect(page.getByRole("button", {name: "views/acp-chat/src/components/MarkdownRenderer.tsx:47"})).toBeVisible()
  await expect(page.getByRole("button", {name: "community/plugins/ui.webview/demo/webview-src/views/acp-chat/src/bridge/webviewApi.ts#L1"})).toBeVisible()

  const markdownRenderedSafely = await page.evaluate(() => {
    const markdown = document.querySelector(".acpMsgAssistant .acpMarkdown")
    const link = markdown?.querySelector<HTMLAnchorElement>('a[href="https://example.com"]')
    const pathLinks = Array.from(markdown?.querySelectorAll<HTMLButtonElement>(".acpMarkdownPathLink") ?? [])
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
      && pathLinks.some(button => button.textContent === "views/acp-chat/src/components/MarkdownRenderer.tsx:47")
      && pathLinks.some(button => button.textContent === "community/plugins/ui.webview/demo/webview-src/views/acp-chat/src/bridge/webviewApi.ts#L1")
      && !pathLinks.some(button => button.textContent === "missing/Nope.kt")
      && !pathLinks.some(button => button.textContent === "src/Mermaid.kt")
      && unsafeImage != null
      && !unsafeImage.hasAttribute("onerror")
      && markdown?.querySelector("script") == null
      && (window as any).__ACP_MARKDOWN_SCRIPT_EXECUTED__ !== true
      && (window as any).__ACP_MARKDOWN_ONERROR_EXECUTED__ !== true
  })
  expect(markdownRenderedSafely).toBe(true)

  await page.getByRole("button", {name: "views/acp-chat/src/components/MarkdownRenderer.tsx:47"}).click()
  const navigatePathLinkCalled = await page.evaluate(() => {
    const calls = (window as MockWindow).__WVI_MOCK__?.calls.byMethod("acp.bridge/navigatePathLink") ?? []
    return calls.some(call => {
      const params = call.params as { rawPath?: unknown; clientX?: unknown; clientY?: unknown }
      return params.rawPath === "views/acp-chat/src/components/MarkdownRenderer.tsx:47"
        && typeof params.clientX === "number"
        && typeof params.clientY === "number"
    })
  })
  expect(navigatePathLinkCalled).toBe(true)
})

test("sends pasted image resources as ACP prompt content blocks", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()
  await pasteImageIntoComposer(page)
  await expect(page.getByText("pasted.png", {exact: true})).toBeVisible()

  await composerInput(page).fill("attachment probe")
  await page.getByRole("button", {name: "Send"}).click()
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

test("inserts ACP slash commands into the composer and sends them as prompt prefixes", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()

  const input = composerInput(page)
  await input.fill("/")
  await expect(page.getByRole("option", {name: /\/summarize/})).toBeVisible()
  await expect(page.getByRole("option", {name: /\/explain/})).toBeVisible()

  await input.fill("/sum")
  await page.getByRole("option", {name: /\/summarize/}).click()
  await page.waitForFunction(() => (document.querySelector(".acpComposerInput") as HTMLTextAreaElement | null)?.value === "/summarize ")
  const insertedCommand = await input.inputValue()
  expect(insertedCommand === "/summarize ").toBe(true)

  await input.fill(`${insertedCommand}this file`)
  await page.getByRole("button", {name: "Send"}).click()
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

test("quotes selected assistant text and sends quoted context before the prompt", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()

  await composerInput(page).fill("quote source")
  await page.getByRole("button", {name: "Send"}).click()
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
        document.dispatchEvent(new MouseEvent("mouseup", {bubbles: true}))
        return true
      }
      node = walker.nextNode()
    }
    return false
  })
  expect(selected).toBe(true)
  await expect(page.getByRole("button", {name: "Quote"})).toBeVisible()

  await page.getByRole("button", {name: "Quote"}).click()
  await page.waitForSelector(".acpComposerQuote")
  const composerQuoteVisible = await page.evaluate(() => document.querySelector(".acpComposerQuoteText")?.textContent === "Mock response from AI chat")
  expect(composerQuoteVisible).toBe(true)

  await composerInput(page).fill("quote follow-up")
  await page.getByRole("button", {name: "Send"}).click()
  await expect(page.getByText("quote follow-up", {exact: true})).toBeVisible()
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

test("keeps the keyboard-highlighted slash command visible while navigating", async ({page}) => {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)

  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name: "Mock Agent"}).click()

  const input = composerInput(page)
  await input.fill("/")
  await expect(page.getByRole("option", {name: /\/summarize/})).toBeVisible()
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

async function verifyAcpMermaidViewport(page: Page): Promise<void> {
  await expect(page.getByRole("button", {name: "Zoom in diagram"})).toBeVisible()
  expect(await acpMermaidToolbarIconsLoaded(page)).toBe(true)
  const resizeEnabled = await page.evaluate(() => {
    const block = document.querySelector(".acpMermaidBlock--interactive")
    return block != null && getComputedStyle(block).resize === "vertical"
  })
  expect(resizeEnabled).toBe(true)
  expect(await acpMermaidSvgFillsViewport(page)).toBe(true)

  await page.getByRole("button", {name: "Zoom in diagram"}).click()
  await page.waitForFunction(() => {
    const transform = document.querySelector(".acpMermaidPanZoom")?.getAttribute("transform") ?? ""
    return transform.includes("scale(") && !transform.endsWith("scale(1)")
  })

  await page.getByRole("button", {name: "Reset diagram zoom"}).click()
  await page.waitForFunction(() => (document.querySelector(".acpMermaidPanZoom")?.getAttribute("transform") ?? "") === "translate(0,0) scale(1)")

  expect(await acpMermaidViewBoxContainsContent(page)).toBe(true)

  const transformBeforeWheel = await acpMermaidTransform(page)
  await page.locator(".acpMermaidViewport svg").dispatchEvent("wheel", {
    deltaY: -120,
    clientX: 80,
    clientY: 80,
    bubbles: true,
    cancelable: true
  })
  expect((await acpMermaidTransform(page)) === transformBeforeWheel).toBe(true)

  const svg = page.locator(".acpMermaidViewport svg")
  const box = await svg.boundingBox()
  if (!box) throw new Error("ACP Mermaid SVG does not have a rendered bounding box")
  const transformBeforeDrag = await acpMermaidTransform(page)
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2 + 28)
  await page.mouse.up()
  await page.waitForFunction(() => {
    const transform = document.querySelector(".acpMermaidPanZoom")?.getAttribute("transform") ?? ""
    return transform.includes("translate(") && !transform.startsWith("translate(0,0)")
  })
  expect((await acpMermaidTransform(page)) !== transformBeforeDrag).toBe(true)
}

function acpMermaidTransform(page: Page): Promise<string> {
  return page.evaluate(() => document.querySelector(".acpMermaidPanZoom")?.getAttribute("transform") ?? "")
}

function acpMermaidSvgFillsViewport(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const viewport = document.querySelector(".acpMermaidViewport")
    const svg = document.querySelector(".acpMermaidViewport svg")
    if (!viewport || !svg) return false
    return Math.abs(svg.getBoundingClientRect().width - viewport.getBoundingClientRect().width) <= 1
  })
}

function acpMermaidToolbarIconsLoaded(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const icons = Array.from(document.querySelectorAll<HTMLImageElement>(".acpMermaidToolbar img"))
    return icons.length === 3 && icons.every(icon => icon.complete && icon.naturalWidth > 0 && icon.naturalHeight > 0)
  })
}

function acpMermaidViewBoxContainsContent(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const svg = document.querySelector<SVGSVGElement>(".acpMermaidViewport svg")
    const content = document.querySelector<SVGGraphicsElement>(".acpMermaidPanZoom")
    if (!svg || !content) return false
    const viewBox = svg.viewBox.baseVal
    const box = content.getBBox()
    return viewBox.x <= box.x
      && viewBox.y <= box.y
      && viewBox.x + viewBox.width >= box.x + box.width
      && viewBox.y + viewBox.height >= box.y + box.height
  })
}

async function openPreview(page: Page): Promise<void> {
  if (!preview) {
    throw new Error("ACP chat mock preview server was not started")
  }
  await page.goto(preview.url)
}

async function clickCenter(page: Page, locator: Locator): Promise<void> {
  const box = await locator.boundingBox()
  if (!box) throw new Error("Cannot click an element without a rendered bounding box")
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.up()
}

async function waitForTwoAnimationFrames(page: Page): Promise<void> {
  await page.evaluate(() => new Promise<void>(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }))
}

async function startMockAgent(page: Page): Promise<void> {
  await selectAgentByName(page, "Mock Agent")
  await expect(page.getByText("Mock Agent", {exact: true})).toBeVisible()
}

async function selectAgentByName(page: Page, name: string): Promise<void> {
  await page.locator(".acpAgentSelect").click()
  await page.getByRole("option", {name, exact: true}).click()
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
    dataTransfer.items.add(new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], "pasted.png", {type: "image/png"}))
    const event = new Event("paste", {bubbles: true, cancelable: true})
    Object.defineProperty(event, "clipboardData", {value: dataTransfer})
    input.dispatchEvent(event)
  })
}

function composerInput(page: Page): Locator {
  return page.locator(".acpComposerInput")
}

async function waitForLateMockUpdates(page: Page): Promise<void> {
  await page.evaluate(() => new Promise(resolve => setTimeout(resolve, 120)))
}
