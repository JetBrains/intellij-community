// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { ClientSideConnection, PROTOCOL_VERSION, type Client, type ContentBlock } from "@agentclientprotocol/sdk"
import { createAgentStdioStream, type AgentStdioStream } from "../bridge/acpStdioStream"
import { acpBridgeHost } from "../bridge/webviewApi"
import type {
  AcpSessionCapabilitiesView,
  AcpSessionInfoUpdateView,
  AcpSessionInfoView,
  AuthMethodView,
  CommandView,
  ConfigOptionSelectChoiceView,
  ConfigOptionView,
  PermissionView,
  PlanEntryView,
  PromptCapabilitiesView,
  SessionModeView,
  ToolCallView,
} from "../model/types"

/**
 * Sink for ACP session events, decoded from `session/update` notifications and `session/request_permission` calls.
 * The runtime hook implements this to drive the assistant-ui store.
 */
export interface AcpEventSink {
  onUserMessage(text: string): void
  onMessageChunk(text: string): void
  onThoughtChunk(text: string): void
  onToolCall(view: ToolCallView): void
  onPlan(entries: PlanEntryView[]): void
  onPlanUpdate(planId: string, entries: PlanEntryView[]): void
  onPlanRemoved(planId: string): void
  onPromptCapabilities(capabilities: PromptCapabilitiesView): void
  onSessionModes(modes: SessionModeView[], currentModeId: string | null): void
  onCurrentMode(currentModeId: string): void
  onConfigOptions(options: ConfigOptionView[]): void
  onCommands(commands: CommandView[]): void
  onSessionInfoUpdate(view: AcpSessionInfoUpdateView): void
  /** Resolve with the chosen optionId, or null to cancel. */
  requestPermission(view: PermissionView): Promise<string | null>
  /** A verification URL for an in-progress OAuth device flow (pushed by the agent via `authenticate/update`). */
  onAuthUpdate(authUri: string): void
  onAgentExit(code: number | null): void
}

/** Result of attempting to open a session: ready, blocked on authentication, or a hard failure. */
export type StartOutcome =
  | { kind: "ready" }
  | { kind: "auth-required"; methods: AuthMethodView[]; message: string }
  | { kind: "error"; message: string }

export class AcpAuthRequiredError extends Error {
  constructor(readonly methods: AuthMethodView[], message: string) {
    super(message)
    this.name = "AcpAuthRequiredError"
  }
}

export interface ListSessionsOutcome {
  sessions: AcpSessionInfoView[]
  nextCursor: string | null
}

/**
 * One ACP session over a spawned agent. The protocol is handled by the ACP TypeScript SDK; the transport is the
 * Kotlin-bridged process stdio. ACP wire objects are accessed defensively (`any`) so this stays resilient to minor
 * SDK shape differences.
 */
export class AcpSession {
  private connection: ClientSideConnection | null = null
  private sessionId: string | null = null
  private cwd = "."
  private authMethods: any[] = []
  private capabilities: AcpSessionCapabilitiesView = emptySessionCapabilities()
  private io: AgentStdioStream | null = null
  private sink: AcpEventSink | null = null
  private extraEnv: Record<string, string> | undefined
  // Bumped on every stop()/connect(); an in-flight connect() that finds the value changed was superseded
  // (e.g. by a Cancel during a re-spawn) and tears itself down instead of installing a live, orphaned session.
  private generation = 0

  get isActive(): boolean {
    return this.connection != null && this.sessionId != null
  }

  get activeSessionId(): string | null {
    return this.sessionId
  }

  get workingDirectory(): string {
    return this.cwd
  }

  get sessionCapabilities(): AcpSessionCapabilitiesView {
    return this.capabilities
  }

  get canCloseActiveSession(): boolean {
    return this.capabilities.close && typeof this.connection?.closeSession === "function"
  }

  /** Spawn + connect the agent and attempt to open a session. */
  async start(agentId: string, sink: AcpEventSink): Promise<StartOutcome> {
    try {
      await this.connect(agentId, sink)
    }
    catch (error) {
      return { kind: "error", message: messageOf(error) }
    }
    return this.openSession()
  }

  /** Re-spawn the agent with extra environment (an entered API key) and reconnect; used for env_var auth methods. */
  async reconnectWithEnv(agentId: string, env: Record<string, string>, sink: AcpEventSink): Promise<void> {
    await this.stop()
    await this.connect(agentId, sink, env)
    this.extraEnv = env
  }

  async restart(agentId: string, sink: AcpEventSink): Promise<StartOutcome> {
    try {
      const extraEnv = this.extraEnv
      await this.stop()
      await this.connect(agentId, sink, extraEnv)
    }
    catch (error) {
      return { kind: "error", message: messageOf(error) }
    }
    return this.openSession()
  }

  /** Authenticate the live connection with the chosen method; for OAuth methods the agent drives a device flow. */
  async authenticate(methodId: string): Promise<void> {
    const connection = this.connection
    if (!connection) throw new Error("No agent connection")
    await connection.authenticate({ methodId })
  }

  /** (Re)try `session/new`, classifying an auth_required rejection into an actionable outcome. */
  async openSession(): Promise<StartOutcome> {
    const connection = this.connection
    if (!connection) return { kind: "error", message: "No agent connection" }
    try {
      const session = await connection.newSession({ cwd: this.cwd, mcpServers: [] })
      this.sessionId = session.sessionId
      this.sink?.onSessionModes(toSessionModeViews(session.modes?.availableModes), stringOrNull(session.modes?.currentModeId))
      this.sink?.onConfigOptions(toConfigOptionViews(session.configOptions))
      return { kind: "ready" }
    }
    catch (error) {
      const authError = this.toAuthRequiredError(error)
      if (!authError) {
        return { kind: "error", message: messageOf(error) }
      }
      return { kind: "auth-required", methods: authError.methods, message: authError.message }
    }
  }

  async closeActiveSession(): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (!connection || !sessionId) return
    if (!this.canCloseActiveSession) return
    await connection.closeSession({ sessionId })
    this.sessionId = null
  }

  async prompt(blocks: ContentBlock[]): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (!connection || !sessionId) {
      throw new Error("No active ACP session")
    }
    await this.callWithAuthClassification(() => connection.prompt({ sessionId, prompt: blocks }))
  }

  async promptText(text: string): Promise<void> {
    await this.prompt([{ type: "text", text }])
  }

  async listSessions(cursor?: string | null): Promise<ListSessionsOutcome> {
    const connection = this.connection
    if (!connection) throw new Error("No agent connection")
    if (!this.capabilities.list || typeof connection.listSessions !== "function") {
      throw new Error("The selected ACP agent does not support chat history.")
    }
    const response = await this.callWithAuthClassification(() => connection.listSessions({ cwd: this.cwd, cursor: cursor ?? undefined }))
    return {
      sessions: Array.isArray(response?.sessions) ? response.sessions.map(session => toSessionInfoView(session, this.cwd)).filter(isSessionInfoView) : [],
      nextCursor: stringOrNull(response?.nextCursor),
    }
  }

  async loadSession(sessionInfo: AcpSessionInfoView): Promise<void> {
    const connection = this.connection
    if (!connection) throw new Error("No agent connection")
    if (!this.capabilities.load || typeof connection.loadSession !== "function") {
      throw new Error("The selected ACP agent does not support loading existing chats.")
    }
    const previousSessionId = this.sessionId
    const previousCwd = this.cwd
    const cwd = sessionInfo.cwd || this.cwd
    this.sessionId = sessionInfo.sessionId
    this.cwd = cwd
    try {
      const session = await this.callWithAuthClassification(() => connection.loadSession({
        sessionId: sessionInfo.sessionId,
        cwd,
        additionalDirectories: sessionInfo.additionalDirectories ?? [],
        mcpServers: [],
      }))
      this.sink?.onSessionModes(toSessionModeViews(session?.modes?.availableModes), stringOrNull(session?.modes?.currentModeId))
      this.sink?.onConfigOptions(toConfigOptionViews(session?.configOptions))
    }
    catch (error) {
      this.sessionId = previousSessionId
      this.cwd = previousCwd
      throw error
    }
  }

  async deleteSession(sessionId: string): Promise<void> {
    const connection = this.connection
    if (!connection) throw new Error("No agent connection")
    if (!this.capabilities.delete || typeof connection.deleteSession !== "function") {
      throw new Error("The selected ACP agent does not support deleting chats.")
    }
    await this.callWithAuthClassification(() => connection.deleteSession({ sessionId }))
  }

  async setMode(modeId: string): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (!connection || !sessionId) {
      throw new Error("No active ACP session")
    }
    if (typeof connection.setSessionMode !== "function") {
      throw new Error("The selected ACP agent does not support session modes.")
    }
    await this.callWithAuthClassification(() => connection.setSessionMode({ sessionId, modeId }))
    this.sink?.onCurrentMode(modeId)
  }

  async setConfigOption(configId: string, type: ConfigOptionView["type"], value: string | boolean): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (!connection || !sessionId) {
      throw new Error("No active ACP session")
    }
    if (typeof connection.setSessionConfigOption !== "function") {
      throw new Error("The selected ACP agent does not support session config options.")
    }
    const response = await this.callWithAuthClassification(() => type === "boolean"
      ? connection.setSessionConfigOption({ sessionId, configId, type, value: value === true })
      : connection.setSessionConfigOption({ sessionId, configId, value: String(value) }))
    this.sink?.onConfigOptions(toConfigOptionViews(response?.configOptions))
  }

  async cancel(): Promise<void> {
    const connection = this.connection
    const sessionId = this.sessionId
    if (connection && sessionId) {
      await connection.cancel({ sessionId })
    }
  }

  async stop(): Promise<void> {
    this.generation++
    this.connection = null
    this.sessionId = null
    this.sink = null
    this.capabilities = emptySessionCapabilities()
    // Close the stream first so any in-flight request (e.g. a hanging OAuth `authenticate`) rejects promptly.
    const io = this.io
    this.io = null
    try {
      io?.close()
    }
    catch {
      // ignore
    }
    try {
      await acpBridgeHost.stopAgent()
    }
    catch {
      // ignore
    }
  }

  /** Spawn the process, wire the stdio stream, and run `initialize` (capturing the advertised auth methods). */
  private async connect(agentId: string, sink: AcpEventSink, extraEnv?: Record<string, string>): Promise<void> {
    const generation = ++this.generation
    // Install the stdio stream before spawning so any output the agent emits at startup is buffered, not dropped.
    const io = createAgentStdioStream()
    io.onExit(code => sink.onAgentExit(code))
    try {
      const result = await acpBridgeHost.startAgent({ agentId, extraEnv })
      if (!result.ok) {
        throw new Error(result.error ?? `Failed to start agent '${agentId}'`)
      }
      const client: Client = {
        sessionUpdate: (notification: any): void => {
          const updateSessionId = stringOrNull(notification?.sessionId)
          if (updateSessionId && updateSessionId !== this.sessionId) return
          handleUpdate(notification?.update, sink)
        },
        async requestPermission(request: any): Promise<any> {
          const optionId = await sink.requestPermission(toPermissionView(request))
          if (optionId == null) {
            return { outcome: { outcome: "cancelled" } }
          }
          return { outcome: { outcome: "selected", optionId } }
        },
      extNotification(method: string, params: any): void {
        // OAuth device flow: the agent pushes a verification URL via `authenticate/update` while `authenticate` is pending.
        if (method !== "authenticate/update") return
        const authUri = params?._meta?.authUri ?? params?.authUri
        if (typeof authUri === "string" && authUri) {
          sink.onAuthUpdate(authUri)
        }
      },
      }
      const connection = new ClientSideConnection(() => client, io.stream)
      const initializeRequest: any = {
        protocolVersion: PROTOCOL_VERSION,
        clientInfo: { name: "IntelliJ ACP Chat WebView Demo", version: "1.0.0" },
        clientCapabilities: { fs: { readTextFile: false, writeTextFile: false }, terminal: false, auth: {} },
      }
      const init: any = await connection.initialize(initializeRequest)
      if (this.generation !== generation) {
        throw new Error("ACP connection superseded")
      }
      this.cwd = result.cwd ?? "."
      this.io = io
      this.connection = connection
      this.sink = sink
      this.authMethods = Array.isArray(init?.authMethods) ? init.authMethods : []
      this.capabilities = toSessionCapabilitiesView(init?.agentCapabilities)
      sink.onPromptCapabilities(toPromptCapabilitiesView(init?.agentCapabilities?.promptCapabilities))
    }
    catch (error) {
      // Spawn/initialize failed or this attempt was superseded: drop the stream and kill the process we started.
      io.close()
      await acpBridgeHost.stopAgent().catch(() => {})
      throw error
    }
  }

  private authMethodViews(errorData?: any): AuthMethodView[] {
    return authMethodsOf(errorData, { authMethods: this.authMethods }).map(toAuthMethodView).filter(method => method.id.length > 0)
  }

  private toAuthRequiredError(error: unknown): AcpAuthRequiredError | null {
    if (!isAuthRequired(error)) return null
    return new AcpAuthRequiredError(this.authMethodViews((error as any)?.data), authMessage(error))
  }

  private async callWithAuthClassification<T>(call: () => Promise<T>): Promise<T> {
    try {
      return await call()
    }
    catch (error) {
      throw this.toAuthRequiredError(error) ?? error
    }
  }
}

function handleUpdate(update: any, sink: AcpEventSink): void {
  if (!update) return
  switch (update.sessionUpdate) {
    case "user_message_chunk":
      sink.onUserMessage(textOf(update.content))
      break
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
    case "plan_update": {
      const plan = toPlanUpdateView(update.plan)
      if (plan) sink.onPlanUpdate(plan.planId, plan.entries)
      break
    }
    case "plan_removed":
      if (typeof update.id === "string") sink.onPlanRemoved(update.id)
      break
    case "available_commands_update":
      sink.onCommands(toCommandViews(update.availableCommands))
      break
    case "current_mode_update":
      if (typeof update.currentModeId === "string") sink.onCurrentMode(update.currentModeId)
      break
    case "config_option_update":
      sink.onConfigOptions(toConfigOptionViews(update.configOptions))
      break
    case "session_info_update":
      sink.onSessionInfoUpdate(toSessionInfoUpdateView(update))
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

function toPlanView(entry: any, planId?: string): PlanEntryView {
  const status = entry?.status
  const view: PlanEntryView = {
    content: typeof entry?.content === "string" ? entry.content : "",
    status: status === "pending" || status === "in_progress" || status === "completed" ? status : "pending",
    priority: typeof entry?.priority === "string" ? entry.priority : undefined,
  }
  if (planId) view.planId = planId
  return view
}

function toPlanUpdateView(plan: any): { planId: string; entries: PlanEntryView[] } | null {
  const planId = typeof plan?.id === "string" ? plan.id : null
  if (!planId) return null
  if (plan.type === "items") {
    return { planId, entries: Array.isArray(plan.entries) ? plan.entries.map((entry: any) => toPlanView(entry, planId)) : [] }
  }
  if (plan.type === "markdown") {
    return { planId, entries: [{ planId, content: typeof plan.content === "string" ? plan.content : "", status: "in_progress" }] }
  }
  if (plan.type === "file") {
    const uri = typeof plan.uri === "string" ? plan.uri : ""
    return { planId, entries: [{ planId, content: uri ? `Plan file: ${uri}` : "Plan file", status: "in_progress" }] }
  }
  return null
}

function toPromptCapabilitiesView(capabilities: any): PromptCapabilitiesView {
  return {
    image: capabilities?.image === true,
    audio: capabilities?.audio === true,
    embeddedContext: capabilities?.embeddedContext === true,
  }
}

function emptySessionCapabilities(): AcpSessionCapabilitiesView {
  return { list: false, load: false, delete: false, resume: false, close: false }
}

function toSessionCapabilitiesView(agentCapabilities: any): AcpSessionCapabilitiesView {
  const sessionCapabilities = agentCapabilities?.sessionCapabilities
  return {
    list: sessionCapabilities?.list != null,
    load: agentCapabilities?.loadSession === true,
    delete: sessionCapabilities?.delete != null,
    resume: sessionCapabilities?.resume != null,
    close: sessionCapabilities?.close != null,
  }
}

function toSessionInfoView(session: any, fallbackCwd: string): AcpSessionInfoView | null {
  const sessionId = typeof session?.sessionId === "string" ? session.sessionId : ""
  if (!sessionId) return null
  return {
    sessionId,
    cwd: stringOrDefault(session.cwd, fallbackCwd),
    additionalDirectories: Array.isArray(session.additionalDirectories) ? session.additionalDirectories.filter((dir: any): dir is string => typeof dir === "string") : undefined,
    title: stringOrNull(session.title),
    updatedAt: stringOrNull(session.updatedAt),
  }
}

function isSessionInfoView(session: AcpSessionInfoView | null): session is AcpSessionInfoView {
  return session != null
}

function toSessionInfoUpdateView(update: any): AcpSessionInfoUpdateView {
  const view: AcpSessionInfoUpdateView = {}
  if (Object.prototype.hasOwnProperty.call(update, "title")) view.title = stringOrNull(update.title)
  if (Object.prototype.hasOwnProperty.call(update, "updatedAt")) view.updatedAt = stringOrNull(update.updatedAt)
  return view
}

function toSessionModeViews(modes: any): SessionModeView[] {
  if (!Array.isArray(modes)) return []
  const result: SessionModeView[] = []
  for (const mode of modes) {
    const id = typeof mode?.id === "string" ? mode.id : ""
    if (!id) continue
    result.push({ id, name: stringOrDefault(mode.name, id), description: stringOrUndefined(mode.description) })
  }
  return result
}

function toConfigOptionViews(options: any): ConfigOptionView[] {
  if (!Array.isArray(options)) return []
  const result: ConfigOptionView[] = []
  for (const option of options) {
    const id = typeof option?.id === "string" ? option.id : ""
    if (!id) continue
    const base = {
      id,
      name: stringOrDefault(option.name, id),
      description: stringOrUndefined(option.description),
      category: stringOrUndefined(option.category),
    }
    if (option.type === "select") {
      result.push({
        ...base,
        type: "select",
        currentValue: typeof option.currentValue === "string" ? option.currentValue : "",
        options: toConfigOptionSelectChoices(option.options),
      })
    }
    else if (option.type === "boolean") {
      result.push({ ...base, type: "boolean", currentValue: option.currentValue === true })
    }
  }
  return result
}

function toConfigOptionSelectChoices(options: any): ConfigOptionSelectChoiceView[] {
  if (!Array.isArray(options)) return []
  const result: ConfigOptionSelectChoiceView[] = []
  for (const option of options) {
    if (Array.isArray(option?.options)) {
      const group = stringOrUndefined(option.group)
      const groupName = stringOrUndefined(option.name)
      for (const groupedOption of option.options) {
        const choice = toConfigOptionSelectChoice(groupedOption, group, groupName)
        if (choice) result.push(choice)
      }
    }
    else {
      const choice = toConfigOptionSelectChoice(option)
      if (choice) result.push(choice)
    }
  }
  return result
}

function toConfigOptionSelectChoice(option: any, group?: string, groupName?: string): ConfigOptionSelectChoiceView | null {
  const value = typeof option?.value === "string" ? option.value : ""
  if (!value) return null
  const choice: ConfigOptionSelectChoiceView = {
    value,
    name: stringOrDefault(option.name, value),
    description: stringOrUndefined(option.description),
  }
  if (group) choice.group = group
  if (groupName) choice.groupName = groupName
  return choice
}

function toCommandViews(commands: any): CommandView[] {
  if (!Array.isArray(commands)) return []
  const result: CommandView[] = []
  for (const command of commands) {
    const name = typeof command?.name === "string" ? command.name : ""
    if (!name) continue
    result.push({
      name,
      description: typeof command.description === "string" ? command.description : "",
      inputHint: stringOrUndefined(command.input?.hint),
    })
  }
  return result
}

function stringOrDefault(value: any, fallback: string): string {
  return typeof value === "string" && value ? value : fallback
}

function stringOrUndefined(value: any): string | undefined {
  return typeof value === "string" ? value : undefined
}

function stringOrNull(value: any): string | null {
  return typeof value === "string" ? value : null
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

/** ACP `auth_required` JSON-RPC error code (see `RequestError.authRequired` in the ACP SDK). */
const ACP_AUTH_REQUIRED_CODE = -32000

function isAuthRequired(error: unknown): boolean {
  return (error as any)?.code === ACP_AUTH_REQUIRED_CODE
}

/** The agent's own auth_required message (e.g. "Authentication required: …"), or a generic fallback. */
function authMessage(error: unknown): string {
  return messageOf(error).trim() || "This agent requires authentication."
}

/** Prefer auth methods carried in the error payload, falling back to those advertised at initialize. */
function authMethodsOf(errorData: any, init: any): any[] {
  for (const container of [errorData, errorData?.auth, errorData?._meta, init]) {
    if (Array.isArray(container?.authMethods) && container.authMethods.length > 0) return container.authMethods
    if (Array.isArray(container?.methods) && container.methods.length > 0) return container.methods
  }
  return []
}

function toAuthMethodView(method: any): AuthMethodView {
  const vars = Array.isArray(method?.vars)
    ? method.vars
        .map((v: any) => ({
          name: String(v?.name ?? ""),
          label: typeof v?.label === "string" ? v.label : undefined,
          secret: v?.secret !== false, // `secret` defaults to true per the ACP spec
          optional: v?.optional === true,
        }))
        .filter((v: { name: string }) => v.name)
    : []
  return {
    id: String(method?.id ?? ""),
    name: stringOrDefault(method?.name, stringOrDefault(method?.label, String(method?.id ?? "auth"))),
    label: stringOrUndefined(method?.label),
    type: stringOrUndefined(method?.type),
    description: typeof method?.description === "string" ? method.description : undefined,
    link: stringOrUndefined(method?.link),
    vars,
    meta: objectOrUndefined(method?._meta),
  }
}

function objectOrUndefined(value: any): Record<string, unknown> | undefined {
  return value != null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
