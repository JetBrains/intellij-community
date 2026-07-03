// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { codeNodeFromPreNode } from "./markdownHastUtils"

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

export function frontmatterTitle(language: string): string {
  return language === "toml" ? "Front matter (TOML)" : "Front matter (YAML)"
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
