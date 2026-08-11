// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { codeNodeFromPreNode, type HastNode } from "./markdownHastUtils"

export interface SourcePositionRange {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

interface SourcePositionElement extends SourcePositionRange {
  element: HTMLElement
}

interface MarkdownChangedBlockDescriptor {
  kind: "ADDED" | "MODIFIED" | "REMOVED"
  startLine: number
  endLine: number
}

const sourcePositionPattern = /^(\d+):(\d+)-(\d+):(\d+)$/
const sourceDecorationClassNames = ["is-source-selected", "is-vcs-added", "is-vcs-modified"]
const sourceDecorationClassSelector = sourceDecorationClassNames.map(className => `.${className}`).join(", ")
const sourceDecorationBlockTagNames = new Set([
  "BLOCKQUOTE",
  "DD",
  "DETAILS",
  "DIV",
  "DL",
  "DT",
  "H1",
  "H2",
  "H3",
  "H4",
  "H5",
  "H6",
  "LI",
  "OL",
  "P",
  "PRE",
  "SECTION",
  "TABLE",
  "TBODY",
  "TD",
  "TFOOT",
  "TH",
  "THEAD",
  "TR",
  "UL",
])
const removedBlockPlaceholderClassName = "markdownRemovedBlockPlaceholder"
let scheduledScrollFrame: number | undefined

export function scrollMarkdownPreviewToLine(line: number): void {
  cancelScheduledMarkdownPreviewScroll()
  scheduledScrollFrame = window.requestAnimationFrame(() => {
    scheduledScrollFrame = undefined
    scrollToSourceLine(line)
  })
}

export function cancelScheduledMarkdownPreviewScroll(): void {
  if (scheduledScrollFrame === undefined) return
  window.cancelAnimationFrame(scheduledScrollFrame)
  scheduledScrollFrame = undefined
}

export function decorateSourceBlocks(selection: SourcePositionRange | undefined, changes: MarkdownChangedBlockDescriptor[]): void {
  const contentElement = markdownContentElement()
  if (!contentElement) return

  clearSourceDecorations(contentElement)
  const elements = sourceDecorationElements(contentElement)

  if (selection) {
    for (const element of elements) {
      if (sourceRangesIntersect(element, selection)) {
        element.element.classList.add("is-source-selected")
      }
    }
  }

  for (const change of changes) {
    if (change.kind === "REMOVED") {
      insertRemovedBlockPlaceholder(contentElement, elements, change)
      continue
    }
    const className = change.kind === "ADDED" ? "is-vcs-added" : "is-vcs-modified"
    for (const element of elements) {
      if (sourceLinesIntersect(element, change)) {
        element.element.classList.add(className)
      }
    }
  }
}

export function clearSourceDecorations(root: HTMLElement | null = markdownContentElement()): void {
  if (!root) return
  root.querySelectorAll<HTMLElement>(sourceDecorationClassSelector).forEach(element => {
    element.classList.remove(...sourceDecorationClassNames)
  })
  root.querySelectorAll<HTMLElement>(`.${removedBlockPlaceholderClassName}`).forEach(placeholder => {
    placeholder.remove()
  })
}

export function sourcePositionFromPreNode(node: unknown): SourcePositionRange | undefined {
  const prePosition = sourcePositionFromHastNode(node)
  if (prePosition) return prePosition

  return sourcePositionFromHastNode(codeNodeFromPreNode(node))
}

export function sourcePositionFromHastNode(node: unknown): SourcePositionRange | undefined {
  const value = (node as HastNode | undefined)?.properties?.dataSourcepos
  return typeof value === "string" ? parseSourcePosition(value) : undefined
}

export function positionKey(sourcePosition: SourcePositionRange): string {
  return `${sourcePosition.startLine}:${sourcePosition.startColumn}-${sourcePosition.endLine}:${sourcePosition.endColumn}`
}

function scrollToSourceLine(line: number): void {
  const contentElement = markdownContentElement()
  if (!contentElement) return

  const targetLine = Math.max(1, line + 1)
  const target = findElementForLine(sourcePositionElements(contentElement), targetLine)
  if (target) {
    target.scrollIntoView({ block: "start", behavior: "instant" })
  }
  else if (targetLine === 1) {
    window.scrollTo({ top: 0, behavior: "instant" })
  }
}

function markdownContentElement(): HTMLElement | null {
  return document.querySelector<HTMLElement>(".markdownPreviewContent") ?? document.getElementById("content")
}

function sourcePositionElements(root: HTMLElement): SourcePositionElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>("[data-sourcepos]"))
    .map(element => {
      const sourcePosition = parseSourcePosition(element.dataset.sourcepos)
      return sourcePosition ? { element, ...sourcePosition } : undefined
    })
    .filter((element): element is SourcePositionElement => element !== undefined)
}

function sourceDecorationElements(root: HTMLElement): SourcePositionElement[] {
  const elements: SourcePositionElement[] = []
  const seenTargets = new Set<HTMLElement>()
  for (const sourcePosition of sourcePositionElements(root)) {
    const target = sourceDecorationTarget(sourcePosition.element)
    if (!target || seenTargets.has(target)) continue

    seenTargets.add(target)
    elements.push({ ...sourcePosition, element: target })
  }
  return elements
}

function sourceDecorationTarget(element: HTMLElement): HTMLElement | undefined {
  if (sourceDecorationBlockTagNames.has(element.tagName)) return element
  if (element.tagName === "CODE" && element.parentElement?.tagName === "PRE") return element.parentElement
  return undefined
}

function sourceRangesIntersect(first: SourcePositionRange, second: SourcePositionRange): boolean {
  if (first.endLine < second.startLine || second.endLine < first.startLine) return false
  if (first.endLine === second.startLine && first.endColumn < second.startColumn) return false
  if (second.endLine === first.startLine && second.endColumn < first.startColumn) return false
  return true
}

function sourceLinesIntersect(sourcePosition: SourcePositionRange, change: MarkdownChangedBlockDescriptor): boolean {
  return sourcePosition.startLine <= change.endLine && change.startLine <= sourcePosition.endLine
}

function insertRemovedBlockPlaceholder(root: HTMLElement, elements: SourcePositionElement[], change: MarkdownChangedBlockDescriptor): void {
  const placeholder = document.createElement("div")
  placeholder.className = removedBlockPlaceholderClassName
  placeholder.setAttribute("aria-hidden", "true")

  const anchorElement = elements.find(element => element.startLine >= change.startLine)?.element
  const insertionTarget = anchorElement ? directChildOf(root, anchorElement) : undefined
  root.insertBefore(placeholder, insertionTarget ?? null)
}

function directChildOf(root: HTMLElement, element: HTMLElement): HTMLElement | undefined {
  let current = element
  while (current.parentElement && current.parentElement !== root && root.contains(current.parentElement)) {
    current = current.parentElement
  }
  return current.parentElement === root ? current : undefined
}

function parseSourcePosition(sourcePosition: string | undefined): SourcePositionRange | undefined {
  const match = sourcePosition?.match(sourcePositionPattern)
  if (!match) return undefined

  return {
    startLine: Number(match[1]),
    startColumn: Number(match[2]),
    endLine: Number(match[3]),
    endColumn: Number(match[4]),
  }
}

function findElementForLine(elements: SourcePositionElement[], targetLine: number): HTMLElement | undefined {
  let containingElement: SourcePositionElement | undefined
  let nextElement: SourcePositionElement | undefined
  let previousElement: SourcePositionElement | undefined

  for (const element of elements) {
    if (element.startLine <= targetLine && targetLine <= element.endLine) {
      if (!containingElement || lineSpan(element) < lineSpan(containingElement)) {
        containingElement = element
      }
      continue
    }
    if (element.startLine > targetLine) {
      nextElement = element
      break
    }
    previousElement = element
  }

  return containingElement?.element ?? nextElement?.element ?? previousElement?.element
}

function lineSpan(element: SourcePositionElement): number {
  return element.endLine - element.startLine
}
