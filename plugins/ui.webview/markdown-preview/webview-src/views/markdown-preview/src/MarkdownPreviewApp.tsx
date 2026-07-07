// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { lazy, Suspense, useEffect, useMemo, useState, type MouseEvent } from "react"
import ReactMarkdown, { type Components, type Options } from "react-markdown"
import { getPerfLogger } from "@jetbrains/intellij-webview"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import rehypeSlug from "rehype-slug"
import remarkFrontmatter from "remark-frontmatter"
import remarkGfm from "remark-gfm"
import { FloatingMarkdownControls } from "./FloatingMarkdownControls"
import { markdownDiagnosticDetails } from "./markdownDiagnostics"
import { codeNodeFromPreNode, type HastNode } from "./markdownHastUtils"
import { collectPathLinkCandidates, renderPathLinks } from "./markdownPathLinks"
import type {
  MarkdownChangedBlockDescriptor,
  MarkdownCommandCandidate,
  MarkdownCommandDescriptor,
  MarkdownPreviewSettings,
  MarkdownNavigatePathLinkRequest,
  MarkdownResolvePathLinksRequest,
  MarkdownResolvedPathLinksResponse,
  MarkdownResolveRunCommandsRequest,
  MarkdownResolvedRunCommandsResponse,
  MarkdownRunCommandRequest,
  MarkdownSourceRange,
} from "./markdownPreviewTypes"
import { codeToString } from "./markdownReactUtils"
import { markdownResourceSrc } from "./markdownResources"
import { frontmatterBlockFromPreNode, frontmatterLanguageFromPreNode, remarkFrontmatterBlocks, remarkSourcePositionAttributes } from "./markdownRemarkPlugins"
import {
  CodeFenceRunGutter,
  RunCommandButton,
  codeFenceCommandCandidates,
  createCommandLookup,
  findBlockCommand,
  findInlineCommand,
  findLineCommands,
  hasLanguageClass,
  inlineCommandCandidate,
  isMermaidCodeNode,
  uniqueCommandCandidates,
} from "./markdownRunCommands"
import { markdownSanitizeSchema } from "./markdownSanitizeSchema"
import {
  cancelScheduledMarkdownPreviewScroll,
  clearSourceDecorations,
  decorateSourceBlocks,
  positionKey,
  scrollMarkdownPreviewToLine,
  sourcePositionFromHastNode,
  sourcePositionFromPreNode,
} from "./markdownSourcePositions"

export { scrollMarkdownPreviewToLine } from "./markdownSourcePositions"

interface MarkdownPreviewAppProps {
  markdown: string
  scrollLine: number
  contentVersion: number
  changes: MarkdownChangedBlockDescriptor[]
  selection: MarkdownSourceRange | undefined
  settings: MarkdownPreviewSettings
  theme: "light" | "dark"
  onOpenLink: (href: string) => void
  onResolveRunCommands: (request: MarkdownResolveRunCommandsRequest) => Promise<MarkdownResolvedRunCommandsResponse>
  onRunCommand: (request: MarkdownRunCommandRequest) => void
  onResolvePathLinks: (request: MarkdownResolvePathLinksRequest) => Promise<MarkdownResolvedPathLinksResponse>
  onNavigatePathLink: (request: MarkdownNavigatePathLinkRequest) => void
  onSetFontSize: (fontSize: number) => void
}

const emptyPathSet = new Set<string>()
const markdownLogger = getPerfLogger("markdown")
const LazyMarkdownImageBlock = lazy(() => import("./MarkdownImageBlock").then(module => ({ default: module.MarkdownImageBlock })))
const LazyMermaidBlock = lazy(() => import("./MermaidBlock").then(module => ({ default: module.MermaidBlock })))
type RehypePlugin = NonNullable<Options["rehypePlugins"]>[number]
type IdleCallbackHandle = number

type WindowWithIdleCallback = Window & {
  requestIdleCallback: ((callback: () => void, options?: { timeout?: number }) => IdleCallbackHandle) | undefined
  cancelIdleCallback: ((handle: IdleCallbackHandle) => void) | undefined
}

const remarkPlugins: Options["remarkPlugins"] = [
  remarkGfm,
  [remarkFrontmatter, ["yaml", "toml"]],
  remarkFrontmatterBlocks,
  remarkSourcePositionAttributes,
]
const baseRehypePlugins: NonNullable<Options["rehypePlugins"]> = [
  rehypeRaw,
  rehypeSlug,
  [rehypeSanitize, markdownSanitizeSchema],
]

export function MarkdownPreviewApp({
  markdown,
  scrollLine,
  contentVersion,
  changes,
  selection,
  settings,
  theme,
  onOpenLink,
  onResolveRunCommands,
  onRunCommand,
  onResolvePathLinks,
  onNavigatePathLink,
  onSetFontSize,
}: MarkdownPreviewAppProps) {
  const commandCandidates: MarkdownCommandCandidate[] = []
  const pathLinkCandidates = useMemo(() => {
    const startedAtMs = performance.now()
    const candidates = collectPathLinkCandidates(markdown)
    markdownLogger.perfSince("pathLinks.collect", startedAtMs, markdownDiagnosticDetails(markdown, contentVersion, `candidates=${candidates.length}`))
    return candidates
  }, [contentVersion, markdown])
  const [resolvedCommands, setResolvedCommands] = useState<{ contentVersion: number, commands: MarkdownCommandDescriptor[] }>({
    contentVersion: -1,
    commands: [],
  })
  const [resolvedPathLinks, setResolvedPathLinks] = useState<{ contentVersion: number, rawPaths: ReadonlySet<string> }>({
    contentVersion: -1,
    rawPaths: emptyPathSet,
  })
  const commandsReady = resolvedCommands.contentVersion === contentVersion
  const pathLinksReady = resolvedPathLinks.contentVersion === contentVersion
  const commands = commandsReady ? resolvedCommands.commands : []
  const resolvedRawPaths = pathLinksReady ? resolvedPathLinks.rawPaths : emptyPathSet
  const commandLookup = createCommandLookup(commands)
  const [rehypeHighlightState, setRehypeHighlightState] = useState<{ contentVersion: number, plugin: RehypePlugin } | undefined>()
  const rehypeHighlightPlugin = rehypeHighlightState?.contentVersion === contentVersion ? rehypeHighlightState.plugin : undefined
  const rehypePlugins = useMemo<Options["rehypePlugins"]>(() => {
    if (!rehypeHighlightPlugin) return baseRehypePlugins
    return [
      ...baseRehypePlugins,
      [rehypeHighlightPlugin, { detect: true, plainText: ["mermaid", "text", "txt"] }],
    ] as Options["rehypePlugins"]
  }, [rehypeHighlightPlugin])
  const components: Components = {
    a({ href, children, ...props }) {
      function handleClick(event: MouseEvent<HTMLAnchorElement>): void {
        if (!href) return
        event.preventDefault()
        const localFragment = localAnchorFragment(href)
        if (localFragment !== undefined) {
          navigateToLocalAnchor(localFragment)
          return
        }
        onOpenLink(href)
      }

      return <a {...props} href={href} onClick={handleClick}>{children}</a>
    },
    img({ src, alt, ...props }) {
      return <img {...props} src={markdownResourceSrc(src)} alt={alt} />
    },
    p({ node, className, children, ...props }) {
      const image = standaloneImageFromParagraphNode(node)
      if (image) {
        const imageSrc = markdownResourceSrc(image.src) ?? image.src
        return (
          <Suspense fallback={<p {...props} className={className}><img src={imageSrc} alt={image.alt ?? ""} title={image.title} /></p>}>
            <LazyMarkdownImageBlock {...props} className={className} src={imageSrc} alt={image.alt} title={image.title} />
          </Suspense>
        )
      }
      return <p {...props} className={className}>{children}</p>
    },
    pre({ node, className, children, ...props }) {
      const frontmatterLanguage = frontmatterLanguageFromPreNode(node)
      if (frontmatterLanguage) {
        const frontmatterBlock = frontmatterBlockFromPreNode(node)
        if (!frontmatterBlock) return null

        const sourcePosition = sourcePositionFromPreNode(node)
        return (
          <section className="frontmatterBlock" data-sourcepos={sourcePosition ? positionKey(sourcePosition) : undefined}>
            <details className="frontmatterMetadata">
              <summary>Frontmatter metadata</summary>
              <dl>
                {frontmatterBlock.metadata.map((entry, index) => (
                  <div className="frontmatterMetadataEntry" key={`${entry.key}-${index}`}>
                    <dt>{entry.key}</dt>
                    <dd>{entry.value}</dd>
                  </div>
                ))}
              </dl>
            </details>
          </section>
        )
      }
      const sourcePosition = sourcePositionFromPreNode(node)
      const codeNode = codeNodeFromPreNode(node)
      const isMermaidFence = codeNode ? isMermaidCodeNode(codeNode) : false
      const codeFenceCandidates = sourcePosition && codeNode && !isMermaidFence ? codeFenceCommandCandidates(sourcePosition, codeNode) : []
      commandCandidates.push(...codeFenceCandidates)
      if (isMermaidFence) {
        return <>{children}</>
      }
      const blockCommand = sourcePosition ? findBlockCommand(commandLookup, sourcePosition) : undefined
      const lineCommands = sourcePosition ? findLineCommands(commandLookup, sourcePosition, blockCommand?.firstLineCommandId) : []
      const hasCommandGutter = codeFenceCandidates.length > 0 || blockCommand || lineCommands.length > 0
      if (!hasCommandGutter) {
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
        return (
          <Suspense fallback={<div className="mermaidBlock isRendering">Rendering diagram...</div>}>
            <LazyMermaidBlock chart={code} theme={theme} />
          </Suspense>
        )
      }
      const sourcePosition = sourcePositionFromHastNode(node)
      if (sourcePosition && !hasLanguageClass(className)) {
        commandCandidates.push(inlineCommandCandidate(sourcePosition, code))
      }
      const inlineCommand = sourcePosition ? findInlineCommand(commandLookup, sourcePosition) : undefined
      const linkedChildren = renderPathLinks(children, resolvedRawPaths, "code", contentVersion, onNavigatePathLink)
      if (inlineCommand) {
        return (
          <code className={className} {...props}>
            <RunCommandButton contentVersion={contentVersion} command={inlineCommand} variant="inline" onRunCommand={onRunCommand} />
            {linkedChildren}
          </code>
        )
      }
      return <code className={className} {...props}>{linkedChildren}</code>
    },
  }

  useEffect(() => {
    if (pathLinkCandidates.length === 0) {
      setResolvedPathLinks({ contentVersion, rawPaths: emptyPathSet })
      return
    }

    let cancelled = false
    setResolvedPathLinks({ contentVersion: -1, rawPaths: emptyPathSet })
    const cancelIdle = executeWhenIdle(() => {
      const startedAtMs = performance.now()
      void onResolvePathLinks({ contentVersion, candidates: pathLinkCandidates }).then(response => {
        if (cancelled) return
        const resolvedIds = new Set(response.resolvedIds)
        markdownLogger.perfSince(
          "pathLinks.resolve",
          startedAtMs,
          markdownDiagnosticDetails(markdown, contentVersion, `candidates=${pathLinkCandidates.length}, resolved=${resolvedIds.size}`),
        )
        setResolvedPathLinks({
          contentVersion,
          rawPaths: new Set(pathLinkCandidates.filter(candidate => resolvedIds.has(candidate.id)).map(candidate => candidate.rawPath)),
        })
      }).catch(() => {
        if (!cancelled) setResolvedPathLinks({ contentVersion, rawPaths: emptyPathSet })
      })
    })
    return () => {
      cancelled = true
      cancelIdle()
    }
  }, [contentVersion, onResolvePathLinks, pathLinkCandidates])

  useEffect(() => {
    let cancelled = false
    setResolvedCommands({ contentVersion: -1, commands: [] })
    const candidates = uniqueCommandCandidates(commandCandidates)
    if (candidates.length === 0) {
      setResolvedCommands({ contentVersion, commands: [] })
      return
    }

    const cancelIdle = executeWhenIdle(() => {
      const startedAtMs = performance.now()
      void onResolveRunCommands({ contentVersion, candidates }).then(response => {
        if (cancelled) return
        setResolvedCommands({ contentVersion, commands: response.commands })
        markdownLogger.perfSince(
          "runCommands.resolve",
          startedAtMs,
          markdownDiagnosticDetails(markdown, contentVersion, `candidates=${candidates.length}, resolved=${response.commands.length}`),
        )
      })
    })
    return () => {
      cancelled = true
      cancelIdle()
    }
  }, [contentVersion, onResolveRunCommands])

  useEffect(() => {
    if (!markdownMayNeedSyntaxHighlighting(markdown) || rehypeHighlightState?.contentVersion === contentVersion) return

    let cancelled = false
    const cancelIdle = executeWhenIdle(() => {
      void import("rehype-highlight").then(module => {
        if (!cancelled) {
          setRehypeHighlightState({ contentVersion, plugin: module.default as RehypePlugin })
        }
      })
    })
    return () => {
      cancelled = true
      cancelIdle()
    }
  }, [contentVersion, markdown, rehypeHighlightState])

  useEffect(() => {
    if (!commandsReady || !pathLinksReady || !markdownMayContainLatex(markdown)) return

    let cancelled = false
    const cancelIdle = executeWhenIdle(() => {
      const startedAtMs = performance.now()
      void import("./markdownLatex").then(({ renderMarkdownLatex }) => {
        if (cancelled) return
        renderMarkdownLatex()
        markdownLogger.perfSince("latex.render", startedAtMs, markdownDiagnosticDetails(markdown, contentVersion))
      })
    })
    return () => {
      cancelled = true
      cancelIdle()
    }
  }, [commandsReady, contentVersion, markdown, pathLinksReady, theme])

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
      <div key={contentVersion} className="markdownPreviewContent webview-selectable-text">
        <ReactMarkdown
          remarkPlugins={remarkPlugins}
          rehypePlugins={rehypePlugins}
          components={components}
          urlTransform={url => url}
        >
          {markdown}
        </ReactMarkdown>
      </div>
      <FloatingMarkdownControls markdown={markdown} settings={settings} onSetFontSize={onSetFontSize} />
    </>
  )
}

interface MarkdownImageDescriptor {
  src: string
  alt?: string
  title?: string
}

function standaloneImageFromParagraphNode(node: unknown): MarkdownImageDescriptor | undefined {
  const children = (node as HastNode | undefined)?.children ?? []
  const contentChildren = children.filter(child => !isWhitespaceTextNode(child))
  if (contentChildren.length !== 1) return undefined

  const imageNode = contentChildren[0]
  if (imageNode.tagName !== "img") return undefined

  const src = stringProperty(imageNode.properties?.src)
  if (!src) return undefined

  return {
    src,
    alt: stringProperty(imageNode.properties?.alt),
    title: stringProperty(imageNode.properties?.title),
  }
}

function localAnchorFragment(href: string): string | undefined {
  if (href.startsWith("#")) return href.slice(1)

  try {
    const url = new URL(href, window.location.href)
    if (url.origin === window.location.origin && url.pathname === window.location.pathname && url.search === window.location.search && url.hash.startsWith("#")) {
      return url.hash.slice(1)
    }
  }
  catch {
  }
  return undefined
}

function navigateToLocalAnchor(fragment: string): void {
  if (fragment.length === 0) {
    updateLocationHash("#")
    window.scrollTo({ top: 0, left: 0 })
    return
  }

  const target = findLocalAnchorTarget(decodeFragment(fragment))
  updateLocationHash(`#${fragment}`)
  target?.scrollIntoView({ block: "start" })
}

function findLocalAnchorTarget(id: string): HTMLElement | null {
  const normalizedId = id.replace(/^-+/, "")
  return document.getElementById(id)
    ?? document.getElementById(normalizedId)
    ?? document.getElementById(`user-content-${id}`)
    ?? document.getElementById(`user-content-${normalizedId}`)
}

function decodeFragment(fragment: string): string {
  try {
    return decodeURIComponent(fragment)
  }
  catch {
    return fragment
  }
}

function updateLocationHash(href: string): void {
  if (!window.history) return
  try {
    window.history.pushState(null, "", href)
  }
  catch {
  }
}

function isWhitespaceTextNode(node: HastNode): boolean {
  return node.tagName === undefined && (node.value ?? "").trim().length === 0
}

function stringProperty(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined
}

function markdownMayNeedSyntaxHighlighting(markdown: string): boolean {
  return /(^|\n)(```|~~~| {4}|\t|<pre\b|<code\b)/.test(markdown)
}

function markdownMayContainLatex(markdown: string): boolean {
  return /\\\([\s\S]*?\\\)|\\\[[\s\S]*?\\\]|\\begin\{[A-Za-z*]+}/.test(markdown) || markdownMayContainDollarLatex(markdown)
}

function markdownMayContainDollarLatex(markdown: string): boolean {
  for (let index = 0; index < markdown.length; index++) {
    if (markdown[index] !== "$" || isEscaped(markdown, index)) continue

    const displayMath = markdown[index + 1] === "$"
    const delimiter = displayMath ? "$$" : "$"
    const contentStart = index + delimiter.length
    if (!displayMath && (contentStart >= markdown.length || /\s|\d/.test(markdown[contentStart]))) {
      index = contentStart - 1
      continue
    }

    const contentEnd = unescapedIndexOf(markdown, delimiter, contentStart)
    if (contentEnd < 0) {
      index = contentStart - 1
      continue
    }
    if (!displayMath && (contentEnd === contentStart || /\s/.test(markdown[contentEnd - 1]))) {
      index = contentEnd + delimiter.length - 1
      continue
    }
    return true
  }
  return false
}

function unescapedIndexOf(text: string, search: string, startIndex: number): number {
  let index = text.indexOf(search, startIndex)
  while (index >= 0 && isEscaped(text, index)) {
    index = text.indexOf(search, index + search.length)
  }
  return index
}

function isEscaped(text: string, index: number): boolean {
  let backslashCount = 0
  for (let offset = index - 1; offset >= 0 && text[offset] === "\\"; offset--) {
    backslashCount++
  }
  return backslashCount % 2 === 1
}

function executeWhenIdle(callback: () => void): () => void {
  let cancelled = false
  let animationFrameHandle: number | undefined
  let idleCallbackHandle: IdleCallbackHandle | undefined
  let timeoutHandle: number | undefined

  function run(): void {
    if (!cancelled) callback()
  }

  animationFrameHandle = requestAnimationFrame(() => {
    animationFrameHandle = undefined
    if (cancelled) return

    const idleWindow = window as WindowWithIdleCallback
    if (idleWindow.requestIdleCallback) {
      idleCallbackHandle = idleWindow.requestIdleCallback(() => {
        idleCallbackHandle = undefined
        run()
      }, { timeout: 1000 })
    }
    else {
      timeoutHandle = window.setTimeout(() => {
        timeoutHandle = undefined
        run()
      }, 0)
    }
  })

  return () => {
    cancelled = true
    if (animationFrameHandle !== undefined) cancelAnimationFrame(animationFrameHandle)
    const idleWindow = window as WindowWithIdleCallback
    if (idleCallbackHandle !== undefined) idleWindow.cancelIdleCallback?.(idleCallbackHandle)
    if (timeoutHandle !== undefined) clearTimeout(timeoutHandle)
  }
}
