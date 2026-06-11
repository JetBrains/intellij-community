// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { build } from "vite"
import { dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { defineWebViewBridgeConfig, defineWebViewPlatformFeaturesConfig } from "@jetbrains/intellij-webview/vite"

const webviewSrcDir = dirname(fileURLToPath(import.meta.url))

await build(defineWebViewBridgeConfig({ webviewSrcDir }))
await build(defineWebViewPlatformFeaturesConfig({ webviewSrcDir }))
