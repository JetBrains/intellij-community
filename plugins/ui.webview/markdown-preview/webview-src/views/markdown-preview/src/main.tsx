// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { createRoot } from "react-dom/client"
import { apiId, webView, webViewTheme, type WebViewCallable, type WebViewImplementable } from "@jetbrains/intellij-webview"
import {
  MarkdownPreviewApp,
  scrollMarkdownPreviewToLine,
  type MarkdownChangedBlockDescriptor,
  type MarkdownCommandDescriptor,
  type MarkdownRunCommandRequest,
  type MarkdownSourceRange,
} from "./MarkdownPreviewApp"

interface MarkdownContentChangedParams {
  markdown: string
  scrollLine: number
  settings: MarkdownPreviewSettingsParams
  commands: MarkdownCommandDescriptor[]
  changes: MarkdownChangedBlockDescriptor[]
}

interface MarkdownPreviewSettingsParams {
  fontSize: number
}

interface MarkdownScrollToLineParams {
  line: number
}

interface MarkdownPreviewPageApi extends WebViewImplementable {
  contentChanged(params: MarkdownContentChangedParams): void
  scrollToLine(params: MarkdownScrollToLineParams): void
  selectionChanged(params: MarkdownSelectionChangedParams): void
}

interface MarkdownSelectionChangedParams {
  selection?: MarkdownSourceRange | null
}

interface MarkdownPreviewHostApi extends WebViewCallable {
  pageReady(): Promise<void>
  openLink(params: MarkdownOpenLinkParams): Promise<void>
  runCommand(params: MarkdownRunCommandRequest): Promise<void>
}

interface MarkdownOpenLinkParams {
  href: string
}

const markdownPreviewPageApiId = apiId<MarkdownPreviewPageApi>()("markdown.preview")
const markdownPreviewHostApi = webView.callable(apiId<MarkdownPreviewHostApi>()("markdown.preview"))
const contentElement = requiredElement<HTMLElement>("content")
const root = createRoot(contentElement)
let markdown = ""
let scrollLine = 0
let commands: MarkdownCommandDescriptor[] = []
let changes: MarkdownChangedBlockDescriptor[] = []
let selection: MarkdownSourceRange | undefined
let theme = webViewTheme.current

webView.implement(markdownPreviewPageApiId, {
  contentChanged(params) {
    markdown = params.markdown
    scrollLine = params.scrollLine
    applyPreviewSettings(params.settings)
    commands = params.commands
    changes = params.changes ?? []
    renderPreview()
  },
  scrollToLine(params) {
    scrollLine = params.line
    scrollMarkdownPreviewToLine(scrollLine)
  },
  selectionChanged(params) {
    selection = params.selection ?? undefined
    renderPreview()
  },
})

applyTheme(theme)
renderPreview()
webViewTheme.onChanged(nextTheme => {
  theme = nextTheme
  applyTheme(nextTheme)
})
void markdownPreviewHostApi.pageReady()

function renderPreview(): void {
  root.render(
    <MarkdownPreviewApp
      markdown={markdown}
      scrollLine={scrollLine}
      commands={commands}
      changes={changes}
      selection={selection}
      theme={theme}
      onOpenLink={openMarkdownLink}
      onRunCommand={runMarkdownCommand}
    />
  )
}

function openMarkdownLink(href: string): void {
  void markdownPreviewHostApi.openLink({ href })
}

function runMarkdownCommand(request: MarkdownRunCommandRequest): void {
  void markdownPreviewHostApi.runCommand(request)
}

function applyTheme(theme: "light" | "dark"): void {
  document.documentElement.dataset.theme = theme
}

function applyPreviewSettings(settings: MarkdownPreviewSettingsParams): void {
  const fontSize = Number.isFinite(settings.fontSize) && settings.fontSize > 0 ? settings.fontSize : 14
  document.documentElement.style.setProperty("--default-font-size", `${fontSize}px`)
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id)
  if (!element) {
    throw new Error(`Missing element #${id}`)
  }
  return element as T
}
