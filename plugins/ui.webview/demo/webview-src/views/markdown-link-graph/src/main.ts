// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import cytoscape, { type Core, type ElementDefinition, type EventObject, type LayoutOptions } from "cytoscape"
import fcose from "cytoscape-fcose"
import { marked } from "marked"
import mermaid from "mermaid"
import { apiId, webView, webViewTheme, type WebViewCallable } from "@jetbrains/intellij-webview"

interface MarkdownGraphDto {
  nodes: MarkdownGraphNodeDto[]
  edges: MarkdownGraphEdgeDto[]
  truncated: boolean
}

interface MarkdownGraphNodeDto {
  id: string
  label: string
  kind: "file" | "folder" | "module"
  path?: string
  parent?: string
}

interface MarkdownGraphEdgeDto {
  id: string
  source: string
  target: string
}

interface MarkdownOpenFileRequest {
  fileId: string
}

interface MarkdownOpenFileResult {
  opened: boolean
}

interface MarkdownFilePreviewRequest {
  fileId: string
}

interface MarkdownFilePreviewDto {
  found: boolean
  fileId: string
  title: string
  path: string
  text: string
  truncated: boolean
}

interface MarkdownLinkGraphHostApi extends WebViewCallable {
  getGraph(): Promise<MarkdownGraphDto>
  openFile(params: MarkdownOpenFileRequest): Promise<MarkdownOpenFileResult>
  getFilePreview(params: MarkdownFilePreviewRequest): Promise<MarkdownFilePreviewDto>
}

cytoscape.use(fcose)

const hostApi = webView.callable(apiId<MarkdownLinkGraphHostApi>()("markdown.linkGraph"))
const contentElement = requiredElement<HTMLElement>("content")
const graphElement = requiredElement<HTMLElement>("graph")
const splitterElement = requiredElement<HTMLElement>("splitter")
const refreshButton = requiredElement<HTMLButtonElement>("refreshButton")
const statsElement = requiredElement<HTMLElement>("stats")
const previewPanelElement = requiredElement<HTMLElement>("previewPanel")
const previewTitleElement = requiredElement<HTMLElement>("previewTitle")
const previewPathElement = requiredElement<HTMLElement>("previewPath")
const previewBodyElement = requiredElement<HTMLElement>("previewBody")

let cy: Core | undefined
let graph: MarkdownGraphDto | undefined
let mermaidRenderId = 0
let previewRequest = 0
let resizePointerId: number | undefined

refreshButton.addEventListener("click", () => void loadGraph())
graphElement.addEventListener("wheel", handleGraphWheel, { capture: true, passive: false })
splitterElement.addEventListener("pointerdown", startPreviewResize)
splitterElement.addEventListener("keydown", handleSplitterKeyDown)
webViewTheme.onChanged(() => {
  cy?.style(graphStyles()).update()
  configureMermaid()
  void renderMermaidDiagrams(previewBodyElement, previewRequest)
})

configureMermaid()

void loadGraph()

async function loadGraph(): Promise<void> {
  refreshButton.disabled = true
  statsElement.textContent = "Loading..."
  try {
    graph = await hostApi.getGraph()
    hidePreview()
    renderGraph(graph)
    updateStats(graph)
  }
  catch (error) {
    statsElement.textContent = error instanceof Error ? error.message : "Failed to load graph"
  }
  finally {
    refreshButton.disabled = false
  }
}

function renderGraph(dto: MarkdownGraphDto): void {
  const elements: ElementDefinition[] = [
    ...dto.nodes.map(node => ({
      group: "nodes" as const,
      data: {
        id: node.id,
        label: node.label,
        kind: node.kind,
        path: node.path,
        parent: node.parent,
      },
    })),
    ...dto.edges.map(edge => ({
      group: "edges" as const,
      data: {
        id: edge.id,
        source: edge.source,
        target: edge.target,
      },
    })),
  ]

  if (!cy) {
    cy = cytoscape({
      container: graphElement,
      elements,
      minZoom: 0.03,
      maxZoom: 4,
      wheelSensitivity: 0.08,
      style: graphStyles(),
    })
    cy.on("dbltap", "node[kind = 'file']", event => void openFile(event))
    cy.on("tap", "node", event => void selectNode(event))
    cy.on("tap", event => {
      if (event.target === cy) {
        clearSelection()
      }
    })
    cy.on("mouseover", "node[kind = 'file']", event => event.target.addClass("focused"))
    cy.on("mouseout", "node[kind = 'file']", event => event.target.removeClass("focused"))
  }
  else {
    cy.elements().remove()
    cy.add(elements)
  }
  runLayout()
}

function runLayout(): void {
  if (!cy) {
    return
  }
  cy.layout({
    ...compoundLayoutOptions(),
    stop: () => resetViewportForScroll(),
  } as LayoutOptions).run()
}

function compoundLayoutOptions(): LayoutOptions {
  return {
    name: "fcose",
    animate: false,
    fit: false,
    padding: 72,
    nodeDimensionsIncludeLabels: false,
    quality: "default",
    randomize: false,
    gravityRangeCompound: 1.5,
    idealEdgeLength: 120,
    nodeRepulsion: 6800,
  } as LayoutOptions
}

function handleGraphWheel(event: WheelEvent): void {
  if (event.ctrlKey) {
    return
  }

  const graphCore = cy
  if (!graphCore) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  graphCore.panBy(normalizedPanDelta(event))
}

function normalizedPanDelta(event: WheelEvent): cytoscape.Position {
  let deltaX = event.deltaX
  let deltaY = event.deltaY
  if (event.shiftKey && deltaX === 0) {
    deltaX = deltaY
    deltaY = 0
  }

  const multiplier = event.deltaMode === WheelEvent.DOM_DELTA_LINE
    ? 16
    : event.deltaMode === WheelEvent.DOM_DELTA_PAGE
      ? graphElement.clientHeight
      : 1
  return {
    x: -deltaX * multiplier,
    y: -deltaY * multiplier,
  }
}

function resetViewportForScroll(): void {
  const graphCore = cy
  if (!graphCore) {
    return
  }

  const bounds = graphCore.elements().boundingBox({ includeLabels: false, includeOverlays: false })
  if (!Number.isFinite(bounds.x1) || !Number.isFinite(bounds.y1) || bounds.w <= 0 || bounds.h <= 0) {
    return
  }

  const padding = 72
  const fitZoom = Math.min(
    graphElement.clientWidth / Math.max(bounds.w + padding * 2, 1),
    graphElement.clientHeight / Math.max(bounds.h + padding * 2, 1),
  )
  const zoom = Math.min(1, Math.max(0.35, fitZoom * 1.7))
  graphCore.zoom(zoom)
  graphCore.pan({
    x: padding - bounds.x1 * zoom,
    y: padding - bounds.y1 * zoom,
  })
}

async function openFile(event: EventObject): Promise<void> {
  const node = event.target
  const fileId = node.data("id") as string | undefined
  if (fileId) {
    await hostApi.openFile({ fileId })
  }
}

async function selectNode(event: EventObject): Promise<void> {
  if (!graph) {
    return
  }
  const node = event.target
  const path = node.data("path") as string | undefined
  updateStats(graph, path)

  if (node.data("kind") !== "file") {
    hidePreview()
    return
  }

  const fileId = node.data("id") as string | undefined
  if (!fileId) {
    hidePreview()
    return
  }

  await loadPreview(fileId, node.data("label") as string | undefined, path)
}

async function loadPreview(fileId: string, title?: string, path?: string): Promise<void> {
  const request = ++previewRequest
  showPreview()
  previewTitleElement.textContent = title || "Loading..."
  previewPathElement.textContent = path || ""
  previewBodyElement.textContent = "Loading..."

  try {
    const preview = await hostApi.getFilePreview({ fileId })
    if (request !== previewRequest) {
      return
    }
    if (!preview.found) {
      showPreviewPlaceholder(preview.title, preview.path || "File not found")
      return
    }
    await showFilePreview(preview, request)
  }
  catch (error) {
    if (request !== previewRequest) {
      return
    }
    showPreviewPlaceholder("Preview failed", error instanceof Error ? error.message : "Failed to load file preview")
  }
}

function clearSelection(): void {
  if (graph) {
    updateStats(graph)
  }
  hidePreview()
}

function showPreview(): void {
  if (contentElement.classList.contains("previewVisible")) {
    return
  }
  contentElement.classList.add("previewVisible")
  previewPanelElement.setAttribute("aria-hidden", "false")
  scheduleGraphResize()
}

function hidePreview(): void {
  previewRequest++
  contentElement.classList.remove("previewVisible")
  previewPanelElement.setAttribute("aria-hidden", "true")
  previewTitleElement.textContent = ""
  previewPathElement.textContent = ""
  previewBodyElement.textContent = ""
  scheduleGraphResize()
}

function scheduleGraphResize(): void {
  requestAnimationFrame(() => cy?.resize())
}

function startPreviewResize(event: PointerEvent): void {
  if (event.button !== 0 || !contentElement.classList.contains("previewVisible")) {
    return
  }

  event.preventDefault()
  resizePointerId = event.pointerId
  splitterElement.setPointerCapture(event.pointerId)
  splitterElement.classList.add("resizing")
  document.body.classList.add("resizingPreview")
  window.addEventListener("pointermove", resizePreview)
  window.addEventListener("pointerup", stopPreviewResize)
  window.addEventListener("pointercancel", stopPreviewResize)
}

function resizePreview(event: PointerEvent): void {
  if (event.pointerId !== resizePointerId) {
    return
  }

  const bounds = contentElement.getBoundingClientRect()
  setPreviewWidth(bounds.right - event.clientX)
}

function stopPreviewResize(event: PointerEvent): void {
  if (event.pointerId !== resizePointerId) {
    return
  }

  if (splitterElement.hasPointerCapture(event.pointerId)) {
    splitterElement.releasePointerCapture(event.pointerId)
  }
  resizePointerId = undefined
  splitterElement.classList.remove("resizing")
  document.body.classList.remove("resizingPreview")
  window.removeEventListener("pointermove", resizePreview)
  window.removeEventListener("pointerup", stopPreviewResize)
  window.removeEventListener("pointercancel", stopPreviewResize)
}

function handleSplitterKeyDown(event: KeyboardEvent): void {
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
    return
  }

  event.preventDefault()
  const currentWidth = previewPanelElement.getBoundingClientRect().width || DEFAULT_PREVIEW_WIDTH
  const direction = event.key === "ArrowLeft" ? 1 : -1
  setPreviewWidth(currentWidth + direction * KEYBOARD_RESIZE_STEP)
}

function setPreviewWidth(width: number): void {
  const maxWidth = Math.max(MIN_PREVIEW_WIDTH, contentElement.clientWidth * MAX_PREVIEW_WIDTH_RATIO)
  const nextWidth = Math.min(maxWidth, Math.max(MIN_PREVIEW_WIDTH, width))
  contentElement.style.setProperty("--preview-width", `${Math.round(nextWidth)}px`)
  cy?.resize()
}

async function showFilePreview(preview: MarkdownFilePreviewDto, request: number): Promise<void> {
  previewTitleElement.textContent = preview.title
  previewPathElement.textContent = preview.path
  const markdown = preview.truncated
    ? `${preview.text}\n\n> Preview truncated.`
    : preview.text
  previewBodyElement.innerHTML = marked.parse(markdown, { async: false, gfm: true }) as string
  await renderMermaidDiagrams(previewBodyElement, request)
}

function showPreviewPlaceholder(title: string, text: string): void {
  previewTitleElement.textContent = title
  previewPathElement.textContent = ""
  previewBodyElement.textContent = text
}

async function renderMermaidDiagrams(root: HTMLElement, request: number): Promise<void> {
  if (request !== previewRequest) {
    return
  }

  const codeBlocks = root.querySelectorAll<HTMLElement>("pre > code.language-mermaid")
  for (let i = 0; i < codeBlocks.length; i++) {
    const codeElement = codeBlocks[i]
    const preElement = codeElement.parentElement
    if (!preElement) {
      continue
    }

    const host = document.createElement("div")
    host.className = "mermaidHost"
    host.dataset.mermaidSource = codeElement.textContent || ""
    preElement.replaceWith(host)
  }

  const hosts = root.querySelectorAll<HTMLElement>(".mermaidHost[data-mermaid-source]")
  for (let i = 0; i < hosts.length; i++) {
    if (request !== previewRequest) {
      return
    }
    await renderMermaidCode(hosts[i], hosts[i].dataset.mermaidSource || "", i, request)
  }
}

async function renderMermaidCode(host: HTMLElement, code: string, index: number, request: number): Promise<void> {
  configureMermaid()
  host.classList.remove("hasError")
  host.textContent = "Rendering diagram..."
  try {
    const id = `markdown-link-graph-mermaid-${++mermaidRenderId}-${index}`
    const { svg } = await mermaid.render(id, code)
    if (request !== previewRequest) {
      return
    }
    host.innerHTML = svg
  }
  catch (error) {
    if (request !== previewRequest) {
      return
    }
    host.classList.add("hasError")
    const message = document.createElement("div")
    message.className = "mermaidError"
    message.textContent = error instanceof Error ? error.message : "Failed to render Mermaid diagram"
    const source = document.createElement("pre")
    source.textContent = code
    host.replaceChildren(message, source)
  }
}

function configureMermaid(): void {
  const isLight = document.documentElement.getAttribute("data-theme") === "light"
  const panel = cssVariable("--ij-bg-panel", isLight ? "#F7F8F9" : "#212326")
  const panelAlt = cssVariable("--ij-bg-panel-alt", isLight ? "#FFFFFF" : "#26282C")
  const hover = cssVariable("--ij-bg-hover", isLight ? "#00000012" : "#FFFFFF17")
  const border = cssVariable("--ij-border-strong", isLight ? "#D1D3D9" : "#40434A")
  const textPrimary = cssVariable("--ij-text-primary", isLight ? "#000000" : "#D1D3D9")
  const textSecondary = cssVariable("--ij-text-secondary", "#73767C")
  const accent = cssVariable("--ij-accent", "#3871E1")
  const font = cssVariable("--ij-font", "Inter, Segoe UI, -apple-system, BlinkMacSystemFont, Helvetica Neue, sans-serif")

  mermaid.initialize({
    startOnLoad: false,
    theme: "base",
    securityLevel: "strict",
    suppressErrorRendering: true,
    themeVariables: {
      fontFamily: font,
      fontSize: "13px",
      primaryColor: panel,
      primaryBorderColor: border,
      primaryTextColor: textPrimary,
      secondaryColor: hover,
      secondaryBorderColor: border,
      secondaryTextColor: textPrimary,
      tertiaryColor: panelAlt,
      tertiaryBorderColor: border,
      tertiaryTextColor: textPrimary,
      mainBkg: panel,
      clusterBkg: panelAlt,
      clusterBorder: border,
      lineColor: textSecondary,
      textColor: textPrimary,
      titleColor: textPrimary,
      nodeBorder: border,
      edgeLabelBackground: panel,
      signalColor: textPrimary,
      actorBorder: border,
      actorBkg: panel,
      actorTextColor: textPrimary,
      noteBkgColor: panelAlt,
      noteBorderColor: border,
      noteTextColor: textPrimary,
      activationBkgColor: hover,
      activationBorderColor: accent,
    },
    themeCSS: `
      .node rect,
      .node circle,
      .node ellipse,
      .node polygon,
      .node path {
        rx: 4px;
        ry: 4px;
      }
      .label,
      .edgeLabel,
      .cluster-label,
      .messageText {
        color: ${textPrimary};
        fill: ${textPrimary};
        font-family: ${font};
      }
      .edgeLabel,
      .edgeLabel p,
      .edgeLabel span {
        background: ${panel};
        color: ${textPrimary};
      }
      .flowchart-link,
      .messageLine0,
      .messageLine1 {
        stroke: ${textSecondary};
      }
      .marker {
        fill: ${textSecondary};
        stroke: ${textSecondary};
      }
    `,
  })
}

function updateStats(dto: MarkdownGraphDto, selectedPath?: string): void {
  const truncated = dto.truncated ? " truncated" : ""
  const selected = selectedPath ? ` selected: ${selectedPath}` : ""
  statsElement.textContent = `${dto.nodes.length} nodes, ${dto.edges.length} edges${truncated}${selected}`
}

function graphStyles(): cytoscape.StylesheetJson {
  const theme = graphThemeColors()
  return [
    {
      selector: "node",
      style: {
        "background-color": theme.neutral,
        "border-color": theme.neutralBorder,
        "border-width": 1,
        color: theme.textPrimary,
        label: "",
        "font-family": theme.fontFamily,
        "font-size": 11,
        "min-zoomed-font-size": 7,
        "text-halign": "center",
        "text-valign": "bottom",
        "text-margin-y": 4,
        "text-wrap": "wrap",
        "text-max-width": "120px",
        height: 10,
        width: 10,
      },
    },
    {
      selector: "node[kind = 'file']",
      style: {
        "background-color": theme.success,
        "border-color": theme.successBorder,
        shape: "round-rectangle",
      },
    },
    {
      selector: "node[kind = 'file'].focused, node[kind = 'file']:selected",
      style: {
        label: "data(label)",
        height: 18,
        width: 18,
        "font-size": 12,
        "text-background-color": theme.panel,
        "text-background-opacity": 0.86,
        "text-background-padding": "3px",
        "text-border-color": theme.border,
        "text-border-opacity": 0.8,
        "text-border-width": 1,
        "z-index": 20,
      },
    },
    {
      selector: "node[kind = 'module']",
      style: {
        "background-color": theme.accent,
        "background-opacity": 0.07,
        "border-color": theme.accentSoftBorder,
        "border-style": "solid",
        "border-opacity": 0.42,
        "border-width": 1,
        color: theme.textMuted,
        "compound-sizing-wrt-labels": "include",
        label: "data(label)",
        "padding": "22px",
        shape: "round-rectangle",
        "text-valign": "top",
        "z-index": 0,
      },
    },
    {
      selector: "node[kind = 'folder']",
      style: {
        "background-color": theme.neutral,
        "background-opacity": 0.04,
        "border-color": theme.neutralBorder,
        "border-style": "dashed",
        "border-opacity": 0.36,
        color: theme.textMuted,
        "compound-sizing-wrt-labels": "include",
        label: "data(label)",
        "padding": "16px",
        shape: "round-rectangle",
        "text-valign": "top",
        "z-index": 0,
      },
    },
    {
      selector: "edge",
      style: {
        "curve-style": "bezier",
        "line-color": theme.textMuted,
        "line-opacity": 0.26,
        "target-arrow-color": theme.textMuted,
        "target-arrow-shape": "triangle",
        "arrow-scale": 0.58,
        width: 0.8,
      },
    },
    {
      selector: ":selected",
      style: {
        "border-width": 3,
        "border-color": theme.warning,
        "line-color": theme.warning,
        "target-arrow-color": theme.warning,
      },
    },
  ]
}

function graphThemeColors(): Record<string, string> {
  return {
    accent: cssVariable("--ij-accent", "#3871E1"),
    accentSoftBorder: cssVariable("--ij-accent-soft-border", "#2E4D89"),
    border: cssVariable("--ij-border", "#3C3F41"),
    fontFamily: cssVariable("--ij-font", "Inter, sans-serif"),
    neutral: cssVariable("--ij-neutral-text", "#B5B7BD"),
    neutralBorder: cssVariable("--ij-neutral-border", "#FFFFFF21"),
    panel: cssVariable("--ij-bg-panel", "#212326"),
    success: cssVariable("--ij-success", "#6DB083"),
    successBorder: cssVariable("--ij-success-border", "#29583C"),
    textMuted: cssVariable("--ij-text-muted", "#9FA2A8"),
    textPrimary: cssVariable("--ij-text-primary", "#D1D3D9"),
    warning: cssVariable("--ij-warning", "#D59637"),
  }
}

function cssVariable(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
  return value.replace(/^#([0-9a-fA-F]{6})[0-9a-fA-F]{2}$/, "#$1")
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id)
  if (!element) {
    throw new Error(`Missing element #${id}`)
  }
  return element as T
}

const DEFAULT_PREVIEW_WIDTH = 420
const KEYBOARD_RESIZE_STEP = 32
const MAX_PREVIEW_WIDTH_RATIO = 0.75
const MIN_PREVIEW_WIDTH = 280
