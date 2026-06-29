// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useId } from "react"
import ReactMarkdown, { defaultUrlTransform, type Components, type Options } from "react-markdown"
import rehypeHighlight from "rehype-highlight"
import rehypeKatex from "rehype-katex"
import rehypeRaw from "rehype-raw"
import rehypeSanitize from "rehype-sanitize"
import remarkGfm from "remark-gfm"
import remarkMath from "remark-math"
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
      return <code className={className} {...props}>{children}</code>
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

function classNames(...names: Array<string | undefined>): string {
  return names.filter(Boolean).join(" ")
}
