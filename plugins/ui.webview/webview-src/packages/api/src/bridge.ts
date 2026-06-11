// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {
  Callable,
  ValidWebViewApi,
  WebViewApiId,
  WebViewCallable,
  WebViewImplementable,
  WebViewImplementation,
} from "./webViewApi"
import type {
  WebViewMessageRegistration,
  WebViewNotificationBinding,
  WebViewNotificationBindings,
  WebViewNotificationDescriptor,
} from "./notifications"

export type WebViewTransport = "webview2" | "webkit" | "jcef" | "missing" | string

export interface WebViewRuntimeOptions {
  bridge?: WebViewBridge
}

export interface WebViewHostCallOptions {
  signal?: AbortSignal
}

export interface WebViewBridge {
  readonly __installed: true
  transport(): WebViewTransport
  callable<Api extends WebViewCallable>(id: WebViewApiId<Api> & ValidWebViewApi<Api>, options?: WebViewRuntimeOptions): Callable<Api>
  implement<Api extends WebViewImplementable>(
    id: WebViewApiId<Api> & ValidWebViewApi<Api>,
    implementation: WebViewImplementation<Api>,
    options?: WebViewRuntimeOptions,
  ): WebViewMessageRegistration
  notification<Params>(descriptor: WebViewNotificationDescriptor<Params>): WebViewNotificationBinding<Params>
  notifications<Descriptors extends Record<string, WebViewNotificationDescriptor<unknown>>>(
    descriptors: Descriptors,
  ): WebViewNotificationBindings<Descriptors>
}

declare global {
  interface Window {
    __WVI__?: WebViewBridge
  }
}

export function getWebViewBridge(): WebViewBridge | undefined {
  return window.__WVI__
}

export function requireWebViewBridge(): WebViewBridge {
  const bridge = getWebViewBridge()
  if (!bridge) {
    throw new Error("WebView bridge is not installed. Load /__webview/wvi-bridge.js before application code.")
  }
  return bridge
}

function createLazyWebViewBridge(): WebViewBridge {
  return new Proxy({}, {
    get(_target, property, receiver) {
      return Reflect.get(requireWebViewBridge(), property, receiver)
    },
    set(_target, property, value, receiver) {
      return Reflect.set(requireWebViewBridge(), property, value, receiver)
    },
    has(_target, property) {
      return property in requireWebViewBridge()
    },
  }) as WebViewBridge
}

export const webview: WebViewBridge = createLazyWebViewBridge()
export const webView: WebViewBridge = webview
