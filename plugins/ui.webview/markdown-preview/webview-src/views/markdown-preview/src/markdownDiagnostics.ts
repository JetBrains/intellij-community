// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function markdownDiagnosticDetails(markdown: string, contentVersion: number, extra = ""): string {
  const details = `contentVersion=${contentVersion}, markdownChars=${markdown.length}, markdownLines=${markdownLineCount(markdown)}`
  return extra.length === 0 ? details : `${details}, ${extra}`
}

function markdownLineCount(markdown: string): number {
  if (markdown.length === 0) return 0
  let lines = 1
  for (let index = 0; index < markdown.length; index++) {
    if (markdown.charCodeAt(index) === 10) {
      lines++
    }
  }
  return lines
}
