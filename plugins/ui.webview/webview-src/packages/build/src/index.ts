// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import type { Plugin, UserConfig } from "vite"

const COMMON_WEBVIEW_ASSET_PREFIX = "/__webview/"
const COMMON_WEBVIEW_BRIDGE_ASSET = "wvi-bridge.js"
const COMMON_WEBVIEW_PLATFORM_FEATURES_ASSET = "wvi-platform-features.js"
const webViewCommonAssetsDir = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../resources/webview")

export interface WebViewViewEntry {
  id: string
  sourceDir?: string
  outDir?: string
}

export interface WebViewViewConfigOptions extends WebViewViewEntry {
  webviewSrcDir: string
}

export interface WebViewViewsConfigOptions {
  webviewSrcDir: string
  views: ReadonlyArray<string | WebViewViewEntry>
}

export interface WebViewViewBuildSelection {
  views: ReadonlyArray<string | WebViewViewEntry>
  watch: boolean
}

export interface WebViewBridgeConfigOptions {
  webviewSrcDir: string
  entry?: string
  outDir?: string
  fileName?: string
}

export interface WebViewPlatformFeaturesConfigOptions {
  webviewSrcDir: string
  entry?: string
  outDir?: string
  fileName?: string
}

export function defineWebViewViewConfigs(options: WebViewViewsConfigOptions): UserConfig[] {
  return options.views.map((view) => {
    const entry = typeof view === "string" ? { id: view } : view
    return defineWebViewViewConfig({ ...entry, webviewSrcDir: options.webviewSrcDir })
  })
}

export function selectWebViewViewBuildEntries(views: ReadonlyArray<string | WebViewViewEntry>, args: ReadonlyArray<string> = process.argv.slice(2)): WebViewViewBuildSelection {
  const watch = args.includes("--watch") || args.includes("-w")
  const requestedIds = args.filter((arg) => arg !== "--" && arg !== "--watch" && arg !== "-w")
  if (requestedIds.length === 0) {
    return { views, watch }
  }

  const entriesById = new Map(views.map((view) => [webViewViewEntryId(view), view]))
  const unknownIds = requestedIds.filter((id) => !entriesById.has(id))
  if (unknownIds.length > 0) {
    throw new Error(`Unknown WebView view: ${unknownIds.join(", ")}. Available views: ${Array.from(entriesById.keys()).join(", ")}`)
  }

  return { views: requestedIds.map((id) => entriesById.get(id)!), watch }
}

export function withWebViewBuildWatch(config: UserConfig, watch: boolean): UserConfig {
  if (!watch) {
    return config
  }

  return {
    ...config,
    build: {
      ...config.build,
      watch: {},
    },
  }
}

function webViewViewEntryId(view: string | WebViewViewEntry): string {
  return typeof view === "string" ? view : view.id
}

/**
 * Builds one WebView view from webview-src/views/<view-id> into
 * resources/webview/views/<view-id>. The generated output is meant to be
 * committed until WebView frontend sources are wired into the main build graph.
 *
 * Stable output contract:
 * - local view entry: view.js;
 * - stylesheet: styles.css;
 * - node_modules chunks: assets/<package-name>.js;
 * - fonts/images/other assets: assets/<original-name>.
 *
 * Common WebView runtime scripts are injected into index.html by this helper.
 * See community/plugins/ui.webview/docs/frontend/WebView-Frontend-Build-Strategy.md#current-view-build-pipeline
 * for the full build pipeline and commit policy.
 */
export function defineWebViewViewConfig(options: WebViewViewConfigOptions): UserConfig {
  const sourceDir = options.sourceDir ?? resolve(options.webviewSrcDir, "views", options.id)
  const outDir = options.outDir ?? resolve(options.webviewSrcDir, "..", "resources", "webview", "views", options.id)

  return {
    plugins: [injectCommonWebViewRuntimeAssetsPlugin(), stripCrossoriginFromHtmlPlugin()],
    root: sourceDir,
    base: "./",
    publicDir: false,
    build: {
      outDir,
      emptyOutDir: true,
      copyPublicDir: false,
      // Keep WebView resource URLs stable and inspectable instead of embedding small assets into JS/CSS.
      assetsInlineLimit: 0,
      // Keep each WebView view with one predictable stylesheet. JS chunks may split by package,
      // but CSS is loaded directly from index.html and should stay as styles.css.
      cssCodeSplit: false,
      minify: false,
      sourcemap: false,
      target: "es2022",
      rollupOptions: {
        output: {
          entryFileNames: "view.js",
          chunkFileNames: "assets/[name].js",
          // Dependency chunks use package names instead of Rollup/Vite hash-like names, for example
          // assets/react.js, assets/react-dom.js, assets/mermaid.js, assets/jetbrains-intellij-webview.js.
          manualChunks: webViewManualChunkName,
          assetFileNames(assetInfo) {
            const originalName = assetInfo.names?.[0] ?? assetInfo.name ?? ""
            return originalName.endsWith(".css") ? "styles.css" : "assets/[name][extname]"
          },
        },
      },
    },
  }
}

function webViewManualChunkName(id: string): string | undefined {
  // Local view source stays in view.js. Dependencies are grouped per npm package so the generated
  // output is commit-friendly and still avoids one huge vendor bundle.
  const normalizedId = id.replace(/\\/g, "/")
  const marker = "/node_modules/"
  const markerIndex = normalizedId.lastIndexOf(marker)
  if (markerIndex < 0) {
    return undefined
  }

  const packagePath = normalizedId.substring(markerIndex + marker.length)
  const packagePathParts = packagePath.split("/")
  const firstPart = packagePathParts[0]
  if (firstPart == null || firstPart.length === 0) {
    return undefined
  }

  const packageName = firstPart.startsWith("@") && packagePathParts[1] != null ? `${firstPart.substring(1)}-${packagePathParts[1]}` : firstPart
  return packageName.replace(/[^A-Za-z0-9_-]+/g, "-")
}

function injectCommonWebViewRuntimeAssetsPlugin(): Plugin {
  const bridgeUrl = COMMON_WEBVIEW_ASSET_PREFIX + COMMON_WEBVIEW_BRIDGE_ASSET
  const platformFeaturesUrl = COMMON_WEBVIEW_ASSET_PREFIX + COMMON_WEBVIEW_PLATFORM_FEATURES_ASSET
  return {
    name: "intellij-webview-common-runtime-assets",
    transformIndexHtml(html: string): string {
      return injectCommonWebViewRuntimeAssets(html, bridgeUrl, platformFeaturesUrl)
    },
    generateBundle(_options, bundle) {
      for (const item of Object.values(bundle)) {
        if (item.type !== "asset" || !item.fileName.endsWith(".html") || typeof item.source !== "string") continue

        item.source = injectCommonWebViewRuntimeAssets(item.source, bridgeUrl, platformFeaturesUrl)
      }
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const assetName = commonWebViewRuntimeAssetName(req.url)
        if (assetName == null) {
          next()
          return
        }
        try {
          res.statusCode = 200
          res.setHeader("Content-Type", "text/javascript; charset=utf-8")
          res.end(readFileSync(resolve(webViewCommonAssetsDir, assetName)))
        }
        catch {
          next()
        }
      })
    },
  }
}

function injectCommonWebViewRuntimeAssets(html: string, bridgeUrl: string, platformFeaturesUrl: string): string {
  if (html.includes(bridgeUrl) || html.includes(platformFeaturesUrl)) {
    return html
  }
  return html.replace(
    /<head(\s[^>]*)?>/i,
    (head) => `${head}\n  <script src="${bridgeUrl}"></script>\n  <script src="${platformFeaturesUrl}"></script>`,
  )
}

function commonWebViewRuntimeAssetName(url: string | undefined): string | null {
  const path = url?.split("?", 1)[0]
  return whenCommonWebViewRuntimeAsset(path, COMMON_WEBVIEW_BRIDGE_ASSET) ??
         whenCommonWebViewRuntimeAsset(path, COMMON_WEBVIEW_PLATFORM_FEATURES_ASSET)
}

function whenCommonWebViewRuntimeAsset(path: string | undefined, assetName: string): string | null {
  return path === COMMON_WEBVIEW_ASSET_PREFIX + assetName ? assetName : null
}

function stripCrossoriginFromHtmlPlugin() {
  return {
    name: "intellij-webview-strip-crossorigin",
    enforce: "post" as const,
    transformIndexHtml(html: string): string {
      return html.replace(/\s+crossorigin(?:=(?:"[^"]*"|'[^']*'|[^\s>]+))?(?=[\s>])/g, "")
    },
  }
}

export function defineWebViewBridgeConfig(options: WebViewBridgeConfigOptions): UserConfig {
  const entry = options.entry ?? resolve(options.webviewSrcDir, "packages", "impl", "src", "entry.ts")
  const outDir = options.outDir ?? resolve(options.webviewSrcDir, "..", "resources", "webview")

  return {
    root: options.webviewSrcDir,
    publicDir: false,
    build: {
      outDir,
      emptyOutDir: false,
      copyPublicDir: false,
      minify: false,
      sourcemap: false,
      target: "es2020",
      rollupOptions: {
        input: entry,
        output: {
          format: "iife",
          entryFileNames: options.fileName ?? "wvi-bridge.js",
        },
      },
    },
  }
}

export function defineWebViewPlatformFeaturesConfig(options: WebViewPlatformFeaturesConfigOptions): UserConfig {
  const entry = options.entry ?? resolve(options.webviewSrcDir, "packages", "impl", "src", "platformFeaturesEntry.ts")
  const outDir = options.outDir ?? resolve(options.webviewSrcDir, "..", "resources", "webview")

  return {
    root: options.webviewSrcDir,
    publicDir: false,
    build: {
      outDir,
      emptyOutDir: false,
      copyPublicDir: false,
      minify: false,
      sourcemap: false,
      target: "es2020",
      rollupOptions: {
        input: entry,
        output: {
          format: "iife",
          entryFileNames: options.fileName ?? "wvi-platform-features.js",
        },
      },
    },
  }
}
