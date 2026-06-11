// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useRef, useState } from "react"
import type mermaidApi from "mermaid"

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
    return <div className="mermaidBlock" dangerouslySetInnerHTML={{ __html: state.svg }} />
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
