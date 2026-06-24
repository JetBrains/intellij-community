// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { ClientSideConnection, PROTOCOL_VERSION, type Client } from "@agentclientprotocol/sdk"
import { createAgentStdioStream } from "../bridge/acpStdioStream"
import { acpBridgeHost } from "../bridge/webviewApi"
import type { PermissionView, PlanEntryView, ToolCallView } from "../model/types"

/**
 * Sink for ACP session events, decoded from `session/update` notifications and `session/request_permission` calls.
 * The runtime hook implements this to drive the assistant-ui store.
 */
export interface AcpEventSink {
  onMessageChunk(text: string): void
  onThoughtChunk(text: string): void
  onToolCall(view: ToolCallView): void
  onPlan(entries: PlanEntryView[]): void
  /** Resolve with the chosen optionId, or null to cancel. */
  requestPermission(view: PermissionView): Promise<string | null>
  onAgentExit(code: number | null): void
}

/**
 * One ACP session over a spawned agent. The protocol is handled by the ACP TypeScript SDK; the transport is the
 * Kotlin-bridged process stdio. ACP wire objects are accessed defensively (`any`) so this stays resilient to minor
 * SDK shape differences.
 */
export class AcpSession {
  private connection: ClientSideConnection | null = null
  private sessionId: string | null = null

  get isActive(): boolean {
    return this.connection != null && this.sessionId != null
  }

  async start(agentId: string, sink: AcpEventSink): Promise<void> {
    const result = await acpBridgeHost.startAgent({ agentId })
    if (!result.ok) {
      throw new Error(result.error ?? `Failed to start agent '${agentId}'`)
    }
    const cwd = result.cwd ?? "."
    const io = createAgentStdioStream()
    io.onExit(code => sink.onAgentExit(code))

    const client: Client = {
      sessionUpdate(notification: any): void {
        handleUpdate(notification?.update, sink)
      },
      async requestPermission(request: any): Promise<any> {
        const optionId = await sink.requestPermission(toPermissionView(request))
        if (optionId == null) {
          return { outcome: { outcome: "cancelled" } }
        }
        return { outcome: { outcome: "selected", optionId } }
      },
    }

    const connection = new ClientSideConnection(() => client, io.stream)
    this.connection = connection
    await connection.initialize({
      protocolVersion: PROTOCOL_VERSION,
      clientCapabilities: { fs: { readTextFile: false, writeTextFile: false }, terminal: false },
    })
    const session = await connection.newSession({ cwd, mcpServers: [] })
    this.sessionId = session.sessionId
  }

  async prompt(text: string): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (!connection || !sessionId) {
      throw new Error("No active ACP session")
    }
    await connection.prompt({ sessionId, prompt: [{ type: "text", text }] })
  }

  async cancel(): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (connection && sessionId) {
      await connection.cancel({ sessionId })
    }
  }

  async stop(): Promise<void> {
    this.connection = null
    this.sessionId = null
    try {
      await acpBridgeHost.stopAgent()
    }
    catch {
      // ignore
    }
  }
}

function handleUpdate(update: any, sink: AcpEventSink): void {
  if (!update) return
  switch (update.sessionUpdate) {
    case "agent_message_chunk":
      sink.onMessageChunk(textOf(update.content))
      break
    case "agent_thought_chunk":
      sink.onThoughtChunk(textOf(update.content))
      break
    case "tool_call":
    case "tool_call_update":
      sink.onToolCall(toToolView(update))
      break
    case "plan":
      sink.onPlan(Array.isArray(update.entries) ? update.entries.map(toPlanView) : [])
      break
    default:
      break
  }
}

function textOf(content: any): string {
  if (!content) return ""
  if (Array.isArray(content)) return content.map(textOf).join("")
  if (content.type === "text" && typeof content.text === "string") return content.text
  return ""
}

function toToolView(update: any): ToolCallView {
  let text: string | undefined
  let diff: ToolCallView["diff"]
  const content = update.content
  if (Array.isArray(content)) {
    for (const item of content) {
      if (item?.type === "content") {
        text = (text ?? "") + textOf(item.content)
      }
      else if (item?.type === "diff") {
        diff = { path: String(item.path ?? ""), oldText: item.oldText ?? null, newText: String(item.newText ?? "") }
      }
    }
  }
  const kind = typeof update.kind === "string" ? update.kind : "other"
  return {
    toolCallId: String(update.toolCallId ?? ""),
    title: typeof update.title === "string" ? update.title : kind,
    kind,
    status: normalizeToolStatus(update.status),
    text,
    diff,
  }
}

function normalizeToolStatus(status: any): ToolCallView["status"] {
  switch (status) {
    case "pending":
    case "in_progress":
    case "completed":
    case "failed":
      return status
    default:
      return "in_progress"
  }
}

function toPlanView(entry: any): PlanEntryView {
  const status = entry?.status
  return {
    content: typeof entry?.content === "string" ? entry.content : "",
    status: status === "pending" || status === "in_progress" || status === "completed" ? status : "pending",
    priority: typeof entry?.priority === "string" ? entry.priority : undefined,
  }
}

function toPermissionView(request: any): PermissionView {
  const options = Array.isArray(request?.options) ? request.options : []
  return {
    title: typeof request?.toolCall?.title === "string" ? request.toolCall.title : "Permission requested",
    options: options.map((option: any) => ({
      optionId: String(option.optionId),
      name: String(option.name ?? option.optionId),
      kind: typeof option.kind === "string" ? option.kind : undefined,
    })),
  }
}
