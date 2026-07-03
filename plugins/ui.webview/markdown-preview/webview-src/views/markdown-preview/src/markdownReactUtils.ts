// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { ReactNode } from "react"

export function codeToString(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map(codeToString).join("")
  }
  return ""
}

export function classNames(...names: Array<string | undefined>): string | undefined {
  const className = names.filter(Boolean).join(" ")
  return className || undefined
}
