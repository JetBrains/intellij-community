// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { WebViewTransport } from "@jetbrains/intellij-webview"
import type { JsonRpcFrame } from "./jsonRpc"

interface WebViewIpcHandler {
  postMessage(message: string): void
}

interface WebKitMessageHandlers {
  webviewIpc?: WebViewIpcHandler
}

interface WebViewWindow extends Window {
  chrome?: {
    webview?: {
      postMessage(message: string): void
    }
  }
  webkit?: {
    messageHandlers?: WebKitMessageHandlers
  }
  __wviJcefQuery?: (request: { request: string; onFailure(code: number, message: string): void }) => void
}

const HANDLER_CHANNEL = "webviewIpc"
const webViewWindow = window as WebViewWindow

function webkitMessageHandler(): WebViewIpcHandler | undefined {
  return webViewWindow.webkit?.messageHandlers?.[HANDLER_CHANNEL]
}

export function detectTransport(): WebViewTransport {
  if (webViewWindow.chrome?.webview) {
    return "webview2"
  }

  if (webkitMessageHandler()) {
    return "webkit"
  }

  if (typeof webViewWindow.__wviJcefQuery === "function") {
    return "jcef"
  }

  return "missing"
}

export function sendToHost(frame: JsonRpcFrame): boolean {
  try {
    const raw = JSON.stringify(frame)
    if (webViewWindow.chrome?.webview) {
      webViewWindow.chrome.webview.postMessage(raw)
      return true
    }

    const webkitHandler = webkitMessageHandler()
    if (webkitHandler) {
      webkitHandler.postMessage(raw)
      return true
    }

    if (typeof webViewWindow.__wviJcefQuery === "function") {
      webViewWindow.__wviJcefQuery({
        request: raw,
        onFailure(code, message) {
          console.warn("[__WVI__] JCEF query failed:", code, message)
        },
      })
      return true
    }

    console.warn("[__WVI__] native host bridge unavailable; dropping", frame)
    return false
  }
  catch (err) {
    console.error("[__WVI__] postMessage failed:", err)
    return false
  }
}
