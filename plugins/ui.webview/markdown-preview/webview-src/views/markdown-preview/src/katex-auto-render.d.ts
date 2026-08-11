// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

declare module "katex/contrib/auto-render" {
  import type { KatexOptions } from "katex"

  interface AutoRenderDelimiter {
    left: string
    right: string
    display: boolean
  }

  interface AutoRenderOptions extends KatexOptions {
    delimiters?: AutoRenderDelimiter[]
    ignoredTags?: string[]
    ignoredClasses?: string[]
    errorCallback?: (message: string, error: unknown) => void
  }

  export default function renderMathInElement(element: HTMLElement, options?: AutoRenderOptions): void
}
