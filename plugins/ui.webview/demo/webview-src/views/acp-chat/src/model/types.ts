// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/** UI view models shared across the ACP chat view. These are intentionally decoupled from the ACP SDK wire types. */

export interface AgentInfo {
  id: string
  name: string
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
  content: string
  status: PlanStatus
  priority?: string
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
