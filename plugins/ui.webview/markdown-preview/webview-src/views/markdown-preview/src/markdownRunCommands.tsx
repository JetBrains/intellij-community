// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { AllIcons } from "@jetbrains/intellij-webview"
import "@jetbrains/intellij-webview-controls/define/icon"
import type { CSSProperties, MouseEvent } from "react"
import { hastClassNames, hastText, type HastNode } from "./markdownHastUtils"
import { classNames } from "./markdownReactUtils"
import { positionKey, type SourcePositionRange } from "./markdownSourcePositions"
import type { MarkdownCommandCandidate, MarkdownCommandDescriptor, MarkdownCommandKind, MarkdownRunCommandRequest } from "./markdownPreviewTypes"

interface CommandLookup {
  blockCommands: MarkdownCommandDescriptor[]
  lineCommands: MarkdownCommandDescriptor[]
  inlineCommands: Map<string, MarkdownCommandDescriptor>
}

interface CodeFenceRunGutterProps {
  contentVersion: number
  sourcePosition: SourcePositionRange | undefined
  blockCommand: MarkdownCommandDescriptor | undefined
  lineCommands: MarkdownCommandDescriptor[]
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

interface RunCommandButtonProps {
  contentVersion: number
  command: MarkdownCommandDescriptor
  variant: "block" | "line" | "inline"
  style?: CSSProperties
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

const runLineIcon = {
  src: () => AllIcons.src("expui/gutter/run.svg"),
}

const runBlockIcon = {
  src: () => AllIcons.src("expui/gutter/rerun.svg"),
}

export function codeFenceCommandCandidates(sourcePosition: SourcePositionRange, codeNode: HastNode): MarkdownCommandCandidate[] {
  const code = hastText(codeNode)
  const language = codeFenceLanguage(codeNode)
  if (isMermaidLanguage(language)) return []
  const lineCommands = lineCommandCandidates(sourcePosition, code)
  const result: MarkdownCommandCandidate[] = []
  if (language) {
    result.push({
      ...commandSource(sourcePosition),
      id: commandId("BLOCK", sourcePosition, code),
      kind: "BLOCK",
      rawCommand: code,
      language,
      firstLineCommandId: lineCommands.length > 1 ? lineCommands[0].id : undefined,
    })
  }
  result.push(...lineCommands)
  return result
}

export function inlineCommandCandidate(sourcePosition: SourcePositionRange, rawCommand: string): MarkdownCommandCandidate {
  return {
    ...commandSource(sourcePosition),
    id: commandId("INLINE", sourcePosition, rawCommand),
    kind: "INLINE",
    rawCommand,
  }
}

export function uniqueCommandCandidates(candidates: MarkdownCommandCandidate[]): MarkdownCommandCandidate[] {
  const result = new Map<string, MarkdownCommandCandidate>()
  for (const candidate of candidates) {
    result.set(candidate.id, candidate)
  }
  return Array.from(result.values())
}

export function isMermaidCodeNode(codeNode: HastNode): boolean {
  return isMermaidLanguage(codeFenceLanguage(codeNode))
}

export function hasLanguageClass(className: string | undefined): boolean {
  return className?.split(/\s+/).some(name => name.startsWith("language-")) ?? false
}

export function createCommandLookup(commands: MarkdownCommandDescriptor[]): CommandLookup {
  const inlineCommands = new Map<string, MarkdownCommandDescriptor>()
  const blockCommands: MarkdownCommandDescriptor[] = []
  const lineCommands: MarkdownCommandDescriptor[] = []
  for (const command of commands) {
    if (command.kind === "BLOCK") {
      blockCommands.push(command)
    }
    else if (command.kind === "LINE") {
      lineCommands.push(command)
    }
    else if (command.kind === "INLINE") {
      inlineCommands.set(positionKey(command), command)
    }
  }
  return { blockCommands, lineCommands, inlineCommands }
}

export function findBlockCommand(lookup: CommandLookup, sourcePosition: SourcePositionRange): MarkdownCommandDescriptor | undefined {
  return lookup.blockCommands.find(command => command.startLine === sourcePosition.startLine)
}

export function findLineCommands(
  lookup: CommandLookup,
  sourcePosition: SourcePositionRange,
  blockFirstLineCommandId: string | undefined
): MarkdownCommandDescriptor[] {
  return lookup.lineCommands.filter(command => {
    return command.id !== blockFirstLineCommandId
      && command.startLine > sourcePosition.startLine
      && command.endLine <= sourcePosition.endLine
  })
}

export function findInlineCommand(lookup: CommandLookup, sourcePosition: SourcePositionRange): MarkdownCommandDescriptor | undefined {
  return lookup.inlineCommands.get(positionKey(sourcePosition))
}

export function CodeFenceRunGutter({ contentVersion, sourcePosition, blockCommand, lineCommands, onRunCommand }: CodeFenceRunGutterProps) {
  if (!sourcePosition || (!blockCommand && lineCommands.length === 0)) return null
  const contentStartLine = sourcePosition.startLine + 1
  return (
    <div className="codeFenceRunGutter" aria-hidden={false}>
      {blockCommand && <RunCommandButton contentVersion={contentVersion} command={blockCommand} variant="block" onRunCommand={onRunCommand} />}
      {lineCommands.map(command => (
        <RunCommandButton
          key={command.id}
          contentVersion={contentVersion}
          command={command}
          variant="line"
          style={{ top: `calc(${Math.max(0, command.startLine - contentStartLine)} * var(--markdown-code-line-height))` }}
          onRunCommand={onRunCommand}
        />
      ))}
    </div>
  )
}

export function RunCommandButton({ contentVersion, command, variant, style, onRunCommand }: RunCommandButtonProps) {
  function handleClick(event: MouseEvent<HTMLButtonElement>): void {
    event.preventDefault()
    event.stopPropagation()
    onRunCommand({ contentVersion, id: command.id, clientX: Math.round(event.clientX), clientY: Math.round(event.clientY) })
  }

  return (
    <button
      type="button"
      className={classNames("markdownRunButton", `is-${variant}`)}
      title={command.title}
      aria-label={command.title}
      style={style}
      onClick={handleClick}
    >
      <jb-icon className="markdownRunIcon" src={runCommandIcon(variant).src()} aria-hidden={true} />
    </button>
  )
}

function lineCommandCandidates(sourcePosition: SourcePositionRange, code: string): MarkdownCommandCandidate[] {
  const result: MarkdownCommandCandidate[] = []
  let offset = 0
  let lineIndex = 0
  while (offset < code.length) {
    const delimiter = code.indexOf("\n", offset)
    const lineEndOffset = delimiter < 0 ? code.length : delimiter
    const rawCommand = code.slice(offset, lineEndOffset)
    const lineSource = codeLineSourcePosition(sourcePosition, lineIndex, rawCommand)
    result.push({
      ...commandSource(lineSource),
      id: commandId("LINE", lineSource, rawCommand),
      kind: "LINE",
      rawCommand,
    })
    if (delimiter < 0) break
    offset = delimiter + 1
    lineIndex++
  }
  return result
}

function commandSource(sourcePosition: SourcePositionRange): Pick<MarkdownCommandCandidate, "startLine" | "startColumn" | "endLine" | "endColumn"> {
  return {
    startLine: sourcePosition.startLine,
    startColumn: sourcePosition.startColumn,
    endLine: sourcePosition.endLine,
    endColumn: sourcePosition.endColumn,
  }
}

function codeLineSourcePosition(sourcePosition: SourcePositionRange, lineIndex: number, rawCommand: string): SourcePositionRange {
  const line = sourcePosition.startLine + lineIndex + 1
  return {
    startLine: line,
    startColumn: 1,
    endLine: line,
    endColumn: rawCommand.length + 1,
  }
}

function commandId(kind: MarkdownCommandKind, sourcePosition: SourcePositionRange, rawCommand: string): string {
  return `${kind}:${positionKey(sourcePosition)}:${hashString(rawCommand)}`
}

function hashString(value: string): string {
  let hash = 0
  for (let index = 0; index < value.length; index++) {
    hash = (Math.imul(hash, 31) + value.charCodeAt(index)) | 0
  }
  return (hash >>> 0).toString(16)
}

function codeFenceLanguage(codeNode: HastNode): string | undefined {
  const classNames = hastClassNames(codeNode)
  const languageClass = classNames.find(className => className.startsWith("language-"))
  return languageClass?.substring("language-".length)
}

function isMermaidLanguage(language: string | undefined): boolean {
  return language?.toLowerCase() === "mermaid"
}

function runCommandIcon(variant: "block" | "line" | "inline"): typeof runLineIcon {
  return variant === "block" ? runBlockIcon : runLineIcon
}
