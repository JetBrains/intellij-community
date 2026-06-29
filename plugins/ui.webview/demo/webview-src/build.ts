// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { build } from "vite"
import { defineWebViewViewConfigs, selectWebViewViewBuildEntries, withWebViewBuildWatch } from "@jetbrains/intellij-webview/vite"

const webviewSrcDir = dirname(fileURLToPath(import.meta.url))
const selectedViews = selectWebViewViewBuildEntries(["sample-panel", "controls-showcase", "markdown-link-graph", "acp-chat"])
const DEFAULT_ASSETS_INLINE_LIMIT_BYTES = 4096

for (const config of defineWebViewViewConfigs({ webviewSrcDir, views: selectedViews.views })) {
  await build(withWebViewBuildWatch(withViewBuildOverrides(config), selectedViews.watch) as unknown as Parameters<typeof build>[0])
}

function withViewBuildOverrides(config: ReturnType<typeof defineWebViewViewConfigs>[number]): ReturnType<typeof defineWebViewViewConfigs>[number] {
  if (typeof config.root !== "string" || !config.root.endsWith("/views/acp-chat")) return config
  return {
    ...config,
    build: {
      ...config.build,
      assetsInlineLimit(filePath, content) {
        return isAcpChatIconAsset(filePath) ? false : content.length < DEFAULT_ASSETS_INLINE_LIMIT_BYTES
      },
    },
  }
}

function isAcpChatIconAsset(filePath: string): boolean {
  return filePath.replace(/\\/g, "/").includes("/views/acp-chat/src/icons/acpChat")
}
