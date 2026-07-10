// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { build } from "vite"
import { defineWebViewViewConfigs, selectWebViewViewBuildEntries, withWebViewBuildWatch } from "@jetbrains/intellij-webview/vite"

const webviewSrcDir = dirname(fileURLToPath(import.meta.url))
const selectedViews = selectWebViewViewBuildEntries([{ id: "markdown-preview", modulePreload: false }])

for (const config of defineWebViewViewConfigs({ webviewSrcDir, views: selectedViews.views })) {
  await build(withWebViewBuildWatch(config, selectedViews.watch) as unknown as Parameters<typeof build>[0])
}
