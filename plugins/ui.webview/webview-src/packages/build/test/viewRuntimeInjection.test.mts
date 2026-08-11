// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/// <reference path="../../impl/test/bun-test.d.ts" />

import { describe, expect, test } from "bun:test"
import { defineWebViewViewConfig } from "../src/index.ts"

const VIEW_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
</head>
<body class="ij-webview-root">
  <main id="root"></main>
  <script type="module" src="./src/main.ts"></script>
</body>
</html>`

describe("WebView view runtime injection", () => {
  test("does not add a text selection opt-out meta tag by default", () => {
    const html = transformIndexHtml()

    expect(html.includes("wvi-enable-default-text-selection-guard")).toBe(false)
    expect(html.indexOf('/__webview/wvi-bridge.js') < html.indexOf('/__webview/wvi-platform-features.js')).toBe(true)
    expect(html.indexOf('/__webview/wvi-platform-features.js') < html.indexOf('./src/main.ts')).toBe(true)
  })

  test("adds text selection opt-out meta tag before platform features when disabled", () => {
    const html = transformIndexHtml(false)
    const bridgeIndex = html.indexOf('/__webview/wvi-bridge.js')
    const metaIndex = html.indexOf('name="wvi-enable-default-text-selection-guard" content="false"')
    const platformFeaturesIndex = html.indexOf('/__webview/wvi-platform-features.js')
    const viewIndex = html.indexOf('./src/main.ts')

    expect(bridgeIndex >= 0).toBe(true)
    expect(metaIndex >= 0).toBe(true)
    expect(platformFeaturesIndex >= 0).toBe(true)
    expect(bridgeIndex < metaIndex).toBe(true)
    expect(metaIndex < platformFeaturesIndex).toBe(true)
    expect(platformFeaturesIndex < viewIndex).toBe(true)
  })
})

function transformIndexHtml(enableDefaultTextSelectionGuard?: boolean): string {
  const config = defineWebViewViewConfig({
    id: "sample",
    webviewSrcDir: "/tmp/webview-src",
    ...(enableDefaultTextSelectionGuard == null ? {} : { enableDefaultTextSelectionGuard }),
  })
  const plugin = (config.plugins as Array<{ name: string, transformIndexHtml?: (html: string) => string }>).find(item => item.name === "intellij-webview-common-runtime-assets")
  if (!plugin?.transformIndexHtml) {
    throw new Error("Runtime injection plugin is missing")
  }
  return plugin.transformIndexHtml(VIEW_HTML)
}
