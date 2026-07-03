// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export interface MarkdownCommandDescriptor {
  id: string
  kind: MarkdownCommandKind
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  title: string
  firstLineCommandId?: string
}

export type MarkdownCommandKind = "BLOCK" | "LINE" | "INLINE"

export interface MarkdownCommandCandidate {
  id: string
  kind: MarkdownCommandKind
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  rawCommand: string
  language?: string
  firstLineCommandId?: string
}

export interface MarkdownResolveRunCommandsRequest {
  contentVersion: number
  candidates: MarkdownCommandCandidate[]
}

export interface MarkdownResolvedRunCommandsResponse {
  commands: MarkdownCommandDescriptor[]
}

export type MarkdownChangedBlockKind = "ADDED" | "MODIFIED" | "REMOVED"

export interface MarkdownChangedBlockDescriptor {
  kind: MarkdownChangedBlockKind
  startLine: number
  endLine: number
}

export interface MarkdownRunCommandRequest {
  contentVersion: number
  id: string
  clientX: number
  clientY: number
}

export interface MarkdownPathLinkCandidate {
  id: string
  rawPath: string
}

export interface MarkdownResolvePathLinksRequest {
  contentVersion: number
  candidates: MarkdownPathLinkCandidate[]
}

export interface MarkdownResolvedPathLinksResponse {
  resolvedIds: string[]
}

export interface MarkdownNavigatePathLinkRequest {
  contentVersion: number
  rawPath: string
  clientX: number
  clientY: number
}

export interface MarkdownSourceRange {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}
