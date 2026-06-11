// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useState, type CSSProperties, type MouseEvent, type ReactNode } from "react"
import renderMathInElement from "katex/contrib/auto-render"
import "katex/dist/katex.min.css"
import ReactMarkdown, { type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import rehypeSlug from "rehype-slug"
import remarkFrontmatter from "remark-frontmatter"
import remarkGfm from "remark-gfm"
import { MermaidBlock } from "./MermaidBlock"
import { markdownSanitizeSchema } from "./markdownSanitizeSchema"

interface MarkdownPreviewAppProps {
  markdown: string
  scrollLine: number
  commands: MarkdownCommandDescriptor[]
  changes: MarkdownChangedBlockDescriptor[]
  selection: MarkdownSourceRange | undefined
  theme: "light" | "dark"
  onOpenLink: (href: string) => void
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

export type MarkdownChangedBlockKind = "ADDED" | "MODIFIED" | "REMOVED"

export interface MarkdownChangedBlockDescriptor {
  kind: MarkdownChangedBlockKind
  startLine: number
  endLine: number
}

export interface MarkdownRunCommandRequest {
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
const markdownIconPrefix = "./__markdown-preview-icon/"
let scheduledScrollFrame: number | undefined

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

export function MarkdownPreviewApp({ markdown, scrollLine, commands, changes, selection, theme, onOpenLink, onRunCommand }: MarkdownPreviewAppProps) {
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
      const blockCommand = sourcePosition ? findBlockCommand(commandLookup, sourcePosition) : undefined
      const lineCommands = sourcePosition ? findLineCommands(commandLookup, sourcePosition, blockCommand?.firstLineCommandId) : []
      if (!blockCommand && lineCommands.length === 0) {
        return <pre className={className} {...props}>{children}</pre>
      }
      return (
        <div className="codeFenceWithCommands">
          <pre className={className} {...props}>{children}</pre>
          <CodeFenceRunGutter
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
      const inlineCommand = sourcePosition ? findInlineCommand(commandLookup, sourcePosition) : undefined
      if (inlineCommand) {
        return (
          <code className={className} {...props}>
            <RunCommandButton command={inlineCommand} variant="inline" onRunCommand={onRunCommand} />
            {children}
          </code>
        )
      }
      return <code className={className} {...props}>{children}</code>
    },
  }

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
  sourcePosition: SourcePositionRange | undefined
  blockCommand: MarkdownCommandDescriptor | undefined
  lineCommands: MarkdownCommandDescriptor[]
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

function CodeFenceRunGutter({ sourcePosition, blockCommand, lineCommands, onRunCommand }: CodeFenceRunGutterProps) {
  if (!sourcePosition || (!blockCommand && lineCommands.length === 0)) return null
  const contentStartLine = sourcePosition.startLine + 1
  return (
    <div className="codeFenceRunGutter" aria-hidden={false}>
      {blockCommand && <RunCommandButton command={blockCommand} variant="block" onRunCommand={onRunCommand} />}
      {lineCommands.map(command => (
        <RunCommandButton
          key={command.id}
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
  command: MarkdownCommandDescriptor
  variant: "block" | "line" | "inline"
  style?: CSSProperties
  onRunCommand: (request: MarkdownRunCommandRequest) => void
}

function RunCommandButton({ command, variant, style, onRunCommand }: RunCommandButtonProps) {
  function handleClick(event: MouseEvent<HTMLButtonElement>): void {
    event.preventDefault()
    event.stopPropagation()
    onRunCommand({ id: command.id, clientX: Math.round(event.clientX), clientY: Math.round(event.clientY) })
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
      <img src={runCommandIconSrc(variant)} alt="" />
    </button>
  )
}

function runCommandIconSrc(variant: "block" | "line" | "inline"): string {
  return `${markdownIconPrefix}${variant === "block" ? "runBlock.png" : "run.png"}`
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

  const codeNode = (node as HastNode | undefined)?.children?.find(child => child.tagName === "code")
  return sourcePositionFromHastNode(codeNode)
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
  const codeNode = (node as HastNode | undefined)?.children?.find(child => child.tagName === "code")
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
