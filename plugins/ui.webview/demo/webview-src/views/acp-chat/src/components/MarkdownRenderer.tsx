// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useId, useMemo, useState } from "react"
import ReactMarkdown, { defaultUrlTransform, type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeKatex from "rehype-katex"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import remarkGfm from "remark-gfm"
import remarkMath from "remark-math"
import { acpBridgeHost } from "../bridge/webviewApi"
import { MermaidBlock } from "./MermaidBlock"
import { codeNodeFromPreNode, hastClassNames, hastText } from "./markdownHastUtils"
import { collectPathLinkCandidates, renderPathLinks } from "./markdownPathLinks"
import { markdownSanitizeSchema } from "./markdownSanitizeSchema"

interface MarkdownRendererProps {
  text: string
  streaming?: boolean
  className?: string
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
  const rootClassName = classNames(className, "webview-selectable-text", streaming ? "acpMarkdown--streaming" : undefined)
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
      const linkedChildren = streaming ? children : renderPathLinks(children, resolvedRawPaths, "code", request => {
        void acpBridgeHost.navigatePathLink(request)
      })
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

function classNames(...names: Array<string | undefined>): string {
  return names.filter(Boolean).join(" ")
}
