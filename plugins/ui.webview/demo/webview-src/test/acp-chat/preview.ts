// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { runWebViewMockPreview } from "@jetbrains/intellij-webview-testkit/node"

await runWebViewMockPreview({
  importMetaUrl: import.meta.url,
  viewId: "acp-chat",
  mock: "default",
  open: true,
})
