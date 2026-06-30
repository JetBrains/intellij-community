// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { cloneElement, Fragment, isValidElement, useEffect, useId, useMemo, useState, type ReactElement, type ReactNode } from "react"
import ReactMarkdown, { defaultUrlTransform, type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeKatex from "rehype-katex"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import remarkGfm from "remark-gfm"
import remarkMath from "remark-math"
import { acpBridgeHost } from "../bridge/webviewApi"
import { MermaidBlock } from "./MermaidBlock"
import { markdownSanitizeSchema } from "./markdownSanitizeSchema"

interface MarkdownRendererProps {
  text: string
  streaming?: boolean
  className?: string
}

interface HastNode {
  tagName?: string
  value?: string
  properties?: Record<string, unknown>
  children?: HastNode[]
}

interface PathLinkCandidate {
  id: string
  rawPath: string
}

interface PathToken {
  rawPath: string
  start: number
  end: number
}

const remarkPlugins: Options["remarkPlugins"] = [remarkGfm, remarkMath]
const rehypePlugins: Options["rehypePlugins"] = [
  rehypeRaw,
  [rehypeSanitize, markdownSanitizeSchema],
  [rehypeKatex, { strict: "warn", throwOnError: false }],
  [rehypeHighlight, { detect: true, plainText: ["mermaid", "text", "txt"] }],
]

export function MarkdownRenderer({ text, streaming = false, className = "acpMarkdown" }: MarkdownRendererProps) {
  const idPrefix = `acp-md-${useId().replace(/[^a-zA-Z0-9_-]/g, "")}-`
  const rootClassName = classNames(className, streaming ? "acpMarkdown--streaming" : undefined)
  const pathLinkCandidates = useMemo(() => streaming ? [] : collectPathLinkCandidates(text), [streaming, text])
  const [resolvedRawPaths, setResolvedRawPaths] = useState<ReadonlySet<string>>(() => new Set())

  useEffect(() => {
    if (pathLinkCandidates.length === 0) {
      setResolvedRawPaths(new Set())
      return
    }

    let cancelled = false
    setResolvedRawPaths(new Set())
    void acpBridgeHost.resolvePathLinks({ candidates: pathLinkCandidates }).then(result => {
      if (cancelled) return
      const resolvedIds = new Set(result.resolvedIds)
      setResolvedRawPaths(new Set(pathLinkCandidates.filter(candidate => resolvedIds.has(candidate.id)).map(candidate => candidate.rawPath)))
    }).catch(() => {
      if (!cancelled) setResolvedRawPaths(new Set())
    })
    return () => {
      cancelled = true
    }
  }, [pathLinkCandidates])

  const components: Components = {
    a({ href, children, ...props }) {
      return <a {...props} href={href} target="_blank" rel="noreferrer">{children}</a>
    },
    pre({ node, className, children, ...props }) {
      const codeNode = codeNodeFromPreNode(node)
      const code = hastText(codeNode).replace(/\n$/, "")
      if (!streaming && hastClassNames(codeNode).includes("language-mermaid")) {
        return <MermaidBlock chart={code} />
      }
      return <pre className={className} {...props}>{children}</pre>
    },
    code({ className, children, ...props }) {
      const linkedChildren = streaming ? children : renderPathLinks(children, resolvedRawPaths, "code")
      return <code className={className} {...props}>{linkedChildren}</code>
    },
  }

  return (
    <div className={rootClassName}>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        remarkRehypeOptions={{ clobberPrefix: idPrefix }}
        components={components}
        urlTransform={defaultUrlTransform}
      >
        {text}
      </ReactMarkdown>
    </div>
  )
}

function codeNodeFromPreNode(node: unknown): HastNode | undefined {
  return (node as HastNode | undefined)?.children?.find(child => child.tagName === "code")
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

function collectPathLinkCandidates(markdown: string): PathLinkCandidate[] {
  const codeSegments = markdownCodeSegments(markdown)
  const candidates: PathLinkCandidate[] = []
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

function renderPathLinks(node: ReactNode, resolvedRawPaths: ReadonlySet<string>, keyPrefix: string): ReactNode {
  if (typeof node === "string") {
    return renderTextPathLinks(node, resolvedRawPaths, keyPrefix)
  }
  if (Array.isArray(node)) {
    return node.map((child, index) => <Fragment key={`${keyPrefix}-${index}`}>{renderPathLinks(child, resolvedRawPaths, `${keyPrefix}-${index}`)}</Fragment>)
  }
  if (isValidElement(node)) {
    const element = node as ReactElement<{ children?: ReactNode }>
    if (element.props.children == null) return element
    return cloneElement(element, undefined, renderPathLinks(element.props.children, resolvedRawPaths, keyPrefix))
  }
  return node
}

function renderTextPathLinks(text: string, resolvedRawPaths: ReadonlySet<string>, keyPrefix: string): ReactNode {
  const tokens = pathTokens(text).filter(token => resolvedRawPaths.has(token.rawPath))
  if (tokens.length === 0) return text

  const parts: ReactNode[] = []
  let offset = 0
  for (const [index, token] of tokens.entries()) {
    if (token.start < offset) continue
    if (offset < token.start) {
      parts.push(text.slice(offset, token.start))
    }
    const label = text.slice(token.start, token.end)
    parts.push(
      <button
        key={`${keyPrefix}-${token.start}-${index}`}
        type="button"
        className="acpMarkdownPathLink"
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          void acpBridgeHost.navigatePathLink({ rawPath: token.rawPath, clientX: event.clientX, clientY: event.clientY })
        }}
      >
        {label}
      </button>,
    )
    offset = token.end
  }
  if (offset < text.length) {
    parts.push(text.slice(offset))
  }
  return parts
}

function pathTokens(text: string): PathToken[] {
  const tokens: PathToken[] = []
  for (const match of text.matchAll(PATH_CANDIDATE_PATTERN)) {
    const matchText = match[0]
    const rawPath = trimPathCandidate(matchText)
    if (!rawPath || !isPathLike(rawPath)) continue

    const leadingTrim = matchText.indexOf(rawPath)
    const start = (match.index ?? 0) + leadingTrim
    tokens.push({ rawPath, start, end: start + rawPath.length })
  }
  return tokens
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

function classNames(...names: Array<string | undefined>): string {
  return names.filter(Boolean).join(" ")
}

const FENCED_CODE_BLOCK_PATTERN = /(^|\n)(`{3,}|~{3,})([^\n]*)\n([\s\S]*?)\n\2(?=\n|$)/g
const INLINE_CODE_PATTERN = /`([^`\n]+)`/g
const FILE_EXTENSION_PATTERN = /\.[A-Za-z0-9]+(?:#L\d+|:\d+(?::\d+)?)?$/
const PATH_CANDIDATE_PATTERN = /(?:(?:(?:~|\.{1,2})[\\/]|[\\/]|[A-Za-z]:[\\/]|[A-Za-z0-9_.-]+[\\/])[^\s`<>"']+|[A-Za-z0-9_.-]+\.(?:bazel|bzl|c|cmd|cpp|cs|css|go|gradle|h|hpp|html|iml|java|js|jsx|json|kt|kts|md|mjs|properties|py|rs|scss|sh|ts|tsx|txt|xml|yaml|yml))(?:#L\d+|:\d+(?::\d+)?)?/gi
const PATH_TRIM_START = new Set(["(", "[", "{", "<"])
const PATH_TRIM_END = new Set([")", "]", "}", ">", ".", ",", ";"])
const URL_SCHEME_PATTERN = /^[a-z][a-z0-9+.-]*:\/\//i
