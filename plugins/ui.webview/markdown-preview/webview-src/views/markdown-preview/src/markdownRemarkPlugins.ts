// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { codeNodeFromPreNode, hastText } from "./markdownHastUtils"

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

export interface FrontmatterBlock {
  metadata: FrontmatterMetadataEntry[]
}

export interface FrontmatterMetadataEntry {
  key: string
  value: string
}

export function remarkFrontmatterBlocks() {
  return (tree: unknown) => transformFrontmatterNodes(tree as MarkdownNode)
}

export function remarkSourcePositionAttributes() {
  return (tree: unknown) => addSourcePositionAttributes(tree as MarkdownNode)
}

export function frontmatterLanguageFromPreNode(node: unknown): string | undefined {
  const codeNode = codeNodeFromPreNode(node)
  const language = codeNode?.properties?.dataFrontmatter
  return typeof language === "string" ? language : undefined
}

export function frontmatterBlockFromPreNode(node: unknown): FrontmatterBlock | undefined {
  const language = frontmatterLanguageFromPreNode(node)
  if (!language) return undefined

  const entries = parseFrontmatterEntries(hastText(codeNodeFromPreNode(node)), language)
  if (entries.length === 0) return undefined

  return {
    metadata: entries,
  }
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

function parseFrontmatterEntries(source: string, language: string): FrontmatterMetadataEntry[] {
  return source.split(/\r?\n/)
    .map(line => parseFrontmatterEntry(line, language))
    .filter((entry): entry is FrontmatterMetadataEntry => entry !== undefined)
}

function parseFrontmatterEntry(line: string, language: string): FrontmatterMetadataEntry | undefined {
  if (line.startsWith(" ") || line.startsWith("\t")) return undefined

  const trimmedLine = line.trim()
  if (!trimmedLine || trimmedLine.startsWith("#")) return undefined

  const match = language === "toml"
    ? trimmedLine.match(/^([A-Za-z0-9_.-]+)\s*=\s*(.+)$/)
    : trimmedLine.match(/^([A-Za-z0-9_.-]+):\s*(.*)$/)
  if (!match) return undefined

  const key = match[1]
  const value = formatFrontmatterValue(match[2])
  if (!value) return undefined

  return { key, value }
}

function formatFrontmatterValue(value: string): string | undefined {
  const trimmedValue = value.trim()
  if (!trimmedValue || trimmedValue === "|" || trimmedValue === ">" || trimmedValue.startsWith("{")) return undefined
  if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) {
    return formatFrontmatterArray(trimmedValue.substring(1, trimmedValue.length - 1))
  }
  if (trimmedValue.startsWith("[")) return undefined

  return unquoteFrontmatterValue(trimmedValue)
}

function formatFrontmatterArray(value: string): string | undefined {
  const items: string[] = []
  for (const item of splitFrontmatterArrayItems(value)) {
    const trimmedItem = item.trim()
    if (trimmedItem.startsWith("[") || trimmedItem.startsWith("{")) return undefined

    const formattedItem = unquoteFrontmatterValue(trimmedItem)
    if (!formattedItem) return undefined

    items.push(formattedItem)
  }
  return items.length > 0 ? items.join(", ") : undefined
}

function splitFrontmatterArrayItems(value: string): string[] {
  const items: string[] = []
  let start = 0
  let quote: string | undefined
  let escaped = false

  for (let index = 0; index < value.length; index++) {
    const character = value[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (character === "\\" && quote === "\"") {
      escaped = true
      continue
    }
    if (quote) {
      if (character === quote) quote = undefined
      continue
    }
    if (character === "\"" || character === "'") {
      quote = character
      continue
    }
    if (character === ",") {
      items.push(value.substring(start, index))
      start = index + 1
    }
  }

  items.push(value.substring(start))
  return items
}

function unquoteFrontmatterValue(value: string): string | undefined {
  const quote = value[0]
  const unquoted = (quote === "\"" || quote === "'") && value.endsWith(quote)
    ? value.substring(1, value.length - 1)
    : value
  const trimmedValue = unquoted.trim()
  return trimmedValue.length > 0 ? trimmedValue : undefined
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
