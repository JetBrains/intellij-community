// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { createRoot } from "react-dom/client"
import { apiId, webView, webViewTheme, type WebViewCallable, type WebViewImplementable } from "@jetbrains/intellij-webview"
import { MarkdownPreviewApp, scrollMarkdownPreviewToLine } from "./MarkdownPreviewApp"
import { decorateSourceBlocks } from "./markdownSourcePositions"
import type {
  MarkdownChangedBlockDescriptor,
  MarkdownNavigatePathLinkRequest,
  MarkdownPreviewSettings,
  MarkdownResolvePathLinksRequest,
  MarkdownResolvedPathLinksResponse,
  MarkdownResolveRunCommandsRequest,
  MarkdownResolvedRunCommandsResponse,
  MarkdownRunCommandRequest,
  MarkdownSetFontSizeRequest,
  MarkdownSourceRange,
} from "./markdownPreviewTypes"

interface MarkdownContentChangedParams {
  markdown: string
  scrollLine: number
  settings: MarkdownPreviewSettings
  contentVersion: number
  changes: MarkdownChangedBlockDescriptor[]
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
  resolveRunCommands(params: MarkdownResolveRunCommandsRequest): Promise<MarkdownResolvedRunCommandsResponse>
  runCommand(params: MarkdownRunCommandRequest): Promise<void>
  resolvePathLinks(params: MarkdownResolvePathLinksRequest): Promise<MarkdownResolvedPathLinksResponse>
  navigatePathLink(params: MarkdownNavigatePathLinkRequest): Promise<void>
  setFontSize(params: MarkdownSetFontSizeRequest): Promise<void>
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
let contentVersion = -1
let changes: MarkdownChangedBlockDescriptor[] = []
let selection: MarkdownSourceRange | undefined
let theme = webViewTheme.current
let previewSettings = defaultPreviewSettings()

webView.implement(markdownPreviewPageApiId, {
  contentChanged(params) {
    markdown = params.markdown
    scrollLine = params.scrollLine
    contentVersion = params.contentVersion
    previewSettings = normalizePreviewSettings(params.settings)
    applyPreviewSettings(previewSettings)
    changes = params.changes ?? []
    renderPreview()
  },
  scrollToLine(params) {
    scrollLine = params.line
    scrollMarkdownPreviewToLine(scrollLine)
  },
  selectionChanged(params) {
    selection = params.selection ?? undefined
    decorateSourceBlocks(selection, changes)
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
      contentVersion={contentVersion}
      changes={changes}
      selection={selection}
      settings={previewSettings}
      theme={theme}
      onOpenLink={openMarkdownLink}
      onResolveRunCommands={resolveMarkdownRunCommands}
      onRunCommand={runMarkdownCommand}
      onResolvePathLinks={resolveMarkdownPathLinks}
      onNavigatePathLink={navigateMarkdownPathLink}
      onSetFontSize={setMarkdownFontSize}
    />
  )
}

function openMarkdownLink(href: string): void {
  void markdownPreviewHostApi.openLink({ href })
}

function resolveMarkdownRunCommands(request: MarkdownResolveRunCommandsRequest): Promise<MarkdownResolvedRunCommandsResponse> {
  return markdownPreviewHostApi.resolveRunCommands(request)
}

function runMarkdownCommand(request: MarkdownRunCommandRequest): void {
  void markdownPreviewHostApi.runCommand(request)
}

function resolveMarkdownPathLinks(request: MarkdownResolvePathLinksRequest): Promise<MarkdownResolvedPathLinksResponse> {
  return markdownPreviewHostApi.resolvePathLinks(request)
}

function navigateMarkdownPathLink(request: MarkdownNavigatePathLinkRequest): void {
  void markdownPreviewHostApi.navigatePathLink(request)
}

function setMarkdownFontSize(fontSize: number): void {
  if (fontSize === previewSettings.effectiveFontSize) return
  void markdownPreviewHostApi.setFontSize({ fontSize })
}

function applyTheme(theme: "light" | "dark"): void {
  document.documentElement.dataset.theme = theme
}

function applyPreviewSettings(settings: MarkdownPreviewSettings): void {
  const fontSize = settings.fontSize
  if (typeof fontSize === "number" && Number.isFinite(fontSize) && fontSize > 0) {
    document.documentElement.style.setProperty("--markdown-preview-font-size", `${fontSize}px`)
  }
  else {
    document.documentElement.style.removeProperty("--markdown-preview-font-size")
  }
}

function defaultPreviewSettings(): MarkdownPreviewSettings {
  const fontSize = 13
  return {
    fontSize: null,
    effectiveFontSize: fontSize,
    defaultFontSize: fontSize,
    fontSizeOptions: [fontSize],
  }
}

function normalizePreviewSettings(settings: MarkdownPreviewSettings): MarkdownPreviewSettings {
  const effectiveFontSize = positiveFiniteNumber(settings.effectiveFontSize) ?? previewSettings.effectiveFontSize
  const defaultFontSize = positiveFiniteNumber(settings.defaultFontSize) ?? previewSettings.defaultFontSize
  const rawFontSizeOptions = Array.isArray(settings.fontSizeOptions) ? settings.fontSizeOptions : []
  const fontSizeOptions = uniqueSortedNumbers([...rawFontSizeOptions, effectiveFontSize, defaultFontSize])
  return {
    fontSize: settings.fontSize,
    effectiveFontSize,
    defaultFontSize,
    fontSizeOptions,
  }
}

function positiveFiniteNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : undefined
}

function uniqueSortedNumbers(values: number[]): number[] {
  return Array.from(new Set(values.filter(value => Number.isFinite(value) && value > 0))).sort((left, right) => left - right)
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id)
  if (!element) {
    throw new Error(`Missing element #${id}`)
  }
  return element as T
}
