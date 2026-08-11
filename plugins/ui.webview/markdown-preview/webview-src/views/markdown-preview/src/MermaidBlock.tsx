// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useRef, useState } from "react"
import { select } from "d3-selection"
import { zoom, zoomIdentity, type D3ZoomEvent, type ZoomBehavior } from "d3-zoom"
import type mermaidApi from "mermaid"
import {
  MARKDOWN_ZOOM_BUTTON_FACTOR,
  MARKDOWN_ZOOM_SCALE_EXTENT,
  MarkdownZoomToolbar,
  shouldHandleZoomEvent,
} from "./MarkdownZoomControls"

interface MermaidBlockProps {
  chart: string
  theme: "light" | "dark"
}

type RenderState =
  | { kind: "rendering" }
  | { kind: "rendered", svg: string }
  | { kind: "error", message: string }

let mermaidBlockId = 0
let mermaidRenderId = 0
let mermaidModule: Promise<Mermaid> | undefined
const PRESERVED_SVG_TAGS = new Set(["defs", "style", "title", "desc", "metadata", "marker"])

export function MermaidBlock({ chart, theme }: MermaidBlockProps) {
  const hostId = useRef(`markdown-preview-mermaid-${++mermaidBlockId}`)
  const [state, setState] = useState<RenderState>({ kind: "rendering" })

  useEffect(() => {
    let cancelled = false
    const renderId = `${hostId.current}-${++mermaidRenderId}`

    setState({ kind: "rendering" })
    loadMermaid()
      .then(mermaid => {
        configureMermaid(mermaid, theme)
        return mermaid.render(renderId, chart)
      })
      .then(({ svg }) => {
        if (!cancelled) {
          setState({ kind: "rendered", svg })
        }
      })
      .catch(error => {
        if (!cancelled) {
          setState({ kind: "error", message: error instanceof Error ? error.message : "Failed to render Mermaid diagram" })
        }
      })

    return () => {
      cancelled = true
    }
  }, [chart, theme])

  if (state.kind === "rendered") {
    return <RenderedMermaidDiagram svg={state.svg} />
  }
  if (state.kind === "error") {
    return (
      <div className="mermaidBlock hasError">
        <div className="mermaidError">{state.message}</div>
        <pre><code>{chart}</code></pre>
      </div>
    )
  }
  return <div className="mermaidBlock isRendering">Rendering diagram...</div>
}

function RenderedMermaidDiagram({ svg }: { svg: string }) {
  const hostRef = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement | null>(null)
  const zoomBehaviorRef = useRef<ZoomBehavior<SVGSVGElement, unknown> | null>(null)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    host.innerHTML = svg
    const svgElement = host.querySelector<SVGSVGElement>("svg")
    if (!svgElement) {
      return () => {
        host.innerHTML = ""
      }
    }

    prepareSvg(svgElement, "mermaidSvg")
    const panZoomGroup = wrapSvgContent(svgElement, "mermaidPanZoom")
    fitSvgViewBoxToContent(svgElement, panZoomGroup)
    svgRef.current = svgElement

    const zoomBehavior = zoom<SVGSVGElement, unknown>()
      .filter(shouldHandleZoomEvent)
      .scaleExtent(MARKDOWN_ZOOM_SCALE_EXTENT)
      .on("zoom", (event: D3ZoomEvent<SVGSVGElement, unknown>) => {
        panZoomGroup.setAttribute("transform", event.transform.toString())
      })
    zoomBehaviorRef.current = zoomBehavior

    const svgSelection = select<SVGSVGElement, unknown>(svgElement)
    svgSelection.call(zoomBehavior)
    svgSelection.call(zoomBehavior.transform, zoomIdentity)

    return () => {
      svgSelection.on(".zoom", null)
      host.innerHTML = ""
      svgRef.current = null
      zoomBehaviorRef.current = null
    }
  }, [svg])

  function zoomBy(factor: number): void {
    const svgElement = svgRef.current
    const zoomBehavior = zoomBehaviorRef.current
    if (!svgElement || !zoomBehavior) return
    select<SVGSVGElement, unknown>(svgElement).call(zoomBehavior.scaleBy, factor)
  }

  function resetZoom(): void {
    const svgElement = svgRef.current
    const zoomBehavior = zoomBehaviorRef.current
    if (!svgElement || !zoomBehavior) return
    select<SVGSVGElement, unknown>(svgElement).call(zoomBehavior.transform, zoomIdentity)
  }

  return (
    <div className="mermaidBlock isInteractive">
      <div className="mermaidViewport" ref={hostRef} />
      <MarkdownZoomToolbar
        targetLabel="diagram"
        className="mermaidToolbar"
        buttonClassName="mermaidToolbarButton"
        onZoomOut={() => zoomBy(1 / MARKDOWN_ZOOM_BUTTON_FACTOR)}
        onResetZoom={resetZoom}
        onZoomIn={() => zoomBy(MARKDOWN_ZOOM_BUTTON_FACTOR)}
      />
    </div>
  )
}

function prepareSvg(svgElement: SVGSVGElement, className: string): void {
  svgElement.classList.add(className)
  svgElement.setAttribute("preserveAspectRatio", "xMidYMid meet")
  if (!svgElement.hasAttribute("viewBox")) {
    const width = svgDimension(svgElement.getAttribute("width"))
    const height = svgDimension(svgElement.getAttribute("height"))
    if (width && height) svgElement.setAttribute("viewBox", `0 0 ${width} ${height}`)
  }
  svgElement.removeAttribute("width")
  svgElement.removeAttribute("height")
  svgElement.style.removeProperty("width")
  svgElement.style.removeProperty("height")
  svgElement.style.removeProperty("max-width")
}

function wrapSvgContent(svgElement: SVGSVGElement, className: string): SVGGElement {
  for (const child of Array.from(svgElement.children)) {
    if (child.tagName.toLowerCase() === "g" && child.classList.contains(className)) return child as SVGGElement
  }

  const group = document.createElementNS("http://www.w3.org/2000/svg", "g")
  group.setAttribute("class", className)
  for (const child of Array.from(svgElement.childNodes)) {
    if (child.nodeType !== Node.ELEMENT_NODE) continue
    const element = child as Element
    if (PRESERVED_SVG_TAGS.has(element.tagName.toLowerCase())) continue
    group.appendChild(element)
  }
  svgElement.appendChild(group)
  return group
}

function fitSvgViewBoxToContent(svgElement: SVGSVGElement, contentElement: SVGGraphicsElement): void {
  try {
    const box = contentElement.getBBox()
    if (box.width <= 0 || box.height <= 0) return
    const padding = 24
    svgElement.setAttribute("viewBox", `${box.x - padding} ${box.y - padding} ${box.width + padding * 2} ${box.height + padding * 2}`)
  }
  catch {
    // Some SVG fragments cannot be measured until attached; keep Mermaid's viewBox in that case.
  }
}

function svgDimension(value: string | null): number | undefined {
  if (!value) return undefined
  const dimension = Number.parseFloat(value)
  return Number.isFinite(dimension) && dimension > 0 ? dimension : undefined
}

function loadMermaid(): Promise<Mermaid> {
  mermaidModule ||= import("mermaid").then(module => module.default)
  return mermaidModule
}

function configureMermaid(mermaid: Mermaid, theme: "light" | "dark"): void {
  const isLight = theme === "light"
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

function cssVariable(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
  return value.replace(/^#([0-9a-fA-F]{6})[0-9a-fA-F]{2}$/, "#$1")
}

type Mermaid = typeof mermaidApi
