// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useState, type CSSProperties, type MouseEvent, type ReactNode } from "react"
import { AllIcons } from "@jetbrains/intellij-webview"
import renderMathInElement from "katex/contrib/auto-render"
import ReactMarkdown, { type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import rehypeSlug from "rehype-slug"
import remarkFrontmatter from "remark-frontmatter"
import remarkGfm from "remark-gfm"
import { MermaidBlock } from "./MermaidBlock"
import { markdownSanitizeSchema } from "./markdownSanitizeSchema"

declare global {
  namespace JSX {
    interface IntrinsicElements {
      "jb-icon": {
        src?: string
        label?: string
        size?: string
        className?: string
        "aria-hidden"?: boolean | "true" | "false"
      }
    }
  }
}

interface MarkdownPreviewAppProps {
  markdown: string
  scrollLine: number
  contentVersion: number
  changes: MarkdownChangedBlockDescriptor[]
  selection: MarkdownSourceRange | undefined
  theme: "light" | "dark"
  onOpenLink: (href: string) => void
  onResolveRunCommands: (request: MarkdownResolveRunCommandsRequest) => Promise<MarkdownResolvedRunCommandsResponse>
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

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

interface SourcePositionElement {
  element: HTMLElement
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

interface SourcePositionRange {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

export interface MarkdownSourceRange {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

interface MarkdownSourcePosition {
  start?: MarkdownSourcePoint
  end?: MarkdownSourcePoint
}

interface MarkdownSourcePoint {
  line?: number
  column?: number
}

interface MarkdownNode {
  type?: string
  value?: string
  position?: MarkdownSourcePosition
  data?: {
    hProperties?: Record<string, unknown>
    [key: string]: unknown
  }
  children?: MarkdownNode[]
}

interface HastNode {
  tagName?: string
  value?: string
  properties?: Record<string, unknown>
  children?: HastNode[]
}

interface TableOfContentsEntry {
  id: string
  text: string
  level: number
  element: HTMLElement
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
const headingSelector = "h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]"
const activeHeadingTopOffset = 80
const markdownResourcePrefix = "./__markdown-preview-resource/"
let scheduledScrollFrame: number | undefined

const runLineIcon = {
  src: () => AllIcons.src("expui/gutter/run.svg"),
}

const runBlockIcon = {
  src: () => AllIcons.src("expui/gutter/rerun.svg"),
}

const latexDelimiters = [
  { left: "$$", right: "$$", display: true },
  { left: "\\[", right: "\\]", display: true },
  { left: "\\(", right: "\\)", display: false },
  { left: "$", right: "$", display: false },
  { left: "\\begin{equation}", right: "\\end{equation}", display: true },
  { left: "\\begin{align}", right: "\\end{align}", display: true },
  { left: "\\begin{alignat}", right: "\\end{alignat}", display: true },
  { left: "\\begin{gather}", right: "\\end{gather}", display: true },
  { left: "\\begin{CD}", right: "\\end{CD}", display: true },
]

const remarkPlugins: Options["remarkPlugins"] = [
  remarkGfm,
  [remarkFrontmatter, ["yaml", "toml"]],
  remarkFrontmatterBlocks,
  remarkSourcePositionAttributes,
]
const rehypePlugins: Options["rehypePlugins"] = [
  rehypeRaw,
  rehypeSlug,
  [rehypeSanitize, markdownSanitizeSchema],
  [rehypeHighlight, { detect: true, plainText: ["mermaid", "text", "txt"] }],
]

export function MarkdownPreviewApp({
  markdown,
  scrollLine,
  contentVersion,
  changes,
  selection,
  theme,
  onOpenLink,
  onResolveRunCommands,
  onRunCommand,
}: MarkdownPreviewAppProps) {
  const commandCandidates: MarkdownCommandCandidate[] = []
  const [resolvedCommands, setResolvedCommands] = useState<{ contentVersion: number, commands: MarkdownCommandDescriptor[] }>({
    contentVersion: -1,
    commands: [],
  })
  const commands = resolvedCommands.contentVersion === contentVersion ? resolvedCommands.commands : []
  const commandLookup = createCommandLookup(commands)
  const components: Components = {
    a({ href, children, ...props }) {
      function handleClick(event: MouseEvent<HTMLAnchorElement>): void {
        if (!href) return
        event.preventDefault()
        onOpenLink(href)
      }

      return <a {...props} href={href} onClick={handleClick}>{children}</a>
    },
    img({ src, alt, ...props }) {
      return <img {...props} src={markdownResourceSrc(src)} alt={alt} />
    },
    pre({ node, className, children, ...props }) {
      const frontmatterLanguage = frontmatterLanguageFromPreNode(node)
      if (frontmatterLanguage) {
        return (
          <section className="frontmatterBlock">
            <div className="frontmatterHeader">{frontmatterTitle(frontmatterLanguage)}</div>
            <pre className={classNames("frontmatterPre", className)} {...props}>{children}</pre>
          </section>
        )
      }
      const sourcePosition = sourcePositionFromPreNode(node)
      const codeNode = codeNodeFromPreNode(node)
      if (sourcePosition && codeNode) {
        commandCandidates.push(...codeFenceCommandCandidates(sourcePosition, codeNode))
      }
      const blockCommand = sourcePosition ? findBlockCommand(commandLookup, sourcePosition) : undefined
      const lineCommands = sourcePosition ? findLineCommands(commandLookup, sourcePosition, blockCommand?.firstLineCommandId) : []
      if (!blockCommand && lineCommands.length === 0) {
        return <pre className={className} {...props}>{children}</pre>
      }
      return (
        <div className="codeFenceWithCommands">
          <pre className={className} {...props}>{children}</pre>
          <CodeFenceRunGutter
            contentVersion={contentVersion}
            sourcePosition={sourcePosition}
            blockCommand={blockCommand}
            lineCommands={lineCommands}
            onRunCommand={onRunCommand}
          />
        </div>
      )
    },
    code({ node, className, children, ...props }) {
      const code = codeToString(children).replace(/\n$/, "")
      if (className?.split(/\s+/).includes("language-mermaid")) {
        return <MermaidBlock chart={code} theme={theme} />
      }
      const sourcePosition = sourcePositionFromHastNode(node)
      if (sourcePosition && !hasLanguageClass(className)) {
        commandCandidates.push(inlineCommandCandidate(sourcePosition, code))
      }
      const inlineCommand = sourcePosition ? findInlineCommand(commandLookup, sourcePosition) : undefined
      if (inlineCommand) {
        return (
          <code className={className} {...props}>
            <RunCommandButton contentVersion={contentVersion} command={inlineCommand} variant="inline" onRunCommand={onRunCommand} />
            {children}
          </code>
        )
      }
      return <code className={className} {...props}>{children}</code>
    },
  }

  useEffect(() => {
    let cancelled = false
    void onResolveRunCommands({ contentVersion, candidates: uniqueCommandCandidates(commandCandidates) }).then(response => {
      if (!cancelled) setResolvedCommands({ contentVersion, commands: response.commands })
    })
    return () => {
      cancelled = true
    }
  }, [contentVersion, onResolveRunCommands])

  useEffect(() => {
    renderLatex()
  }, [markdown, theme])

  useEffect(() => {
    scrollMarkdownPreviewToLine(scrollLine)
    return cancelScheduledMarkdownPreviewScroll
  }, [markdown, scrollLine])

  useEffect(() => {
    decorateSourceBlocks(selection, changes)
    return clearSourceDecorations
  }, [markdown, selection, changes])

  return (
    <>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={components}
        urlTransform={url => url}
      >
        {markdown}
      </ReactMarkdown>
      <FloatingTableOfContents markdown={markdown} />
    </>
  )
}

interface FloatingTableOfContentsProps {
  markdown: string
}

function FloatingTableOfContents({ markdown }: FloatingTableOfContentsProps) {
  const [entries, setEntries] = useState<TableOfContentsEntry[]>([])
  const [expanded, setExpanded] = useState(false)
  const [activeId, setActiveId] = useState<string | undefined>()

  useEffect(() => {
    const nextEntries = collectTableOfContentsEntries()
    setEntries(nextEntries)
    setActiveId(nextEntries[0]?.id)
  }, [markdown])

  useEffect(() => {
    if (entries.length < 2) {
      setActiveId(undefined)
      return
    }

    let scheduledFrame: number | undefined
    const updateActiveHeading = (): void => {
      let active = entries[0].id
      for (const entry of entries) {
        if (entry.element.getBoundingClientRect().top <= activeHeadingTopOffset) {
          active = entry.id
        }
        else {
          break
        }
      }
      setActiveId(current => current === active ? current : active)
    }
    const scheduleUpdate = (): void => {
      if (scheduledFrame !== undefined) return
      scheduledFrame = window.requestAnimationFrame(() => {
        scheduledFrame = undefined
        updateActiveHeading()
      })
    }

    scheduleUpdate()
    window.addEventListener("scroll", scheduleUpdate, { passive: true })
    window.addEventListener("resize", scheduleUpdate)
    return () => {
      if (scheduledFrame !== undefined) window.cancelAnimationFrame(scheduledFrame)
      window.removeEventListener("scroll", scheduleUpdate)
      window.removeEventListener("resize", scheduleUpdate)
    }
  }, [entries])

  if (entries.length < 2) return null

  if (!expanded) {
    return (
      <button
        type="button"
        className="markdownTocRail"
        title="Table of contents"
        aria-label="Show table of contents"
        aria-expanded="false"
        onClick={() => setExpanded(true)}
      >
        <span className="markdownTocRailIcon" aria-hidden="true" />
      </button>
    )
  }

  function scrollToEntry(entry: TableOfContentsEntry): void {
    const target = document.getElementById(entry.id)
    if (!target) return
    target.scrollIntoView({ block: "start", behavior: "smooth" })
    setActiveId(entry.id)
  }

  return (
    <nav
      className="markdownTocPanel"
      aria-label="Table of contents"
      onKeyDown={event => {
        if (event.key === "Escape") setExpanded(false)
      }}
    >
      <div className="markdownTocHeader">
        <span className="markdownTocTitle">Contents</span>
        <button
          type="button"
          className="markdownTocCollapseButton"
          title="Collapse"
          aria-label="Collapse table of contents"
          aria-expanded="true"
          onClick={() => setExpanded(false)}
        >
          <span className="markdownTocCollapseIcon" aria-hidden="true" />
        </button>
      </div>
      <ol className="markdownTocList">
        {entries.map(entry => (
          <li key={entry.id} className="markdownTocItem">
            <button
              type="button"
              className={classNames("markdownTocLink", entry.id === activeId ? "is-active" : undefined)}
              style={{ paddingLeft: `${8 + Math.max(0, entry.level - 1) * 12}px` }}
              aria-current={entry.id === activeId ? "location" : undefined}
              title={entry.text}
              onClick={() => scrollToEntry(entry)}
            >
              <span className="markdownTocText">{entry.text}</span>
            </button>
          </li>
        ))}
      </ol>
    </nav>
  )
}

function collectTableOfContentsEntries(): TableOfContentsEntry[] {
  const contentElement = document.getElementById("content")
  if (!contentElement) return []

  return Array.from(contentElement.querySelectorAll<HTMLElement>(headingSelector))
    .map(heading => {
      const text = normalizeHeadingText(heading.textContent ?? "")
      if (!heading.id || !text || heading.classList.contains("sr-only") || heading.closest(".footnotes")) return undefined
      return {
        id: heading.id,
        text,
        level: headingLevel(heading),
        element: heading,
      }
    })
    .filter((entry): entry is TableOfContentsEntry => entry !== undefined)
}

function headingLevel(heading: HTMLElement): number {
  const level = Number(heading.tagName.substring(1))
  return Number.isFinite(level) ? Math.min(6, Math.max(1, level)) : 1
}

function normalizeHeadingText(text: string): string {
  return text.replace(/\s+/g, " ").trim()
}

function codeFenceCommandCandidates(sourcePosition: SourcePositionRange, codeNode: HastNode): MarkdownCommandCandidate[] {
  const code = hastText(codeNode)
  const language = codeFenceLanguage(codeNode)
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

function inlineCommandCandidate(sourcePosition: SourcePositionRange, rawCommand: string): MarkdownCommandCandidate {
  return {
    ...commandSource(sourcePosition),
    id: commandId("INLINE", sourcePosition, rawCommand),
    kind: "INLINE",
    rawCommand,
  }
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

function uniqueCommandCandidates(candidates: MarkdownCommandCandidate[]): MarkdownCommandCandidate[] {
  const result = new Map<string, MarkdownCommandCandidate>()
  for (const candidate of candidates) {
    result.set(candidate.id, candidate)
  }
  return Array.from(result.values())
}

function codeFenceLanguage(codeNode: HastNode): string | undefined {
  const classNames = hastClassNames(codeNode)
  const languageClass = classNames.find(className => className.startsWith("language-"))
  return languageClass?.substring("language-".length)
}

function hasLanguageClass(className: string | undefined): boolean {
  return className?.split(/\s+/).some(name => name.startsWith("language-")) ?? false
}

function hastClassNames(node: HastNode | undefined): string[] {
  const className = node?.properties?.className
  if (Array.isArray(className)) return className.filter((name): name is string => typeof name === "string")
  if (typeof className === "string") return className.split(/\s+/)
  return []
}

function hastText(node: HastNode | undefined): string {
  if (!node) return ""
  if (typeof node.value === "string") return node.value
  return node.children?.map(hastText).join("") ?? ""
}

interface CommandLookup {
  blockCommands: MarkdownCommandDescriptor[]
  lineCommands: MarkdownCommandDescriptor[]
  inlineCommands: Map<string, MarkdownCommandDescriptor>
}

function createCommandLookup(commands: MarkdownCommandDescriptor[]): CommandLookup {
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

function findBlockCommand(lookup: CommandLookup, sourcePosition: SourcePositionRange): MarkdownCommandDescriptor | undefined {
  return lookup.blockCommands.find(command => command.startLine === sourcePosition.startLine)
}

function findLineCommands(
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

function findInlineCommand(lookup: CommandLookup, sourcePosition: SourcePositionRange): MarkdownCommandDescriptor | undefined {
  return lookup.inlineCommands.get(positionKey(sourcePosition))
}

interface CodeFenceRunGutterProps {
  contentVersion: number
  sourcePosition: SourcePositionRange | undefined
  blockCommand: MarkdownCommandDescriptor | undefined
  lineCommands: MarkdownCommandDescriptor[]
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

function CodeFenceRunGutter({ contentVersion, sourcePosition, blockCommand, lineCommands, onRunCommand }: CodeFenceRunGutterProps) {
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

interface RunCommandButtonProps {
  contentVersion: number
  command: MarkdownCommandDescriptor
  variant: "block" | "line" | "inline"
  style?: CSSProperties
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

function RunCommandButton({ contentVersion, command, variant, style, onRunCommand }: RunCommandButtonProps) {
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

function runCommandIcon(variant: "block" | "line" | "inline"): typeof runLineIcon {
  return variant === "block" ? runBlockIcon : runLineIcon
}

function renderLatex(): void {
  const contentElement = document.getElementById("content")
  if (!contentElement) return

  renderMathInElement(contentElement, {
    delimiters: latexDelimiters,
    ignoredClasses: ["katex"],
    throwOnError: false,
  })
}

function codeToString(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map(codeToString).join("")
  }
  return ""
}

function markdownResourceSrc(src: string | undefined): string | undefined {
  if (!src || !isLocalMarkdownResource(src)) return src
  return `${markdownResourcePrefix}${base64UrlEncode(src)}`
}

function isLocalMarkdownResource(src: string): boolean {
  const trimmed = src.trim()
  if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("//")) return false
  const scheme = trimmed.match(/^([A-Za-z][A-Za-z\d+.-]*):/)?.[1]?.toLowerCase()
  return scheme === undefined || scheme === "file"
}

function base64UrlEncode(value: string): string {
  const bytes = new TextEncoder().encode(value)
  let binary = ""
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "")
}

export function scrollMarkdownPreviewToLine(line: number): void {
  cancelScheduledMarkdownPreviewScroll()
  scheduledScrollFrame = window.requestAnimationFrame(() => {
    scheduledScrollFrame = undefined
    scrollToSourceLine(line)
  })
}

function cancelScheduledMarkdownPreviewScroll(): void {
  if (scheduledScrollFrame === undefined) return
  window.cancelAnimationFrame(scheduledScrollFrame)
  scheduledScrollFrame = undefined
}

function scrollToSourceLine(line: number): void {
  const contentElement = document.getElementById("content")
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

function decorateSourceBlocks(selection: MarkdownSourceRange | undefined, changes: MarkdownChangedBlockDescriptor[]): void {
  const contentElement = document.getElementById("content")
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

function clearSourceDecorations(root: HTMLElement | null = document.getElementById("content")): void {
  if (!root) return
  root.querySelectorAll<HTMLElement>(sourceDecorationClassSelector).forEach(element => {
    element.classList.remove(...sourceDecorationClassNames)
  })
  root.querySelectorAll<HTMLElement>(`.${removedBlockPlaceholderClassName}`).forEach(placeholder => {
    placeholder.remove()
  })
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

function sourcePositionFromPreNode(node: unknown): SourcePositionRange | undefined {
  const prePosition = sourcePositionFromHastNode(node)
  if (prePosition) return prePosition

  return sourcePositionFromHastNode(codeNodeFromPreNode(node))
}

function codeNodeFromPreNode(node: unknown): HastNode | undefined {
  return (node as HastNode | undefined)?.children?.find(child => child.tagName === "code")
}

function sourcePositionFromHastNode(node: unknown): SourcePositionRange | undefined {
  const value = (node as HastNode | undefined)?.properties?.dataSourcepos
  return typeof value === "string" ? parseSourcePosition(value) : undefined
}

function positionKey(sourcePosition: SourcePositionRange): string {
  return `${sourcePosition.startLine}:${sourcePosition.startColumn}-${sourcePosition.endLine}:${sourcePosition.endColumn}`
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

function remarkFrontmatterBlocks() {
  return (tree: unknown) => transformFrontmatterNodes(tree as MarkdownNode)
}

function transformFrontmatterNodes(node: MarkdownNode): void {
  if (!node.children) return

  node.children = node.children.map(child => {
    if (isFrontmatterNode(child)) {
      return frontmatterCodeNode(child)
    }
    transformFrontmatterNodes(child)
    return child
  })
}

function isFrontmatterNode(node: MarkdownNode): boolean {
  return node.type === "yaml" || node.type === "toml"
}

function frontmatterCodeNode(node: MarkdownNode): MarkdownNode {
  const language = node.type === "toml" ? "toml" : "yaml"
  return {
    type: "code",
    value: node.value ?? "",
    position: node.position,
    data: {
      ...node.data,
      hProperties: {
        ...node.data?.hProperties,
        className: [`language-${language}`, "frontmatterCode"],
        dataFrontmatter: language,
      },
    },
  }
}

function frontmatterLanguageFromPreNode(node: unknown): string | undefined {
  const codeNode = codeNodeFromPreNode(node)
  const language = codeNode?.properties?.dataFrontmatter
  return typeof language === "string" ? language : undefined
}

function frontmatterTitle(language: string): string {
  return language === "toml" ? "Front matter (TOML)" : "Front matter (YAML)"
}

function classNames(...names: Array<string | undefined>): string | undefined {
  const className = names.filter(Boolean).join(" ")
  return className || undefined
}

function remarkSourcePositionAttributes() {
  return (tree: unknown) => addSourcePositionAttributes(tree as MarkdownNode)
}

function addSourcePositionAttributes(node: MarkdownNode): void {
  const position = node.position
  if (position?.start?.line && position.end?.line) {
    node.data ??= {}
    node.data.hProperties = {
      ...node.data.hProperties,
      dataSourcepos: `${position.start.line}:${position.start.column ?? 1}-${position.end.line}:${position.end.column ?? 1}`,
    }
  }

  node.children?.forEach(addSourcePositionAttributes)
}
