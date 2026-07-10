// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { apiId, type WebViewCallable, type WebViewImplementable } from "./webViewApi"

export type WebViewFocusDirection = "forward" | "backward"

export interface WebViewFocusEntry {
  direction: WebViewFocusDirection
}

export interface WebViewFocusExit {
  direction: WebViewFocusDirection
}

export interface WebViewFocusPageApi extends WebViewImplementable {
  enter(params: WebViewFocusEntry): void
  leave(): void
}

export interface WebViewFocusHostApi extends WebViewCallable {
  activated(): void
  exit(params: WebViewFocusExit): void
}

export const WEBVIEW_FOCUS_LEAVE_EVENT = "wvi-focus-leave"

export function addWebViewFocusLeaveListener(listener: EventListener): () => void {
  window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener)
  return () => window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener)
}

export const webViewFocusPageApiId = apiId<WebViewFocusPageApi>()("webview.focus")
export const webViewFocusHostApiId = apiId<WebViewFocusHostApi>()("webview.focus")
