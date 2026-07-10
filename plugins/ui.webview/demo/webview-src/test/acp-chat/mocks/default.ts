// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId } from "@jetbrains/intellij-webview"
import { defineWebViewMock, type MockCallable } from "@jetbrains/intellij-webview-testkit"
import type { AcpBridgeHostApi, AcpBridgePageApi } from "../../../views/acp-chat/src/bridge/webviewApi"

const acpBridgeHostApiId = apiId<AcpBridgeHostApi>()("acp.bridge")
const acpBridgePageApiId = apiId<AcpBridgePageApi>()("acp.bridge")
const sessionId = "mock-session"
const loadedSessionId = "loaded-session-1"
const deletedSessionId = "loaded-session-2"
const mockCwd = "/mock/project"
const streamingProbePrompt = "streaming probe"
const markdownFeatureProbePrompt = "markdown feature probe"
const promptAuthProbePrompt = "prompt auth probe"
const repeatedPromptAuthProbePrompt = "repeat prompt auth probe"
const envAuthVar = "JUNIE_TOKEN"
const promptAuthVar = "PROMPT_AUTH_TOKEN"
const repeatedPromptAuthVar = "REPEAT_PROMPT_AUTH_TOKEN"
const toolCallCompactProbePrompt = "tool call compact probe"
const toolCallOrderProbePrompt = "tool call order probe"
const toolStatusIconsProbePrompt = "tool status icons probe"
const modeConfigId = "mode"
const modelConfigId = "model"
const effortConfigId = "effort"
const braveModeConfigId = "brave_mode"
const thinkMoreConfigId = "think_more"
const debugModeConfigId = "debug_mode"
const defaultSessionModeValue = "auto"
const defaultModelValue = "gemini-2.5-flash"
const defaultEffortValue = "medium"
const defaultBraveModeValue = false
const defaultThinkMoreValue = "off"
const defaultDebugModeValue = false

type JsonRpcId = string | number | null

interface JsonRpcMessage {
  jsonrpc?: string
  id?: JsonRpcId
  method?: string
  params?: any
}

export default defineWebViewMock((context) => {
  let pageApi: MockCallable<AcpBridgePageApi> | null = null
  const pendingStdout: unknown[] = []
  let currentSessionModeValue = defaultSessionModeValue
  let currentModelValue = defaultModelValue
  let currentEffortValue = defaultEffortValue
  let currentBraveModeValue = defaultBraveModeValue
  let currentThinkMoreValue = defaultThinkMoreValue
  let currentDebugModeValue = defaultDebugModeValue
  let activeSessionId = sessionId
  let activeAgentId = "mock-agent"
  let envSessionAuthenticated = false
  let oauthAuthenticated = false
  let promptAuthAuthenticated = false
  let repeatedPromptAuthCount = 0
  let listedSessions = defaultListedSessions()
  let newSessionCounter = 0
  let restartCounter = 0

  context.page.whenImplemented(acpBridgePageApiId, api => {
    pageApi = api
    flushPendingStdout()
  })

  context.host.implement(acpBridgeHostApiId, {
    async listAgents() {
      return {
        agents: [
          { id: "junie", name: "Junie", iconResourcePath: "webview/views/acp-chat/assets/acpChatJunie.svg" },
          { id: "mock-agent", name: "Mock Agent" },
          { id: "env-auth-agent", name: "Env Auth Agent" },
          { id: "oauth-auth-agent", name: "OAuth Auth Agent" },
          { id: "no-auth-methods-agent", name: "No Auth Methods Agent" },
        ],
      }
    },
    async startAgent(params) {
      pendingStdout.length = 0
      activeAgentId = typeof params?.agentId === "string" ? params.agentId : "mock-agent"
      currentSessionModeValue = defaultSessionModeValue
      currentModelValue = defaultModelValue
      currentEffortValue = defaultEffortValue
      currentBraveModeValue = defaultBraveModeValue
      currentThinkMoreValue = defaultThinkMoreValue
      currentDebugModeValue = defaultDebugModeValue
      activeSessionId = restartCounter === 0 ? sessionId : `mock-session-restarted-${restartCounter}`
      envSessionAuthenticated = activeAgentId === "env-auth-agent" && typeof params?.extraEnv?.[envAuthVar] === "string"
      oauthAuthenticated = false
      promptAuthAuthenticated = typeof params?.extraEnv?.[promptAuthVar] === "string"
      if (typeof params?.extraEnv?.[repeatedPromptAuthVar] !== "string") repeatedPromptAuthCount = 0
      listedSessions = defaultListedSessions()
      newSessionCounter = 0
      restartCounter++
      return { ok: true, cwd: mockCwd }
    },
    async openAcpConfig() {
      return { ok: true }
    },
    async resolvePathLinks(params) {
      const resolvedRawPaths = new Set([
        "views/acp-chat/src/components/MarkdownRenderer.tsx:47",
        "community/plugins/ui.webview/demo/webview-src/views/acp-chat/src/bridge/webviewApi.ts#L1",
      ])
      return { resolvedIds: params.candidates.filter(candidate => resolvedRawPaths.has(candidate.rawPath)).map(candidate => candidate.id) }
    },
    async navigatePathLink() {
    },
    async sendStdin(params) {
      const message = parseJsonRpcMessage(params.line)
      if (!message || typeof message.method !== "string") {
        return
      }
      if (message.id == null) {
        handleNotification(message)
        return
      }
      await handleRequest(message as JsonRpcMessage & { id: JsonRpcId; method: string })
    },
    async stopAgent() {
      pageApi?.onAgentExit({ code: 0 })
    },
  })

  async function handleRequest(message: JsonRpcMessage & { id: JsonRpcId; method: string }): Promise<void> {
    switch (message.method) {
      case "initialize":
        await sendPageStdout(response(message.id, {
          protocolVersion: message.params?.protocolVersion ?? 1,
          agentCapabilities: {
            loadSession: true,
            promptCapabilities: { image: true, audio: false, embeddedContext: true },
            sessionCapabilities: { list: {}, delete: {} },
          },
          authMethods: initializeAuthMethods(),
        }))
        break
      case "authenticate":
        await authenticate(message.id, message.params)
        break
      case "session/new":
        if (activeAgentId === "env-auth-agent" && !envSessionAuthenticated) {
          await sendPageStdout(authRequiredResponse(message.id, envAuthMethods()))
          break
        }
        if (activeAgentId === "oauth-auth-agent" && !oauthAuthenticated) {
          await sendPageStdout(authRequiredResponse(message.id, oauthAuthMethods()))
          break
        }
        if (activeAgentId === "no-auth-methods-agent") {
          await sendPageStdout(authRequiredResponse(message.id, []))
          break
        }
        activeSessionId = newSessionCounter === 0 ? activeSessionId : `mock-session-new-${newSessionCounter}`
        newSessionCounter++
        await sendPageStdout(response(message.id, {
          sessionId: activeSessionId,
          modes: sessionModes(),
          configOptions: sessionConfigOptions(),
        }))
        await sendPageStdout({
          jsonrpc: "2.0",
          method: "session/update",
          params: {
            sessionId: activeSessionId,
            update: {
              sessionUpdate: "available_commands_update",
              availableCommands: sessionCommands(),
            },
          },
        })
        if (activeSessionId !== sessionId) void sendLateStaleSessionUpdate(loadedSessionId)
        break
      case "session/set_config_option":
        updateConfigOption(message.params)
        await sendPageStdout(response(message.id, { configOptions: sessionConfigOptions() }))
        break
      case "session/list":
        await sendPageStdout(response(message.id, { sessions: listedSessions, nextCursor: null }))
        break
      case "session/load":
        await loadSession(message.id, message.params)
        break
      case "session/delete":
        if (typeof message.params?.sessionId === "string") {
          listedSessions = listedSessions.filter(session => session.sessionId !== message.params.sessionId)
        }
        await sendPageStdout(response(message.id, {}))
        break
      case "session/prompt": {
        const text = promptText(message.params?.prompt)
        if (text.includes(repeatedPromptAuthProbePrompt) && repeatedPromptAuthCount < 2) {
          await sendPageStdout(authRequiredResponse(message.id, repeatedPromptAuthMethods()))
          break
        }
        if (!text.includes(repeatedPromptAuthProbePrompt) && text.includes(promptAuthProbePrompt) && !promptAuthAuthenticated) {
          await sendPageStdout(authRequiredResponse(message.id, promptAuthMethods()))
          break
        }
        await sendAssistantResponse(message.id, text)
        break
      }
      default:
        await sendPageStdout({
          jsonrpc: "2.0",
          id: message.id,
          error: { code: -32601, message: "Method not found: " + message.method },
        })
    }
  }

  function handleNotification(message: JsonRpcMessage): void {
    if (message.method === "session/cancel") {
      void sendPageStdout({
        jsonrpc: "2.0",
        method: "session/update",
        params: {
          sessionId: activeSessionId,
          update: {
            sessionUpdate: "agent_message_chunk",
            content: { type: "text", text: "\n\nMock agent cancelled." },
          },
        },
      })
    }
  }

  async function loadSession(requestId: JsonRpcId, params: any): Promise<void> {
    activeSessionId = typeof params?.sessionId === "string" ? params.sessionId : loadedSessionId
    await sendSessionUpdate({
      sessionUpdate: "user_message_chunk",
      content: { type: "text", text: "Loaded user request" },
    })
    await sendSessionUpdate({
      sessionUpdate: "agent_message_chunk",
      content: { type: "text", text: "Loaded assistant response" },
    })
    const updatedAt = "2026-06-29T09:30:00.000Z"
    listedSessions = listedSessions.map(session => session.sessionId === activeSessionId
      ? { ...session, title: "Loaded session renamed", updatedAt }
      : session)
    await sendSessionUpdate({
      sessionUpdate: "session_info_update",
      title: "Loaded session renamed",
      updatedAt,
    })
    await sendPageStdout(response(requestId, { modes: sessionModes(), configOptions: sessionConfigOptions() }))
  }

  async function authenticate(requestId: JsonRpcId, params: any): Promise<void> {
    const methodId = typeof params?.methodId === "string" ? params.methodId : ""
    if (activeAgentId === "oauth-auth-agent" && methodId === "oauth-browser") {
      await sendPageStdout({
        jsonrpc: "2.0",
        method: "authenticate/update",
        params: {
          authUri: "https://example.com/oauth/device?token=oauth-secret&user_code=ABCD-EFGH",
          _meta: { authUri: "https://example.com/oauth/device?token=oauth-secret&user_code=ABCD-EFGH" },
        },
      })
      await delay(300)
      oauthAuthenticated = true
    }
    else if (methodId === "env-token") {
      envSessionAuthenticated = true
    }
    else if (methodId === "prompt-env") {
      promptAuthAuthenticated = true
    }
    else if (methodId === "repeat-prompt-env") {
      repeatedPromptAuthCount++
    }
    await sendPageStdout(response(requestId, {}))
  }

  async function sendSessionUpdate(update: unknown): Promise<void> {
    await sendPageStdout({
      jsonrpc: "2.0",
      method: "session/update",
      params: {
        sessionId: activeSessionId,
        update,
      },
    })
  }

  async function sendLateStaleSessionUpdate(staleSessionId: string): Promise<void> {
    await delay(50)
    await sendPageStdout({
      jsonrpc: "2.0",
      method: "session/update",
      params: {
        sessionId: staleSessionId,
        update: {
          sessionUpdate: "user_message_chunk",
          content: { type: "text", text: "Late stale loaded session request" },
        },
      },
    })
  }

  async function sendAssistantResponse(requestId: JsonRpcId, text: string): Promise<void> {
    if (text.includes(repeatedPromptAuthProbePrompt)) {
      await sendAssistantText(requestId, `Repeated prompt auth retry completed: ${text}`)
      return
    }
    if (text.includes(promptAuthProbePrompt)) {
      await sendAssistantText(requestId, `Prompt auth retry completed: ${text}`)
      return
    }
    if (text.includes(markdownFeatureProbePrompt)) {
      await sendAssistantText(requestId, markdownFeatureResponse())
      return
    }
    if (text.includes(toolCallCompactProbePrompt)) {
      await sendToolCallCompactResponse(requestId)
      return
    }
    if (text.includes(toolCallOrderProbePrompt)) {
      await sendToolCallOrderResponse(requestId)
      return
    }
    if (text.includes(toolStatusIconsProbePrompt)) {
      await sendToolStatusIconsResponse(requestId)
      return
    }
    if (text.includes(streamingProbePrompt)) {
      await sendStreamingAssistantResponse(requestId, text)
      return
    }
    await sendAssistantText(requestId, `Mock response from AI chat: ${text || "Hello from the browser mock."}`)
  }

  async function sendAssistantText(requestId: JsonRpcId, text: string): Promise<void> {
    await sendPageStdout({
      jsonrpc: "2.0",
      method: "session/update",
      params: {
        sessionId: activeSessionId,
        update: {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text },
        },
      },
    })
    await sendPageStdout(response(requestId, { stopReason: "end_turn" }))
  }

  async function sendToolCallCompactResponse(requestId: JsonRpcId): Promise<void> {
    await sendSessionUpdate({
      sessionUpdate: "tool_call",
      toolCallId: "compact-tool-call",
      title: "Run compact tool probe",
      kind: "execute",
      status: "completed",
      content: [
        {
          type: "content",
          content: {
            type: "text",
            text: compactToolOutput(),
          },
        },
      ],
    })
    await sendAssistantText(requestId, "Tool call compact probe done.")
  }

  async function sendToolCallOrderResponse(requestId: JsonRpcId): Promise<void> {
    await sendSessionUpdate({
      sessionUpdate: "agent_message_chunk",
      content: { type: "text", text: "Before interleaved tool." },
    })
    await sendSessionUpdate({
      sessionUpdate: "tool_call",
      toolCallId: "ordered-tool-call",
      title: "Run ordered tool probe",
      kind: "execute",
      status: "completed",
      content: [
        {
          type: "content",
          content: {
            type: "text",
            text: "ordered tool output",
          },
        },
      ],
    })
    await sendSessionUpdate({
      sessionUpdate: "agent_message_chunk",
      content: { type: "text", text: "After interleaved tool." },
    })
    await sendPageStdout(response(requestId, { stopReason: "end_turn" }))
  }

  async function sendToolStatusIconsResponse(requestId: JsonRpcId): Promise<void> {
    await sendSessionUpdate({
      sessionUpdate: "tool_call",
      toolCallId: "status-completed-tool-call",
      title: "Completed status probe",
      kind: "execute",
      status: "completed",
    })
    await sendSessionUpdate({
      sessionUpdate: "tool_call",
      toolCallId: "status-running-tool-call",
      title: "Running status probe",
      kind: "execute",
      status: "in_progress",
    })
    await sendSessionUpdate({
      sessionUpdate: "tool_call",
      toolCallId: "status-failed-tool-call",
      title: "Failed status probe",
      kind: "execute",
      status: "failed",
    })
    await sendAssistantText(requestId, "Tool status icons probe done.")
  }

  function updateConfigOption(params: any): void {
    if (params?.configId === modeConfigId && typeof params.value === "string") {
      currentSessionModeValue = params.value
    }
    else if (params?.configId === modelConfigId && typeof params.value === "string") {
      currentModelValue = params.value
    }
    else if (params?.configId === effortConfigId && typeof params.value === "string") {
      currentEffortValue = params.value
    }
    else if (params?.configId === braveModeConfigId && params.type === "boolean" && typeof params.value === "boolean") {
      currentBraveModeValue = params.value
    }
    else if (params?.configId === thinkMoreConfigId && typeof params.value === "string") {
      currentThinkMoreValue = params.value
    }
    else if (params?.configId === debugModeConfigId && params.type === "boolean" && typeof params.value === "boolean") {
      currentDebugModeValue = params.value
    }
  }

  function sessionModes(): unknown {
    return {
      availableModes: [],
      currentModeId: null,
    }
  }

  function sessionConfigOptions(): unknown[] {
    return [
      {
        id: modeConfigId,
        type: "select",
        name: "Mode",
        description: "Controls how Junie handles the current session.",
        currentValue: currentSessionModeValue,
        options: [
          { value: "auto", name: "Auto", description: "Let Junie choose the best session mode." },
          { value: "ask", name: "Ask", description: "Answer questions without editing files." },
          { value: "code", name: "Code", description: "Use tools and apply changes." },
        ],
      },
      {
        id: modelConfigId,
        type: "select",
        name: "Model",
        description: "Controls the mocked model profile.",
        category: "model",
        currentValue: currentModelValue,
        options: [
          { value: "gemini-2.5-flash", name: "Gemini 2.5 Flash", description: "Fast Gemini mock model." },
          { value: "gemini-2.5-pro", name: "Gemini 2.5 Pro", description: "Stronger Gemini mock model." },
        ],
      },
      {
        id: effortConfigId,
        type: "select",
        name: "Effort",
        description: "Select how much reasoning effort Junie spends for the selected model.",
        category: "thought_level",
        currentValue: currentEffortValue,
        options: [
          { value: "low", name: "Low effort" },
          { value: "medium", name: "Medium effort" },
          { value: "high", name: "High effort" },
        ],
      },
      {
        id: braveModeConfigId,
        type: "boolean",
        name: "Brave Mode",
        description: "When enabled, Junie will execute commands without asking for approval.",
        currentValue: currentBraveModeValue,
      },
      {
        id: thinkMoreConfigId,
        type: "select",
        name: "Think More",
        description: "When enabled, Junie will spend more time thinking before acting.",
        currentValue: currentThinkMoreValue,
        options: [
          { value: "on", name: "On" },
          { value: "off", name: "Off" },
        ],
      },
      {
        id: debugModeConfigId,
        type: "boolean",
        name: "Debug Mode",
        description: "When enabled, Junie will use debug-specific behavior.",
        currentValue: currentDebugModeValue,
      },
      {
        id: "empty_selector",
        type: "select",
        name: "Empty selector",
        description: "This should not be rendered.",
        currentValue: "",
        options: [],
      },
    ]
  }

  function sessionCommands(): unknown[] {
    return [
      { name: "summarize", description: "Summarize the current context.", input: { hint: "what to summarize" } },
      { name: "explain", description: "Explain the selected topic.", input: { hint: "topic" } },
      { name: "refactor", description: "Suggest a small refactoring.", input: { hint: "target" } },
      { name: "test", description: "Create a focused test plan.", input: { hint: "behavior" } },
      { name: "document", description: "Draft documentation for a symbol.", input: { hint: "symbol" } },
      { name: "optimize", description: "Find a performance improvement.", input: { hint: "code path" } },
      { name: "debug", description: "Inspect a failing scenario.", input: { hint: "failure" } },
      { name: "inspect", description: "Inspect related implementation details.", input: { hint: "area" } },
      { name: "migrate", description: "Plan a migration path.", input: { hint: "source and target" } },
      { name: "cleanup", description: "Identify safe cleanup work.", input: { hint: "scope" } },
      { name: "review", description: "Review the current change.", input: { hint: "focus" } },
      { name: "commit", description: "Draft a commit summary.", input: { hint: "change" } },
    ]
  }

  function initializeAuthMethods(): unknown[] {
    switch (activeAgentId) {
      case "env-auth-agent":
        return envAuthMethods()
      case "oauth-auth-agent":
        return oauthAuthMethods()
      default:
        return []
    }
  }

  async function sendStreamingAssistantResponse(requestId: JsonRpcId, text: string): Promise<void> {
    const thoughtChunks = [`Reasoning about ${text}.`, " Checking stream state.", " Keeping reasoning active."]
    const messageChunks = [`Streaming markdown response for ${text}.`, " Still streaming.", " Almost done."]
    for (let i = 0; i < thoughtChunks.length; i++) {
      await sendPageStdout({
        jsonrpc: "2.0",
        method: "session/update",
        params: {
          sessionId: activeSessionId,
          update: {
            sessionUpdate: "agent_thought_chunk",
            content: { type: "text", text: thoughtChunks[i] },
          },
        },
      })
      await sendPageStdout({
        jsonrpc: "2.0",
        method: "session/update",
        params: {
          sessionId: activeSessionId,
          update: {
            sessionUpdate: "agent_message_chunk",
            content: { type: "text", text: messageChunks[i] },
          },
        },
      })
      await delay(300)
    }
    await delay(1000)
    await sendPageStdout(response(requestId, { stopReason: "end_turn" }))
  }

  async function sendPageStdout(frame: unknown): Promise<void> {
    if (!pageApi) {
      pendingStdout.push(frame)
      return
    }
    pageApi.onAgentStdout({ line: JSON.stringify(frame) })
  }

  function flushPendingStdout(): void {
    for (const frame of pendingStdout.splice(0)) {
      void sendPageStdout(frame)
    }
  }
})

function parseJsonRpcMessage(line: string): JsonRpcMessage | null {
  try {
    const parsed = JSON.parse(line)
    return parsed != null && typeof parsed === "object" ? parsed : null
  }
  catch {
    return null
  }
}

function response(id: JsonRpcId, result: unknown): unknown {
  return { jsonrpc: "2.0", id, result }
}

function authRequiredResponse(id: JsonRpcId, authMethods: unknown[]): unknown {
  return {
    jsonrpc: "2.0",
    id,
    error: {
      code: -32000,
      message: "Authentication is required before this operation can be performed.",
      data: { authMethods },
    },
  }
}

function envAuthMethods(): unknown[] {
  return [{
    id: "env-token",
    type: "env_var",
    name: "Use Junie token",
    description: "Enter a token that will be passed to the local agent process.",
    vars: [{ name: envAuthVar, label: "Junie token", secret: true, optional: false }],
    _meta: { source: "mock-env-auth" },
  }]
}

function oauthAuthMethods(): unknown[] {
  return [{
    id: "oauth-browser",
    type: "browser",
    name: "Sign in with browser",
    description: "The agent will provide a verification URL.",
    link: "https://example.com/oauth/start",
    vars: [],
    _meta: { source: "mock-oauth-auth" },
  }]
}

function promptAuthMethods(): unknown[] {
  return [{
    id: "prompt-env",
    type: "env_var",
    name: "Use prompt token",
    vars: [{ name: promptAuthVar, label: "Prompt auth token", secret: true, optional: false }],
    _meta: { source: "mock-prompt-auth" },
  }]
}

function repeatedPromptAuthMethods(): unknown[] {
  return [{
    id: "repeat-prompt-env",
    type: "env_var",
    name: "Use repeated prompt token",
    vars: [{ name: repeatedPromptAuthVar, label: "Repeated prompt auth token", secret: true, optional: false }],
    _meta: { source: "mock-repeated-prompt-auth" },
  }]
}

function defaultListedSessions() {
  return [
    { sessionId, cwd: mockCwd, title: "Current mock chat", updatedAt: "2026-06-29T09:00:00.000Z" },
    { sessionId: loadedSessionId, cwd: mockCwd, title: "Loaded session one", updatedAt: "2026-06-29T08:30:00.000Z" },
    { sessionId: deletedSessionId, cwd: mockCwd, title: "Loaded session two", updatedAt: "2026-06-28T18:00:00.000Z" },
  ]
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function compactToolOutput(): string {
  return [
    "tool output header",
    ...Array.from({ length: 24 }, (_, index) => `long compact tool output line ${index + 1}`),
    "tool output footer",
  ].join("\n")
}

function promptText(prompt: unknown): string {
  if (!Array.isArray(prompt)) {
    return ""
  }
  return prompt.map(block => block?.type === "text" && typeof block.text === "string" ? block.text : "").join("")
}

function markdownFeatureResponse(): string {
  return `# Markdown feature matrix

| Feature | Status |
| --- | --- |
| GFM table | Works |
| KaTeX | Works |

- [x] Render task lists
- [ ] Keep unchecked tasks visible

Inline math: $a^2 + b^2 = c^2$.

$$
\\int_0^1 x^2 dx = \\frac{1}{3}
$$

\`\`\`mermaid
flowchart TD
  A[src/Mermaid.kt] --> B[Mermaid]
\`\`\`

Inline path: \`views/acp-chat/src/components/MarkdownRenderer.tsx:47\` and unresolved path: \`missing/Nope.kt\`.

\`\`\`ts
const answer: number = 42
\`\`\`

\`\`\`text
community/plugins/ui.webview/demo/webview-src/views/acp-chat/src/bridge/webviewApi.ts#L1
\`\`\`

<details open><summary>Raw HTML details</summary><kbd>Cmd</kbd> + <kbd>K</kbd> with <mark>mark</mark>, H<sub>2</sub>O, and E=mc<sup>2</sup>.</details>

Here is [a safe link](https://example.com).

Footnote reference[^1].

[^1]: Footnote content from ACP chat markdown.

<script>window.__ACP_MARKDOWN_SCRIPT_EXECUTED__ = true</script>
${unsafeImageHtml()}
`
}

function unsafeImageHtml(): string {
  const eventAttribute = "onerror"
  return `<img src="https://example.com/unsafe.png" alt="Unsafe image" ${eventAttribute}="window.__ACP_MARKDOWN_ONERROR_EXECUTED__ = true">`
}
