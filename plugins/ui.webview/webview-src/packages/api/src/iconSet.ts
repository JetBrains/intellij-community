// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { webViewTheme } from "./theme"

export interface IconSet {
  readonly id: string
  src(resourcePath: string): string
}

export const IconSet = /* @__PURE__ */ Object.freeze({
  define(id: string): IconSet {
    validateIconSetId(id)
    return new DefinedIconSet(id)
  },
})

class DefinedIconSet implements IconSet {
  constructor(readonly id: string) {}

  src(resourcePath: string): string {
    validateIconResourcePath(resourcePath)
    return `./__ij-icons/${this.id}/${webViewTheme.current}/${encodeIconResourcePath(resourcePath)}`
  }
}

function validateIconSetId(id: string): void {
  if (!/^[A-Za-z][A-Za-z0-9._-]*$/.test(id)) {
    throw new Error(`Invalid WebView icon set id: ${id}`)
  }
}

function validateIconResourcePath(resourcePath: string): void {
  if (resourcePath.length === 0 || resourcePath.startsWith("/") || resourcePath.includes("\\")) {
    throw new Error(`Invalid WebView icon resource path: ${resourcePath}`)
  }
  if (/^[A-Za-z][A-Za-z0-9+.-]*:/.test(resourcePath)) {
    throw new Error(`Invalid WebView icon resource path: ${resourcePath}`)
  }
  const segments = resourcePath.split("/")
  if (segments.some((segment) => segment.length === 0 || segment === "." || segment === "..")) {
    throw new Error(`Invalid WebView icon resource path: ${resourcePath}`)
  }
  if (!resourcePath.endsWith(".svg") && !resourcePath.endsWith(".png")) {
    throw new Error(`Unsupported WebView icon resource extension: ${resourcePath}`)
  }
}

function encodeIconResourcePath(resourcePath: string): string {
  return resourcePath.split("/").map((segment) => encodeURIComponent(segment)).join("/")
}
