// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/** UI view models shared across the ACP chat view. These are intentionally decoupled from the ACP SDK wire types. */

export interface AgentInfo {
  id: string
  name: string
  icon?: "junie"
}

export type ToolStatus = "pending" | "in_progress" | "completed" | "failed"

export interface ToolCallView {
  toolCallId: string
  title: string
  kind: string
  status: ToolStatus
  /** Rendered textual content of the tool call, if any. */
  text?: string
  /** A file diff produced by the tool call, if any. */
  diff?: { path: string; oldText: string | null; newText: string }
}

export type PlanStatus = "pending" | "in_progress" | "completed"

export interface PlanEntryView {
  planId?: string
  content: string
  status: PlanStatus
  priority?: string
}

export interface PromptCapabilitiesView {
  image: boolean
  audio: boolean
  embeddedContext: boolean
}

export interface AcpSessionInfoView {
  sessionId: string
  cwd: string
  additionalDirectories?: string[]
  title?: string | null
  updatedAt?: string | null
}

export interface AcpSessionCapabilitiesView {
  list: boolean
  load: boolean
  delete: boolean
  resume: boolean
  close: boolean
}

export interface AcpSessionInfoUpdateView {
  title?: string | null
  updatedAt?: string | null
}

export interface SessionModeView {
  id: string
  name: string
  description?: string
}

export interface ConfigOptionSelectChoiceView {
  value: string
  name: string
  description?: string
  group?: string
  groupName?: string
}

interface ConfigOptionBaseView {
  id: string
  name: string
  description?: string
  category?: string
}

export interface ConfigSelectOptionView extends ConfigOptionBaseView {
  type: "select"
  currentValue: string
  options: ConfigOptionSelectChoiceView[]
}

export interface ConfigBooleanOptionView extends ConfigOptionBaseView {
  type: "boolean"
  currentValue: boolean
}

export type ConfigOptionView = ConfigSelectOptionView | ConfigBooleanOptionView

export interface CommandView {
  name: string
  description: string
  inputHint?: string
}

export type AttachmentKind = "image" | "resource"

export interface AttachmentView {
  id: string
  kind: AttachmentKind
  name: string
  mimeType?: string
  uri?: string
  data?: string
  text?: string
}

export interface PermissionOptionView {
  optionId: string
  name: string
  kind?: string
}

export interface PermissionView {
  title: string
  options: PermissionOptionView[]
}

export interface PendingPermission {
  view: PermissionView
  resolve: (optionId: string | null) => void
}

/** One environment variable an env_var auth method asks the client to provide (e.g. OPENAI_API_KEY). */
export interface AuthEnvVarView {
  name: string
  label?: string
  secret: boolean
  optional: boolean
}

/** An authentication method advertised by the agent in its `initialize` response (or in an auth_required error). */
export interface AuthMethodView {
  id: string
  name: string
  label?: string
  type?: string
  description?: string
  link?: string
  vars: AuthEnvVarView[]
  meta?: Record<string, unknown>
}

export interface AuthChoice {
  methodId: string
  /** For env_var methods: credentials to inject as the spawned agent's environment on re-spawn. */
  env?: Record<string, string>
}

export type AuthPhase = "select" | "authenticating" | "complete"

/** Drives the in-chat authorization UI; mirrors {@link PendingPermission}. */
export interface PendingAuth {
  requestId?: string
  methods: AuthMethodView[]
  message?: string
  phase: AuthPhase
  /** Verification URL for an OAuth device flow, pushed by the agent while `authenticate` is pending. */
  authUri?: string
  error?: string
  onChoose: (choice: AuthChoice | null) => void
  onRetry?: () => void
  onOpenConfig?: () => void
}
