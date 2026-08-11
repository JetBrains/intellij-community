// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export interface HastNode {
  tagName?: string
  value?: string
  properties?: Record<string, unknown>
  children?: HastNode[]
}

export function codeNodeFromPreNode(node: unknown): HastNode | undefined {
  return (node as HastNode | undefined)?.children?.find(child => child.tagName === "code")
}

export function hastClassNames(node: HastNode | undefined): string[] {
  const className = node?.properties?.className
  if (Array.isArray(className)) return className.filter((name): name is string => typeof name === "string")
  if (typeof className === "string") return className.split(/\s+/)
  return []
}

export function hastText(node: HastNode | undefined): string {
  if (!node) return ""
  if (typeof node.value === "string") return node.value
  return node.children?.map(hastText).join("") ?? ""
}
