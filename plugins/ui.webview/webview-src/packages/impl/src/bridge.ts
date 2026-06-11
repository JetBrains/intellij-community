// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { WebViewBridge } from "@jetbrains/intellij-webview"
import { createWebViewBridge } from "./messageBus"

export function installWebViewBridge(): WebViewBridge {
  if (window.__WVI__?.__installed) {
    return window.__WVI__
  }

  const bridge = createWebViewBridge()
  window.__WVI__ = bridge
  return bridge
}
