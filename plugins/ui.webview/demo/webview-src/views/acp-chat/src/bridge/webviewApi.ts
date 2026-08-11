// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId, webView, type WebViewCallable, type WebViewImplementable } from "@jetbrains/intellij-webview"

// Typed mirror of the Kotlin `AcpBridgeApi` (namespace "acp.bridge").

export interface AgentDto {
  id: string
  name: string
  iconResourcePath?: string
}

export interface AgentListDto {
  agents: AgentDto[]
}

export interface StartAgentRequest {
  agentId: string
  /** Extra environment variables for the spawned process (e.g. an API key entered for an env_var auth method). */
  extraEnv?: Record<string, string>
}

export interface StartAgentResult {
  ok: boolean
  cwd?: string | null
  error?: string | null
}

export interface OpenAcpConfigResult {
  ok: boolean
  error?: string | null
}

export interface ResolvePathLinksRequest {
  candidates: PathLinkCandidateDto[]
}

export interface ResolvePathLinksResult {
  resolvedIds: string[]
}

export interface PathLinkCandidateDto {
  id: string
  rawPath: string
}

export interface NavigatePathLinkRequest {
  rawPath: string
  clientX: number
  clientY: number
}

export interface LineDto {
  line: string
}

export interface ExitDto {
  code?: number | null
}

/** Implemented in Kotlin, called from the page. */
export interface AcpBridgeHostApi extends WebViewCallable {
  listAgents(): Promise<AgentListDto>
  startAgent(params: StartAgentRequest): Promise<StartAgentResult>
  openAcpConfig(): Promise<OpenAcpConfigResult>
  resolvePathLinks(params: ResolvePathLinksRequest): Promise<ResolvePathLinksResult>
  navigatePathLink(params: NavigatePathLinkRequest): Promise<void>
  sendStdin(params: LineDto): Promise<void>
  stopAgent(): Promise<void>
}

/** Implemented in the page, called from Kotlin (notifications). */
export interface AcpBridgePageApi extends WebViewImplementable {
  onAgentStdout(params: LineDto): void
  onAgentExit(params: ExitDto): void
}

export const acpBridgeHost = webView.callable(apiId<AcpBridgeHostApi>()("acp.bridge"))

export interface BridgePageHandlers {
  onAgentStdout(params: LineDto): void
  onAgentExit(params: ExitDto): void
}

// Registered once for the lifetime of the page; the active stream sets the current handler.
let currentHandlers: BridgePageHandlers | null = null

webView.implement(apiId<AcpBridgePageApi>()("acp.bridge"), {
  onAgentStdout(params: LineDto) {
    currentHandlers?.onAgentStdout(params)
  },
  onAgentExit(params: ExitDto) {
    currentHandlers?.onAgentExit(params)
  },
})

export function setBridgePageHandlers(handlers: BridgePageHandlers | null): void {
  currentHandlers = handlers
}
