// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

const markdownResourcePrefix = "./__markdown-preview-resource/"

export function markdownResourceSrc(src: string | undefined): string | undefined {
  if (!src || !isLocalMarkdownResource(src)) return src
  return `${markdownResourcePrefix}${base64UrlEncode(src)}`
}

function isLocalMarkdownResource(src: string): boolean {
  const trimmed = src.trim()
  if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("//")) return false
  const scheme = trimmed.match(/^([A-Za-z][A-Za-z\d+.-]*):/)?.[1]?.toLowerCase()
  return scheme === undefined || scheme === "file"
}

function base64UrlEncode(value: string): string {
  const bytes = new TextEncoder().encode(value)
  let binary = ""
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "")
}
