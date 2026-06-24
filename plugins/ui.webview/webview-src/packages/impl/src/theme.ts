// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {
  WebViewApiId,
  WebViewBridge,
  WebViewMessageRegistration,
  WebViewTheme,
  WebViewThemeApi,
  WebViewThemeChangedParams,
  WebViewThemeEditorFontInfo,
  WebViewThemeFontInfo,
  WebViewThemeFontSizes,
  WebViewThemeFonts,
  WebViewThemeHostEvents,
  WebViewThemePageEvents,
} from "@jetbrains/intellij-webview"
import stylesheet from "../../styles/src/theming/ij-themes.css?raw"

const IJ_THEME_STYLES_ID = "__wvi-ij-themes"
const JB_THEME_TOKENS_ID = "jb-webview-theme-tokens"
const THEME_QUERY_PARAMETER = "__webviewTheme"
const THEME_API_NAMESPACE = "webview.theme"
const webViewThemeHostEventsId = { namespace: THEME_API_NAMESPACE } as WebViewApiId<WebViewThemeHostEvents>
const webViewThemePageEventsId = { namespace: THEME_API_NAMESPACE } as WebViewApiId<WebViewThemePageEvents>

const jbThemeTokenStyles = `
:root {
  --jb-font-family: var(--ij-font, "Inter", "Segoe UI", -apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif);
  --jb-font-size: var(--ij-font-size, 13px);
  --jb-font-size-h0: var(--ij-font-size-h0, calc(var(--ij-font-size, 13px) + 12px));
  --jb-font-size-h1: var(--ij-font-size-h1, calc(var(--ij-font-size, 13px) + 9px));
  --jb-font-size-h2: var(--ij-font-size-h2, calc(var(--ij-font-size, 13px) + 5px));
  --jb-font-size-h3: var(--ij-font-size-h3, calc(var(--ij-font-size, 13px) + 3px));
  --jb-font-size-h4: var(--ij-font-size-h4, calc(var(--ij-font-size, 13px) + 1px));
  --jb-font-size-regular: var(--ij-font-size-regular, var(--ij-font-size, 13px));
  --jb-font-size-medium: var(--ij-font-size-medium, calc(var(--ij-font-size, 13px) - 1px));
  --jb-font-size-small: var(--ij-font-size-small, max(calc(var(--ij-font-size, 13px) - 2px), 11px));
  --jb-font-size-mini: var(--ij-font-size-mini, max(calc(var(--ij-font-size, 13px) - 4px), 9px));
  --jb-line-height: var(--ij-line-height-default, 16px);
  --jb-line-height-compact: var(--ij-line-height-compact, calc(var(--ij-line-height-default, 16px) - 2px));
  --jb-line-height-paragraph: var(--ij-line-height-paragraph, calc(var(--ij-line-height-default, 16px) + 2px));
  --jb-line-height-heading: var(--ij-line-height-heading, calc(var(--ij-line-height-default, 16px) + 4px));
  --jb-font-weight-regular: var(--ij-font-weight-regular, 400);
  --jb-font-weight-medium: var(--ij-font-weight-medium, 500);
  --jb-control-height: var(--ij-control-height, max(28px, calc(var(--ij-line-height-default, 16px) + 12px)));
  --jb-control-height-compact: var(--ij-control-height-compact, max(24px, calc(var(--ij-line-height-default, 16px) + 8px)));
  --jb-control-radius: var(--ij-radius-control, 4px);
  --jb-control-padding-x: 8px;
  --jb-control-gap: 6px;
  --jb-space-xs: 4px;
  --jb-space-sm: 8px;
  --jb-space-md: 12px;
  --jb-space-lg: 16px;
  --jb-bg-window: var(--ij-bg-window, #ffffff);
  --jb-bg-panel: var(--ij-bg-panel, #f7f8f9);
  --jb-bg-control: var(--ij-bg-control-raised, #ffffff);
  --jb-bg-input: var(--ij-bg-input, #ffffff);
  --jb-bg-hover: var(--ij-bg-hover, #00000012);
  --jb-bg-pressed: var(--ij-bg-pressed, #00000020);
  --jb-bg-selected: var(--ij-bg-selected, #d0dffe);
  --jb-bg-selected-muted: var(--ij-bg-selected-muted, #e3ebfe);
  --jb-border-color: var(--ij-control-border-raised, #b5b7bd);
  --jb-border-color-muted: var(--ij-border-inline, #e9eaee);
  --jb-border-color-strong: var(--ij-border-strong, #d1d3d9);
  --jb-text-color: var(--ij-text-primary, #000000);
  --jb-text-muted: var(--ij-text-muted, #5f6269);
  --jb-text-secondary: var(--ij-text-secondary, #73767c);
  --jb-text-disabled: var(--ij-text-disabled, #9fa2a8);
  --jb-text-on-accent: var(--ij-text-on-accent, #ffffff);
  --jb-accent-color: var(--ij-accent, #3871e1);
  --jb-accent-hover-color: var(--ij-accent-hover, #2f5eb9);
  --jb-accent-text-color: var(--ij-accent-text, #2f5eb9);
  --jb-accent-soft-bg: var(--ij-accent-soft, #3871e129);
  --jb-danger-color: var(--ij-danger, #c54e58);
  --jb-danger-bg: var(--ij-danger-soft, #fff6f5);
  --jb-danger-border-color: var(--ij-danger-border, #ffc4c5);
  --jb-warning-color: var(--ij-warning, #a56906);
  --jb-warning-bg: var(--ij-warning-soft, #fff6e9);
  --jb-warning-border-color: var(--ij-warning-border, #f4cd9a);
  --jb-focus-ring: var(--ij-focus-ring, 0 0 0 2px rgba(56, 113, 225, 0.32));
  --jb-popup-shadow: var(--ij-popup-shadow, 0 8px 24px #00000026);
}
`

/**
 * Installs IntelliJ theming for the current WebView page: injects shared IJ theme styles,
 * exposes the page theme API, applies the initial theme, and keeps the page reactive to
 * theme changes reported by the host over the WebView bridge.
 */
export function installIJTheming(bridge: WebViewBridge): void {
  ensureIJThemeStylesInstalled()
  ensureJBThemeTokensInstalled()
  if (window.__WVI_THEME__) {
    return
  }

  const theme = createWebViewTheme()
  window.__WVI_THEME__ = theme.api
  theme.install(bridge)
}

export function createWebViewTheme(): WebViewThemeController {
  let currentTheme = readInitialTheme()
  const listeners: Array<(theme: WebViewTheme) => void> = []
  let hostEventsRegistration: WebViewMessageRegistration | undefined
  applyThemeAttribute(currentTheme)

  const api: WebViewThemeApi = {
    get current() {
      return currentTheme
    },
    onChanged(handler: (theme: WebViewTheme) => void): WebViewMessageRegistration {
      if (typeof handler !== "function") {
        throw new Error("WebView theme listener must be a function")
      }
      listeners.push(handler)
      return {
        close() {
          const index = listeners.indexOf(handler)
          if (index >= 0) {
            listeners.splice(index, 1)
          }
        },
      }
    },
  }

  function applyHostTheme(params: unknown): void {
    const payload = params && typeof params === "object" ? params as Partial<WebViewThemeChangedParams> : undefined
    applyThemeFonts(payload?.fonts)

    const theme = normalizeTheme(payload?.theme)
    if (theme && theme !== currentTheme) {
      currentTheme = theme
      applyThemeAttribute(theme)
      for (const listener of listeners.slice()) {
        try {
          listener(theme)
        }
        catch (err) {
          console.error("[__WVI__] theme listener threw:", err)
        }
      }
    }
  }

  function install(bridge: WebViewBridge): void {
    hostEventsRegistration?.close()
    hostEventsRegistration = bridge.implement(webViewThemeHostEventsId, {
      themeChanged(params) {
        applyHostTheme(params)
      },
    })
    bridge.callable(webViewThemePageEventsId).themeRequest()
  }

  return { api, install }
}

export interface WebViewThemeController {
  readonly api: WebViewThemeApi
  install(bridge: WebViewBridge): void
}

function ensureIJThemeStylesInstalled(): void {
  if (document.getElementById(IJ_THEME_STYLES_ID)) {
    return
  }

  const style = document.createElement("style")
  style.id = IJ_THEME_STYLES_ID
  style.textContent = stylesheet

  const target = document.head || document.documentElement
  target.insertBefore(style, target.firstChild)
}

function ensureJBThemeTokensInstalled(): void {
  const existing = document.getElementById(JB_THEME_TOKENS_ID)
  if (existing) {
    if (existing.textContent !== jbThemeTokenStyles) {
      existing.textContent = jbThemeTokenStyles
    }
    return
  }

  const style = document.createElement("style")
  style.id = JB_THEME_TOKENS_ID
  style.textContent = jbThemeTokenStyles

  const target = document.head || document.documentElement
  target.insertBefore(style, target.firstChild)
}

function readInitialTheme(): WebViewTheme {
  try {
    const params = new URLSearchParams(window.location.search)
    const themes = params.getAll(THEME_QUERY_PARAMETER)
    return normalizeTheme(themes[themes.length - 1]) ?? "dark"
  }
  catch (_) {
    return "dark"
  }
}

function normalizeTheme(theme: unknown): WebViewTheme | undefined {
  return theme === "light" || theme === "dark" ? theme : undefined
}

function applyThemeAttribute(theme: WebViewTheme): void {
  document.documentElement.setAttribute("data-theme", theme)
  document.documentElement.style.colorScheme = theme
  ensureJBThemeTokensInstalled()
}

function applyThemeFonts(fonts: unknown): void {
  if (!fonts || typeof fonts !== "object") {
    return
  }
  const payload = fonts as Partial<WebViewThemeFonts>
  applyUiFont(payload.ui)
  applyEditorFont(payload.editor)
}

function applyUiFont(font: unknown): void {
  const payload = normalizeFontInfo(font)
  if (!payload) {
    return
  }
  const style = document.documentElement.style
  style.setProperty("--ij-font", toCssFontFamily(payload.families))
  style.setProperty("--ij-font-size", `${payload.size}px`)
  style.setProperty("--ij-font-size-regular", `${payload.sizes?.regular ?? payload.size}px`)
  applyUiFontSizes(style, payload.sizes)
  if (payload.lineHeight !== undefined) {
    style.setProperty("--ij-line-height-default", `${payload.lineHeight}px`)
  }
}

function applyUiFontSizes(style: CSSStyleDeclaration, sizes: WebViewThemeFontSizes | undefined): void {
  if (!sizes) {
    return
  }
  for (const [role, variable] of uiFontSizeVariables) {
    const size = sizes[role]
    if (typeof size === "number" && Number.isFinite(size) && size > 0) {
      style.setProperty(variable, `${size}px`)
    }
  }
}

function applyEditorFont(font: unknown): void {
  const payload = normalizeEditorFontInfo(font)
  if (!payload) {
    return
  }
  const style = document.documentElement.style
  style.setProperty("--ij-editor-font", toCssFontFamily(payload.families))
  style.setProperty("--ij-editor-font-size", `${payload.size}px`)
  if (payload.lineHeight !== undefined) {
    style.setProperty("--ij-editor-line-height", `${payload.lineHeight}`)
  }
  style.setProperty("--ij-editor-font-variant-ligatures", payload.ligatures ? "normal" : "none")
  style.setProperty("--ij-editor-font-feature-settings", toCssFontFeatureSettings(payload.fontFeatureSettings))
}

function normalizeFontInfo(font: unknown): WebViewThemeFontInfo | undefined {
  if (!font || typeof font !== "object") {
    return undefined
  }
  const payload = font as Partial<WebViewThemeFontInfo>
  const families = Array.isArray(payload.families)
    ? payload.families.filter((family): family is string => typeof family === "string" && family.trim().length > 0)
    : []
  if (families.length === 0 || typeof payload.size !== "number" || !Number.isFinite(payload.size) || payload.size <= 0) {
    return undefined
  }
  const lineHeight = typeof payload.lineHeight === "number" && Number.isFinite(payload.lineHeight) && payload.lineHeight > 0
    ? payload.lineHeight
    : undefined
  const sizes = normalizeUiFontSizes(payload.sizes)
  return { families, size: payload.size, lineHeight, ...(sizes ? { sizes } : {}) }
}

function normalizeUiFontSizes(sizes: unknown): WebViewThemeFontSizes | undefined {
  if (!sizes || typeof sizes !== "object") {
    return undefined
  }
  const payload = sizes as Partial<WebViewThemeFontSizes>
  const result: WebViewThemeFontSizes = {}
  for (const [role] of uiFontSizeVariables) {
    const size = payload[role]
    if (typeof size === "number" && Number.isFinite(size) && size > 0) {
      result[role] = size
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}

function normalizeEditorFontInfo(font: unknown): WebViewThemeEditorFontInfo | undefined {
  const payload = normalizeFontInfo(font)
  if (!payload || !font || typeof font !== "object") {
    return undefined
  }
  const editorPayload = font as Partial<WebViewThemeEditorFontInfo>
  const fontFeatureSettings = Array.isArray(editorPayload.fontFeatureSettings)
    ? editorPayload.fontFeatureSettings.filter((feature): feature is string => typeof feature === "string")
    : []
  return {
    ...payload,
    ligatures: editorPayload.ligatures !== false,
    fontFeatureSettings,
  }
}

function toCssFontFamily(families: string[]): string {
  return families.map((family) => cssString(family.trim())).join(", ")
}

function toCssFontFeatureSettings(features: string[]): string {
  const cssFeatures = features
    .filter((feature) => /^[A-Za-z0-9]{4}$/.test(feature))
    .map((feature) => `${cssString(feature)} 1`)
  return cssFeatures.length === 0 ? "normal" : cssFeatures.join(", ")
}

function cssString(value: string): string {
  return `"${value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"`
}

const uiFontSizeVariables: Array<[keyof WebViewThemeFontSizes, string]> = [
  ["h0", "--ij-font-size-h0"],
  ["h1", "--ij-font-size-h1"],
  ["h2", "--ij-font-size-h2"],
  ["h3", "--ij-font-size-h3"],
  ["h4", "--ij-font-size-h4"],
  ["regular", "--ij-font-size-regular"],
  ["medium", "--ij-font-size-medium"],
  ["small", "--ij-font-size-small"],
  ["mini", "--ij-font-size-mini"],
]
