// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { WebViewTransport } from "@jetbrains/intellij-webview"

let browserZoomGuardInstalled = false

export function installWebViewBrowserZoomGuard(transport: WebViewTransport): void {
  if (transport !== "webview2") {
    return
  }
  if (browserZoomGuardInstalled) {
    return
  }
  browserZoomGuardInstalled = true

  window.addEventListener("wheel", handleBrowserZoomWheel, { passive: false })
}

function handleBrowserZoomWheel(event: WheelEvent): void {
  if (event.ctrlKey && event.cancelable) {
    event.preventDefault()
  }
}
