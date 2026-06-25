// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId } from "@jetbrains/intellij-webview"
import { defineWebViewMock, type MockCallable } from "@jetbrains/intellij-webview-testkit"
import type { AcpBridgeHostApi, AcpBridgePageApi } from "../../../views/acp-chat/src/bridge/webviewApi"

const acpBridgeHostApiId = apiId<AcpBridgeHostApi>()("acp.bridge")
const acpBridgePageApiId = apiId<AcpBridgePageApi>()("acp.bridge")
const sessionId = "mock-session"

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
      await pageApi?.onAgentExit({ code: 0 })
    },
  })

  async function handleRequest(message: JsonRpcMessage & { id: JsonRpcId; method: string }): Promise<void> {
    switch (message.method) {
      case "initialize":
        await sendPageStdout(response(message.id, {
          protocolVersion: message.params?.protocolVersion ?? 1,
          agentCapabilities: {
            promptCapabilities: { image: false, audio: false, embeddedContext: false },
          },
          authMethods: [],
        }))
        break
      case "session/new":
        await sendPageStdout(response(message.id, {
          sessionId,
          modes: {
            availableModes: [
              { id: "ask", name: "Ask" },
            ],
            currentModeId: "ask",
          },
          configOptions: [],
        }))
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
    await sendPageStdout({
      jsonrpc: "2.0",
      method: "session/update",
      params: {
        sessionId,
        update: {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text: `Mock response from AI chat: ${text || "Hello from the browser mock."}` },
        },
      },
    })
    await sendPageStdout(response(requestId, { stopReason: "end_turn" }))
  }

  async function sendPageStdout(frame: unknown): Promise<void> {
    if (!pageApi) {
      pendingStdout.push(frame)
      return
    }
    await pageApi.onAgentStdout({ line: JSON.stringify(frame) })
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

function promptText(prompt: unknown): string {
  if (!Array.isArray(prompt)) {
    return ""
  }
  return prompt.map(block => block?.type === "text" && typeof block.text === "string" ? block.text : "").join("")
}
