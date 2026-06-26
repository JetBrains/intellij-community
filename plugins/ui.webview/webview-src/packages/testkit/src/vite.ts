// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import type { Alias, AliasOptions, Plugin, UserConfig } from "vite"
import { mockWebViewBridgeBrowserScript } from "./browserScript.ts"

const testkitSrcDir = dirname(fileURLToPath(import.meta.url))

export interface WebViewMockBridgeViteOptions {
  mock: string
}

export function withWebViewMockBridge(config: UserConfig, options: WebViewMockBridgeViteOptions): UserConfig {
  const mockPath = resolve(options.mock)
  return {
    ...config,
    resolve: {
      ...config.resolve,
      alias: [...testkitPackageAliases(), ...asAliasArray(config.resolve?.alias)],
    },
    plugins: [webViewMockBridgePlugin(mockPath), ...asPluginArray(config.plugins)],
    server: {
      ...config.server,
      fs: {
        ...config.server?.fs,
        allow: [...(config.server?.fs?.allow ?? []), dirname(mockPath), process.cwd()],
      },
    },
  }
}

function webViewMockBridgePlugin(mockPath: string): Plugin {
  const mockUrl = `/@fs/${normalizePath(mockPath)}`
  return {
    name: "intellij-webview-mock-bridge",
    enforce: "pre",
    transformIndexHtml(html) {
      return injectMockEntry(html)
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const path = req.url?.split("?", 1)[0]
        if (path === "/__webview/wvi-bridge.js") {
          res.statusCode = 200
          res.setHeader("Content-Type", "text/javascript; charset=utf-8")
          res.end(mockWebViewBridgeBrowserScript())
          return
        }
        if (path === "/__webview-test/mock-entry.js") {
          res.statusCode = 200
          res.setHeader("Content-Type", "text/javascript; charset=utf-8")
          res.end(`import mock from ${JSON.stringify(mockUrl)};\nwindow.__WVI_MOCK__.apply(mock);\n`)
          return
        }
        next()
      })
    },
  }
}

function injectMockEntry(html: string): string {
  if (html.includes("/__webview-test/mock-entry.js")) {
    return html
  }
  const mockEntryPath = "/__webview-test/" + "mock-entry.js"
  const mockScript = `<script type="module" src="${mockEntryPath}"></script>`
  const transformed = html.replace(
    /<script\s+type=(["'])module\1\s+src=(["'])\.\/src\//i,
    match => `${mockScript}\n${match}`,
  )
  if (transformed !== html) {
    return transformed
  }
  return html.replace(/<\/body>/i, `${mockScript}\n</body>`)
}

function asPluginArray(plugins: UserConfig["plugins"]): Plugin[] {
  if (!plugins) {
    return []
  }
  return Array.isArray(plugins) ? plugins.filter((plugin): plugin is Plugin => Boolean(plugin) && !Array.isArray(plugin)) : [plugins as Plugin]
}

function testkitPackageAliases(): Alias[] {
  return [
    { find: /^@jetbrains\/intellij-webview-testkit$/, replacement: resolve(testkitSrcDir, "index.ts") },
    { find: /^@jetbrains\/intellij-webview-testkit\/vite$/, replacement: resolve(testkitSrcDir, "vite.ts") },
  ]
}

function asAliasArray(alias: AliasOptions | undefined): Alias[] {
  if (!alias) {
    return []
  }
  if (Array.isArray(alias)) {
    return alias.slice()
  }
  return Object.entries(alias).map(([find, replacement]) => ({ find, replacement }))
}

function normalizePath(path: string): string {
  return path.replace(/\\/g, "/")
}
