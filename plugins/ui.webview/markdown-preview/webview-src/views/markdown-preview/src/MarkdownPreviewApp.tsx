// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useMemo, useState, type MouseEvent } from "react"
import ReactMarkdown, { type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import rehypeSlug from "rehype-slug"
import remarkFrontmatter from "remark-frontmatter"
import remarkGfm from "remark-gfm"
import { FloatingMarkdownControls } from "./FloatingMarkdownControls"
import { MarkdownImageBlock } from "./MarkdownImageBlock"
import { MermaidBlock } from "./MermaidBlock"
import { codeNodeFromPreNode, type HastNode } from "./markdownHastUtils"
import { renderMarkdownLatex } from "./markdownLatex"
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
  const pathLinkCandidates = useMemo(() => collectPathLinkCandidates(markdown), [markdown])
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
    p({ node, className, children, ...props }) {
      const image = standaloneImageFromParagraphNode(node)
      if (image) {
        return <MarkdownImageBlock {...props} className={className} src={markdownResourceSrc(image.src) ?? image.src} alt={image.alt} title={image.title} />
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
      if (sourcePosition && codeNode && !isMermaidFence) {
        commandCandidates.push(...codeFenceCommandCandidates(sourcePosition, codeNode))
      }
      if (isMermaidFence) {
        return <>{children}</>
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
    void onResolvePathLinks({ contentVersion, candidates: pathLinkCandidates }).then(response => {
      if (cancelled) return
      const resolvedIds = new Set(response.resolvedIds)
      setResolvedPathLinks({
        contentVersion,
        rawPaths: new Set(pathLinkCandidates.filter(candidate => resolvedIds.has(candidate.id)).map(candidate => candidate.rawPath)),
      })
    }).catch(() => {
      if (!cancelled) setResolvedPathLinks({ contentVersion, rawPaths: emptyPathSet })
    })
    return () => {
      cancelled = true
    }
  }, [contentVersion, onResolvePathLinks, pathLinkCandidates])

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
    if (commandsReady && pathLinksReady) {
      renderMarkdownLatex()
    }
  }, [commandsReady, markdown, pathLinksReady, theme])

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

function isWhitespaceTextNode(node: HastNode): boolean {
  return node.tagName === undefined && (node.value ?? "").trim().length === 0
}

function stringProperty(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined
}
