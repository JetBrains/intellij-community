// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { existsSync, readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { defineWebViewViewConfig } from "@jetbrains/intellij-webview/vite"
import { withWebViewMockBridge } from "@jetbrains/intellij-webview-testkit/vite"

const webviewSrcDir = dirname(fileURLToPath(import.meta.url))
const iconResourceRoot = resolve(webviewSrcDir, "../../../../platform/icons/src")

const viewConfig = defineWebViewViewConfig({ webviewSrcDir, id: "markdown-preview" })

export default withWebViewMockBridge({
  ...viewConfig,
  plugins: [serveAllIconsPlugin(), serveMarkdownPreviewResourcesPlugin(), ...asPluginArray(viewConfig.plugins)],
} as Parameters<typeof withWebViewMockBridge>[0], {
  mock: resolve(webviewSrcDir, "views/markdown-preview/src/markdownPreviewMock.ts"),
})

const mockMarkdownPreviewImageSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="480" viewBox="0 0 960 480">
  <rect width="960" height="480" fill="#f7f8fa"/>
  <rect x="72" y="72" width="816" height="336" rx="24" fill="#ffffff" stroke="#3871e1" stroke-width="12"/>
  <path d="M144 384l168-168 120 120 96-96 288 228H144z" fill="#6db083"/>
  <circle cx="690" cy="174" r="72" fill="#f4b400"/>
  <text x="480" y="104" text-anchor="middle" fill="#1f2328" font-family="system-ui, sans-serif" font-size="40" font-weight="700">Markdown Preview Image</text>
</svg>`

interface VitePluginLike {
  name: string
  configureServer?(server: ViteServerLike): void
}

interface ViteServerLike {
  middlewares: {
    use(handler: (req: { url?: string }, res: ViteResponseLike, next: () => void) => void): void
  }
}

interface ViteResponseLike {
  statusCode: number
  setHeader(name: string, value: string): void
  end(body?: string | Buffer): void
}

function serveAllIconsPlugin(): VitePluginLike {
  return {
    name: "markdown-preview-mock-all-icons",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const requestPath = req.url?.split("?", 1)[0]
        if (requestPath === "/favicon.ico") {
          res.statusCode = 204
          res.end()
          return
        }

        const match = requestPath?.match(/^\/__ij-icons\/AllIcons\/(light|dark)\/(.+)$/)
        if (!match) {
          next()
          return
        }

        const [, flavor, encodedResourcePath] = match
        const resourcePath = decodeIconPath(encodedResourcePath)
        if (!resourcePath) {
          res.statusCode = 403
          res.end("Invalid icon path")
          return
        }

        const actualPath = flavor === "dark" ? darkIconPath(resourcePath) ?? resourcePath : resourcePath
        const iconPath = resolve(iconResourceRoot, actualPath)
        if (!iconPath.startsWith(iconResourceRoot) || !existsSync(iconPath)) {
          res.statusCode = 404
          res.end("Icon not found")
          return
        }

        res.statusCode = 200
        res.setHeader("Content-Type", actualPath.endsWith(".svg") ? "image/svg+xml" : "image/png")
        res.setHeader("Cache-Control", "no-cache")
        res.end(readFileSync(iconPath))
      })
    },
  }
}

function serveMarkdownPreviewResourcesPlugin(): VitePluginLike {
  return {
    name: "markdown-preview-mock-resources",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const requestPath = req.url?.split("?", 1)[0]
        if (!requestPath?.includes("/__markdown-preview-resource/")) {
          next()
          return
        }

        res.statusCode = 200
        res.setHeader("Content-Type", "image/svg+xml")
        res.setHeader("Cache-Control", "no-cache")
        res.end(mockMarkdownPreviewImageSvg)
      })
    },
  }
}

function asPluginArray(plugins: unknown): unknown[] {
  if (!plugins) return []
  return Array.isArray(plugins) ? plugins.filter(plugin => Boolean(plugin) && !Array.isArray(plugin)) : [plugins]
}

function decodeIconPath(encodedPath: string): string | undefined {
  const resourcePath = encodedPath.split("/").map(segment => decodeURIComponent(segment)).join("/")
  if (!resourcePath.endsWith(".svg") && !resourcePath.endsWith(".png")) return undefined
  if (resourcePath.startsWith("/") || resourcePath.split("/").some(segment => !segment || segment === "." || segment === "..")) return undefined
  return resourcePath
}

function darkIconPath(resourcePath: string): string | undefined {
  const extensionStart = resourcePath.lastIndexOf(".")
  const candidate = `${resourcePath.substring(0, extensionStart)}_dark${resourcePath.substring(extensionStart)}`
  return existsSync(resolve(iconResourceRoot, candidate)) ? candidate : undefined
}
