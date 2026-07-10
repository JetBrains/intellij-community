// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { cloneElement, Fragment, isValidElement, type ReactElement, type ReactNode } from "react"
import type { MarkdownPathLinkCandidate } from "./markdownPreviewTypes"

interface MarkdownNavigatePathLinkRequest {
  contentVersion: number
  rawPath: string
  clientX: number
  clientY: number
}

interface PathToken {
  rawPath: string
  start: number
  end: number
}

interface PathTextContent {
  text: string
  leaves: PathTextLeaf[]
}

interface PathTextLeaf {
  text: string
  start: number
  end: number
  wrappers: Array<ReactElement<{ children?: ReactNode }>>
}

// Keep this in sync with community/plugins/ui.webview/demo/webview-src/views/acp-chat/src/components/markdownPathLinks.tsx; extraction is intentionally deferred.
export function collectPathLinkCandidates(markdown: string): MarkdownPathLinkCandidate[] {
  const codeSegments = markdownCodeSegments(markdown)
  const candidates: MarkdownPathLinkCandidate[] = []
  const seen = new Set<string>()
  for (const codeSegment of codeSegments) {
    for (const token of pathTokens(codeSegment)) {
      if (seen.has(token.rawPath)) continue
      seen.add(token.rawPath)
      candidates.push({ id: `path-${candidates.length}`, rawPath: token.rawPath })
    }
  }
  return candidates
}

export function renderPathLinks(
  node: ReactNode,
  resolvedRawPaths: ReadonlySet<string>,
  keyPrefix: string,
  contentVersion: number,
  onNavigatePathLink: (request: MarkdownNavigatePathLinkRequest) => void,
): ReactNode {
  const content = pathTextContent(node)
  const tokens = pathTokens(content.text).filter(token => resolvedRawPaths.has(token.rawPath))
  if (tokens.length === 0) return node

  const parts: ReactNode[] = []
  let offset = 0
  for (const [index, token] of tokens.entries()) {
    if (token.start < offset) continue
    if (offset < token.start) {
      parts.push(...renderPathTextRange(content.leaves, offset, token.start, `${keyPrefix}-text-${index}`))
    }
    parts.push(
      <button
        key={`${keyPrefix}-${token.start}-${index}`}
        type="button"
        className="markdownPathLink"
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          onNavigatePathLink({
            contentVersion,
            rawPath: token.rawPath,
            clientX: Math.round(event.clientX),
            clientY: Math.round(event.clientY),
          })
        }}
      >
        {renderPathTextRange(content.leaves, token.start, token.end, `${keyPrefix}-link-${index}`)}
      </button>,
    )
    offset = token.end
  }
  if (offset < content.text.length) {
    parts.push(...renderPathTextRange(content.leaves, offset, content.text.length, `${keyPrefix}-text-end`))
  }
  return parts
}

function markdownCodeSegments(markdown: string): string[] {
  const segments: string[] = []
  const markdownWithoutFencedCode = markdown.replace(FENCED_CODE_BLOCK_PATTERN, (match, _prefix, _fence, info, code) => {
    const language = String(info).trim().split(/\s+/)[0]?.toLowerCase()
    if (language !== "mermaid") {
      segments.push(String(code))
    }
    return " ".repeat(match.length)
  })

  for (const match of markdownWithoutFencedCode.matchAll(INLINE_CODE_PATTERN)) {
    segments.push(match[1])
  }
  return segments
}

function pathTextContent(node: ReactNode): PathTextContent {
  const leaves: PathTextLeaf[] = []
  let text = ""

  function collect(current: ReactNode, wrappers: Array<ReactElement<{ children?: ReactNode }>>): void {
    if (typeof current === "string" || typeof current === "number") {
      const value = String(current)
      if (value.length === 0) return
      const start = text.length
      text += value
      leaves.push({ text: value, start, end: text.length, wrappers })
      return
    }
    if (Array.isArray(current)) {
      current.forEach(child => collect(child, wrappers))
      return
    }
    if (isValidElement(current)) {
      const element = current as ReactElement<{ children?: ReactNode }>
      if (element.props.children == null) return
      collect(element.props.children, [...wrappers, element])
    }
  }

  collect(node, [])
  return { text, leaves }
}

function renderPathTextRange(leaves: PathTextLeaf[], start: number, end: number, keyPrefix: string): ReactNode[] {
  const parts: ReactNode[] = []
  for (const leaf of leaves) {
    const sliceStart = Math.max(start, leaf.start)
    const sliceEnd = Math.min(end, leaf.end)
    if (sliceStart >= sliceEnd) continue
    parts.push(renderPathTextLeafSlice(leaf, sliceStart, sliceEnd, `${keyPrefix}-${parts.length}`))
  }
  return parts
}

function renderPathTextLeafSlice(leaf: PathTextLeaf, start: number, end: number, key: string): ReactNode {
  let result: ReactNode = leaf.text.slice(start - leaf.start, end - leaf.start)
  for (let index = leaf.wrappers.length - 1; index >= 0; index--) {
    result = cloneElement(leaf.wrappers[index], undefined, result)
  }
  return <Fragment key={key}>{result}</Fragment>
}

function pathTokens(text: string): PathToken[] {
  const tokens: PathToken[] = []
  let lineStart = 0
  while (lineStart <= text.length) {
    const nextLineBreak = text.indexOf("\n", lineStart)
    const lineEnd = nextLineBreak < 0 ? text.length : nextLineBreak
    tokens.push(...pathTokensInLine(text, lineStart, lineEnd))
    if (nextLineBreak < 0) break
    lineStart = nextLineBreak + 1
  }
  return tokens
}

function pathTokensInLine(text: string, lineStart: number, lineEnd: number): PathToken[] {
  const contentStart = firstNonWhitespaceOffset(text, lineStart, lineEnd)
  if (contentStart === undefined) return []
  const contentEnd = lastNonWhitespaceOffset(text, contentStart, lineEnd)
  const lineText = text.slice(contentStart, contentEnd)
  const linePath = trimPathCandidate(lineText)
  if (isStandalonePathLine(linePath)) {
    const leadingTrim = lineText.indexOf(linePath)
    const start = contentStart + leadingTrim
    return [{ rawPath: linePath, start, end: start + linePath.length }]
  }

  return pathTokenChunks(text, lineStart, lineEnd)
}

function pathTokenChunks(text: string, startOffset: number, endOffset: number): PathToken[] {
  const tokens: PathToken[] = []
  let chunkStart: number | undefined
  for (let offset = startOffset; offset <= endOffset; offset++) {
    if (offset < endOffset && !isPathTokenSeparator(text[offset])) {
      chunkStart ??= offset
      continue
    }
    if (chunkStart === undefined) continue

    const chunk = text.slice(chunkStart, offset)
    const rawPath = trimPathCandidate(chunk)
    if (rawPath && isPathLike(rawPath)) {
      const leadingTrim = chunk.indexOf(rawPath)
      const start = chunkStart + leadingTrim
      tokens.push({ rawPath, start, end: start + rawPath.length })
    }
    chunkStart = undefined
  }
  return tokens
}

function firstNonWhitespaceOffset(text: string, startOffset: number, endOffset: number): number | undefined {
  for (let offset = startOffset; offset < endOffset; offset++) {
    if (!isWhitespace(text[offset])) return offset
  }
  return undefined
}

function lastNonWhitespaceOffset(text: string, startOffset: number, endOffset: number): number {
  let offset = endOffset
  while (offset > startOffset && isWhitespace(text[offset - 1])) offset--
  return offset
}

function trimPathCandidate(candidate: string): string {
  let start = 0
  let end = candidate.length
  while (start < end && PATH_TRIM_START.has(candidate[start])) start++
  while (end > start && PATH_TRIM_END.has(candidate[end - 1])) end--
  return candidate.slice(start, end)
}

function isPathLike(rawPath: string): boolean {
  return !URL_SCHEME_PATTERN.test(rawPath) && (rawPath.includes("/") || rawPath.includes("\\") || FILE_EXTENSION_PATTERN.test(rawPath))
}

function isStandalonePathLine(rawPath: string): boolean {
  return rawPath.length > 0 && !HAS_WHITESPACE_PATTERN.test(rawPath) && isPathLike(rawPath)
}

function isPathTokenSeparator(char: string): boolean {
  return isWhitespace(char) || PATH_TOKEN_SEPARATORS.has(char)
}

function isWhitespace(char: string): boolean {
  return WHITESPACE_PATTERN.test(char)
}

const FENCED_CODE_BLOCK_PATTERN = /(^|\n)(`{3,}|~{3,})([^\n]*)\n([\s\S]*?)\n\2(?=\n|$)/g
const INLINE_CODE_PATTERN = /`([^`\n]+)`/g
const FILE_EXTENSION_PATTERN = /\.[A-Za-z0-9]+(?:#L\d+|:\d+(?::\d+)?)?$/
const WHITESPACE_PATTERN = /\s/
const HAS_WHITESPACE_PATTERN = /\s/
const PATH_TOKEN_SEPARATORS = new Set(["`", "<", ">", "\"", "'", "(", ")", "[", "]", "{", "}"])
const PATH_TRIM_START = new Set(["(", "[", "{", "<"])
const PATH_TRIM_END = new Set([")", "]", "}", ">", ".", ",", ";"])
const URL_SCHEME_PATTERN = /^[a-z][a-z0-9+.-]*:\/\//i
