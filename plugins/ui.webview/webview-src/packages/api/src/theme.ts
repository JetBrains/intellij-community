// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { WebViewMessageRegistration } from "./notifications"
import { apiId, type WebViewCallable, type WebViewImplementable } from "./webViewApi"

export type WebViewTheme = "light" | "dark"

export interface WebViewThemeFontInfo {
  families: string[]
  size: number
  lineHeight?: number
  sizes?: WebViewThemeFontSizes
}

export interface WebViewThemeFontSizes {
  h0?: number
  h1?: number
  h2?: number
  h3?: number
  h4?: number
  regular?: number
  medium?: number
  small?: number
  mini?: number
}

export interface WebViewThemeEditorFontInfo extends WebViewThemeFontInfo {
  ligatures: boolean
  fontFeatureSettings: string[]
}

export interface WebViewThemeFonts {
  ui: WebViewThemeFontInfo
  editor: WebViewThemeEditorFontInfo
}

export interface WebViewThemeChangedParams {
  theme: WebViewTheme
  fonts?: WebViewThemeFonts
}

export interface WebViewThemeHostEvents extends WebViewImplementable {
  themeChanged(params: WebViewThemeChangedParams): void
}

export interface WebViewThemePageEvents extends WebViewCallable {
  themeRequest(): void
}

export const webViewThemeHostEventsId = apiId<WebViewThemeHostEvents>()("webview.theme")
export const webViewThemePageEventsId = apiId<WebViewThemePageEvents>()("webview.theme")

export interface WebViewThemeApi {
  readonly current: WebViewTheme
  onChanged(handler: (theme: WebViewTheme) => void): WebViewMessageRegistration
}

declare global {
  interface Window {
    __WVI_THEME__?: WebViewThemeApi
  }
}

export function getWebViewTheme(): WebViewThemeApi | undefined {
  return window.__WVI_THEME__
}

export function requireWebViewTheme(): WebViewThemeApi {
  const theme = getWebViewTheme()
  if (!theme) {
    throw new Error("WebView theme is not installed. Load /__webview/wvi-platform-features.js after /__webview/wvi-bridge.js before theme-aware application code.")
  }
  return theme
}

function createLazyWebViewTheme(): WebViewThemeApi {
  return new Proxy({}, {
    get(_target, property, receiver) {
      return Reflect.get(requireWebViewTheme(), property, receiver)
    },
    set(_target, property, value, receiver) {
      return Reflect.set(requireWebViewTheme(), property, value, receiver)
    },
    has(_target, property) {
      return property in requireWebViewTheme()
    },
  }) as WebViewThemeApi
}

export const webViewTheme: WebViewThemeApi = createLazyWebViewTheme()
