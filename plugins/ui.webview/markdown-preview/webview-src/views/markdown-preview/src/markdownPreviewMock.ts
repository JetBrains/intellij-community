// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId, type WebViewCallable, type WebViewImplementable } from "@jetbrains/intellij-webview"
import { defineWebViewMock } from "@jetbrains/intellij-webview-testkit"
import type {
  MarkdownChangedBlockDescriptor,
  MarkdownCommandDescriptor,
  MarkdownNavigatePathLinkRequest,
  MarkdownResolvePathLinksRequest,
  MarkdownResolvedPathLinksResponse,
  MarkdownResolveRunCommandsRequest,
  MarkdownResolvedRunCommandsResponse,
  MarkdownRunCommandRequest,
  MarkdownSourceRange,
} from "./markdownPreviewTypes"

interface MarkdownContentChangedParams {
  markdown: string
  scrollLine: number
  settings: MarkdownPreviewSettingsParams
  contentVersion: number
  changes: MarkdownChangedBlockDescriptor[]
}

interface MarkdownPreviewSettingsParams {
  fontSize?: number | null
}

interface MarkdownScrollToLineParams {
  line: number
}

interface MarkdownSelectionChangedParams {
  selection?: MarkdownSourceRange | null
}

interface MarkdownPreviewPageApi extends WebViewImplementable {
  contentChanged(params: MarkdownContentChangedParams): void
  scrollToLine(params: MarkdownScrollToLineParams): void
  selectionChanged(params: MarkdownSelectionChangedParams): void
}

interface MarkdownPreviewHostApi extends WebViewCallable {
  pageReady(): Promise<void>
  openLink(params: MarkdownOpenLinkParams): Promise<void>
  resolveRunCommands(params: MarkdownResolveRunCommandsRequest): Promise<MarkdownResolvedRunCommandsResponse>
  runCommand(params: MarkdownRunCommandRequest): Promise<void>
  resolvePathLinks(params: MarkdownResolvePathLinksRequest): Promise<MarkdownResolvedPathLinksResponse>
  navigatePathLink(params: MarkdownNavigatePathLinkRequest): Promise<void>
}

interface MarkdownOpenLinkParams {
  href: string
}

const markdownPreviewPageApiId = apiId<MarkdownPreviewPageApi>()("markdown.preview")
const markdownPreviewHostApiId = apiId<MarkdownPreviewHostApi>()("markdown.preview")
const changes: MarkdownChangedBlockDescriptor[] = [
  { kind: "ADDED", startLine: 9, endLine: 13 },
  { kind: "MODIFIED", startLine: 20, endLine: 23 },
]
const resolvedPathLinks = new Set(["docs/guide.md:12", "src/Main.kt", "src\\WindowsPath.kt", "index.h", "index.html", "style.css", "requirements.txt", "my_django_project/", "my_django_app/"])

export default defineWebViewMock(({ host, page, theme }) => {
  const toolbar = installMockToolbar()
  let contentVersion = 1

  function updatePreview(): void {
    page.callable(markdownPreviewPageApiId).contentChanged({
      markdown: sampleMarkdown,
      scrollLine: 0,
      settings: {},
      contentVersion: contentVersion++,
      changes,
    })
    page.callable(markdownPreviewPageApiId).selectionChanged({
      selection: { startLine: 16, startColumn: 1, endLine: 16, endColumn: 17 },
    })
  }

  toolbar.toggleThemeButton.addEventListener("click", () => {
    const nextTheme = document.documentElement.dataset.theme === "dark" ? "light" : "dark"
    theme.set(nextTheme)
    updatePreview()
  })

  theme.set("light")
  host.implement(markdownPreviewHostApiId, {
    async pageReady() {
      updatePreview()
    },
    async openLink(params) {
      toolbar.log.textContent = `open ${params.href}`
    },
    async resolveRunCommands(request) {
      const commands: MarkdownCommandDescriptor[] = request.candidates
        .filter(candidate => candidate.rawCommand.trim().length > 0)
        .map(candidate => ({
          id: candidate.id,
          kind: candidate.kind,
          startLine: candidate.startLine,
          startColumn: candidate.startColumn,
          endLine: candidate.endLine,
          endColumn: candidate.endColumn,
          title: candidate.kind === "BLOCK" ? `Run ${candidate.language ?? "block"}` : `Run ${candidate.rawCommand.trim()}`,
          firstLineCommandId: candidate.firstLineCommandId,
        }))
      return { commands }
    },
    async runCommand(params) {
      toolbar.log.textContent = `run ${params.id}`
    },
    async resolvePathLinks(request) {
      return {
        resolvedIds: request.candidates
          .filter(candidate => resolvedPathLinks.has(candidate.rawPath))
          .map(candidate => candidate.id),
      }
    },
    async navigatePathLink(params) {
      toolbar.log.textContent = JSON.stringify({ kind: "navigatePathLink", ...params })
    },
  })
})

function installMockToolbar(): { toggleThemeButton: HTMLButtonElement, log: HTMLSpanElement } {
  const content = document.getElementById("content")
  if (!content) {
    throw new Error("Missing Markdown preview content element")
  }

  const toolbar = document.createElement("div")
  toolbar.className = "markdownPreviewMockToolbar"
  toolbar.innerHTML = `<strong>Markdown Preview Mock</strong>`

  const toggleThemeButton = document.createElement("button")
  toggleThemeButton.type = "button"
  toggleThemeButton.textContent = "Toggle theme"
  toolbar.append(toggleThemeButton)

  const log = document.createElement("span")
  log.id = "mock-run-log"
  toolbar.append(log)

  content.before(toolbar)
  installMockToolbarStyles()
  return { toggleThemeButton, log }
}

function installMockToolbarStyles(): void {
  const style = document.createElement("style")
  style.textContent = `
    .markdownPreviewMockToolbar {
      position: sticky;
      z-index: 1;
      top: 0;
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 36px;
      padding: 0 12px;
      border-bottom: 1px solid var(--ij-border, #d0d7de);
      background: var(--ij-bg-panel, #f6f8fa);
      color: var(--ij-text-primary, #24292f);
      font: 13px system-ui, sans-serif;
    }

    .markdownPreviewMockToolbar button {
      height: 24px;
      padding: 0 10px;
    }

    #mock-run-log {
      color: var(--ij-text-secondary, #6a737d);
    }
  `
  document.head.append(style)
}

const sampleMarkdown = `---
title: Markdown Preview Frontmatter
subtitle: WebView testkit bridge
author: JetBrains
tags: [webview, markdown]
draft: false
nested:
  hidden: true
---

# Markdown Preview Mock

This page runs the real Markdown preview entry through the WebView testkit mock bridge.

[External link](https://www.jetbrains.com), an inline command: \`echo inline\`, and a path \`docs/guide.md:12\`.

Inline math \\(a+b\\) and display math $$c=d$$.

## Images

![Zoomable preview](images/zoomable-preview.svg)

Inline image: ![Inline preview](images/inline-preview.svg) stays inline.

[![Linked preview](images/linked-preview.svg)](https://www.jetbrains.com)

## Runnable Code

\`\`\`bash
echo first line
cat src/Main.kt
cat src\\WindowsPath.kt
cat missing/Nope.kt
echo second line
\`\`\`

## Project Tree

\`\`\`text
.venv/
app/
  init.py
  main.py
  routes.py
templates/
  index.html
static/
style.css
requirements.txt
\`\`\`

## Django Tree

\`\`\`
my_django_project/
  manage.py
  ...
  my_django_app/
    migrations/
    templates/
    models.py
    urls.py
    ...
\`\`\`

## Mermaid

\`\`\`mermaid
flowchart LR
  A[src/Mermaid.kt] --> B[Preview]
\`\`\`

## Table

| Item | State |
| --- | --- |
| Icons | AllIcons |
| Theme | Toggleable |
`
