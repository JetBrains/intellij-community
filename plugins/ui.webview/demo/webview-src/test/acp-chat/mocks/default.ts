// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId } from "@jetbrains/intellij-webview"
import { defineWebViewMock, type MockCallable } from "@jetbrains/intellij-webview-testkit"
import type { AcpBridgeHostApi, AcpBridgePageApi } from "../../../views/acp-chat/src/bridge/webviewApi"

const acpBridgeHostApiId = apiId<AcpBridgeHostApi>()("acp.bridge")
const acpBridgePageApiId = apiId<AcpBridgePageApi>()("acp.bridge")
const sessionId = "mock-session"
const streamingProbePrompt = "streaming probe"
const markdownFeatureProbePrompt = "markdown feature probe"
const defaultModeId = "ask"
const modelConfigId = "gemini_model"
const defaultModelValue = "gemini-2.5-flash"
const defaultAutonomyValue = false

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
  let currentModeId = defaultModeId
  let currentModelValue = defaultModelValue
  let currentAutonomyValue = defaultAutonomyValue

  context.page.whenImplemented(acpBridgePageApiId, api => {
    pageApi = api
    flushPendingStdout()
  })

  context.host.implement(acpBridgeHostApiId, {
    async listAgents() {
      return {
        agents: [
          { id: "mock-agent", name: "Mock Agent" },
        ],
      }
    },
    async startAgent() {
      pendingStdout.length = 0
      currentModeId = defaultModeId
      currentModelValue = defaultModelValue
      currentAutonomyValue = defaultAutonomyValue
      return { ok: true, cwd: "/mock/project" }
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
            promptCapabilities: { image: true, audio: false, embeddedContext: true },
          },
          authMethods: [],
        }))
        break
      case "session/new":
        await sendPageStdout(response(message.id, {
          sessionId,
          modes: {
            availableModes: [
              { id: "ask", name: "Ask", description: "Answer questions without editing files." },
              { id: "code", name: "Code", description: "Use tools and apply changes." },
            ],
            currentModeId,
          },
          configOptions: sessionConfigOptions(),
        }))
        await sendPageStdout({
          jsonrpc: "2.0",
          method: "session/update",
          params: {
            sessionId,
            update: {
              sessionUpdate: "available_commands_update",
              availableCommands: sessionCommands(),
            },
          },
        })
        break
      case "session/set_mode":
        if (typeof message.params?.modeId === "string") {
          currentModeId = message.params.modeId
        }
        await sendPageStdout(response(message.id, {}))
        break
      case "session/set_config_option":
        updateConfigOption(message.params)
        await sendPageStdout(response(message.id, { configOptions: sessionConfigOptions() }))
        break
      case "session/prompt":
        await sendAssistantResponse(message.id, promptText(message.params?.prompt))
        break
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
          sessionId,
          update: {
            sessionUpdate: "agent_message_chunk",
            content: { type: "text", text: "\n\nMock agent cancelled." },
          },
        },
      })
    }
  }

  async function sendAssistantResponse(requestId: JsonRpcId, text: string): Promise<void> {
    if (text.includes(markdownFeatureProbePrompt)) {
      await sendAssistantText(requestId, markdownFeatureResponse())
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
        sessionId,
        update: {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text },
        },
      },
    })
    await sendPageStdout(response(requestId, { stopReason: "end_turn" }))
  }

  function updateConfigOption(params: any): void {
    if (params?.configId === modelConfigId && typeof params.value === "string") {
      currentModelValue = params.value
    }
    else if (params?.configId === "autonomy" && params.type === "boolean" && typeof params.value === "boolean") {
      currentAutonomyValue = params.value
    }
  }

  function sessionConfigOptions(): unknown[] {
    return [
      {
        id: modelConfigId,
        type: "select",
        name: "Gemini model",
        description: "Controls the mocked model profile.",
        currentValue: currentModelValue,
        options: [
          { value: "gemini-2.5-flash", name: "Gemini 2.5 Flash", description: "Fast Gemini mock model." },
          { value: "gemini-2.5-pro", name: "Gemini 2.5 Pro", description: "Stronger Gemini mock model." },
        ],
      },
      {
        id: "autonomy",
        type: "boolean",
        name: "Autonomy",
        description: "Allow the mock agent to act independently.",
        currentValue: currentAutonomyValue,
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

  async function sendStreamingAssistantResponse(requestId: JsonRpcId, text: string): Promise<void> {
    const thoughtChunks = [`Reasoning about ${text}.`, " Checking stream state.", " Keeping reasoning active."]
    const messageChunks = [`Streaming markdown response for ${text}.`, " Still streaming.", " Almost done."]
    for (let i = 0; i < thoughtChunks.length; i++) {
      await sendPageStdout({
        jsonrpc: "2.0",
        method: "session/update",
        params: {
          sessionId,
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
          sessionId,
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

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
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
  A[Markdown] --> B[Mermaid]
\`\`\`

\`\`\`ts
const answer: number = 42
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
