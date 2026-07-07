// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { readFileSync, writeFileSync } from "node:fs"
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
  enableDefaultTextSelectionGuard?: boolean
  modulePreload?: boolean
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
    plugins: [
      injectCommonWebViewRuntimeAssetsPlugin(options.enableDefaultTextSelectionGuard !== false),
      stripCrossoriginFromHtmlPlugin(),
      stripEmptyVitePreloadWrappersPlugin(options.modulePreload === false),
    ],
    root: sourceDir,
    base: "./",
    publicDir: false,
    resolve: {
      alias: [
        { find: /^react$/, replacement: resolve(options.webviewSrcDir, "node_modules/react/index.js") },
        { find: /^react\/jsx-runtime$/, replacement: resolve(options.webviewSrcDir, "node_modules/react/jsx-runtime.js") },
        { find: /^react-dom$/, replacement: resolve(options.webviewSrcDir, "node_modules/react-dom/index.js") },
        { find: /^react-dom\/client$/, replacement: resolve(options.webviewSrcDir, "node_modules/react-dom/client.js") },
      ],
      dedupe: ["react", "react-dom"],
    },
    build: {
      outDir,
      emptyOutDir: true,
      copyPublicDir: false,
      // Keep WebView resource URLs stable and inspectable instead of embedding small assets into JS/CSS.
      assetsInlineLimit: 0,
      // Keep each WebView view with one predictable stylesheet. JS chunks may split by package,
      // but CSS is loaded directly from index.html and should stay as styles.css.
      cssCodeSplit: false,
      modulePreload: options.modulePreload,
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
  if (normalizedId.includes("preload-helper")) {
    return "vite-preload-helper"
  }

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

function injectCommonWebViewRuntimeAssetsPlugin(enableDefaultTextSelectionGuard: boolean): Plugin {
  const bridgeUrl = COMMON_WEBVIEW_ASSET_PREFIX + COMMON_WEBVIEW_BRIDGE_ASSET
  const platformFeaturesUrl = COMMON_WEBVIEW_ASSET_PREFIX + COMMON_WEBVIEW_PLATFORM_FEATURES_ASSET
  return {
    name: "intellij-webview-common-runtime-assets",
    transformIndexHtml(html: string): string {
      return injectCommonWebViewRuntimeAssets(html, bridgeUrl, platformFeaturesUrl, enableDefaultTextSelectionGuard)
    },
    generateBundle(_options, bundle) {
      for (const item of Object.values(bundle)) {
        if (item.type !== "asset" || !item.fileName.endsWith(".html") || typeof item.source !== "string") continue

        item.source = injectCommonWebViewRuntimeAssets(item.source, bridgeUrl, platformFeaturesUrl, enableDefaultTextSelectionGuard)
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

function injectCommonWebViewRuntimeAssets(html: string, bridgeUrl: string, platformFeaturesUrl: string, enableDefaultTextSelectionGuard: boolean): string {
  const htmlWithRuntimeAssets = html.includes(bridgeUrl) || html.includes(platformFeaturesUrl)
    ? html
    : html.replace(
      /<head(\s[^>]*)?>/i,
      (head) => `${head}\n  <script src="${bridgeUrl}"></script>\n  <script src="${platformFeaturesUrl}"></script>`,
    )
  return enableDefaultTextSelectionGuard ? htmlWithRuntimeAssets : injectDefaultTextSelectionGuardOptOut(htmlWithRuntimeAssets, platformFeaturesUrl)
}

function injectDefaultTextSelectionGuardOptOut(html: string, platformFeaturesUrl: string): string {
  const metaName = "wvi-enable-default-text-selection-guard"
  if (html.includes(`name="${metaName}"`) || html.includes(`name='${metaName}'`)) {
    return html
  }
  return html.replace(
    new RegExp(`(<script src="${escapeRegExp(platformFeaturesUrl)}"></script>)`, "i"),
    `<meta name="${metaName}" content="false">\n  $1`,
  )
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
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

function stripEmptyVitePreloadWrappersPlugin(enabled: boolean): Plugin | null {
  if (!enabled) return null

  return {
    name: "intellij-webview-strip-empty-vite-preload-wrappers",
    enforce: "post",
    renderChunk: {
      order: "post",
      handler(code) {
        const transformedCode = stripEmptyVitePreloadWrappers(code)
        return transformedCode === code ? null : { code: transformedCode, map: null }
      },
    },
    generateBundle(_options, bundle) {
      for (const item of Object.values(bundle)) {
        if (item.type !== "chunk") continue

        item.code = stripEmptyVitePreloadWrappers(item.code)
      }
    },
    writeBundle(outputOptions, bundle) {
      const outDir = outputOptions.dir ?? (outputOptions.file == null ? undefined : dirname(outputOptions.file))
      if (outDir == null) return

      for (const item of Object.values(bundle)) {
        if (item.type !== "chunk") continue

        const file = resolve(outDir, item.fileName)
        const code = readFileSync(file, "utf8")
        const transformedCode = stripEmptyVitePreloadWrappers(code)
        if (transformedCode !== code) {
          writeFileSync(file, transformedCode)
        }
      }
    },
  }
}

function stripEmptyVitePreloadWrappers(code: string): string {
  const preloadCall = "__vitePreload("
  let result = ""
  let offset = 0

  while (offset < code.length) {
    const callStart = code.indexOf(preloadCall, offset)
    if (callStart < 0) {
      result += code.slice(offset)
      break
    }

    const openParenIndex = callStart + "__vitePreload".length
    const closeParenIndex = findClosingParen(code, openParenIndex)
    if (closeParenIndex == null) {
      result += code.slice(offset)
      break
    }

    const replacement = unwrapEmptyVitePreloadCall(code.slice(openParenIndex + 1, closeParenIndex))
    result += code.slice(offset, callStart)
    result += replacement ?? code.slice(callStart, closeParenIndex + 1)
    offset = closeParenIndex + 1
  }

  if (!/__vitePreload\s*\(/.test(result)) {
    result = result.replace(/^import\s+\{[^}]*\bas\s+__vitePreload[^}]*}\s+from\s+"[^"]+";\r?\n/gm, "")
  }
  return result
}

function unwrapEmptyVitePreloadCall(argumentsSource: string): string | null {
  const args = splitTopLevelArguments(argumentsSource)
  if (args.length !== 3 || args[1]?.trim() !== "[]" || args[2]?.trim() !== "import.meta.url") {
    return null
  }

  return unwrapVitePreloadBaseModule(args[0]?.trim() ?? "")
}

function unwrapVitePreloadBaseModule(baseModule: string): string | null {
  const asyncPrefix = "async () => "
  if (baseModule.startsWith(asyncPrefix)) {
    const body = baseModule.slice(asyncPrefix.length).trim()
    return body.startsWith("{") ? `(${baseModule})()` : body
  }

  const syncPrefix = "() => "
  if (baseModule.startsWith(syncPrefix)) {
    const body = baseModule.slice(syncPrefix.length).trim()
    return body.startsWith("{") ? `(${baseModule})()` : body
  }

  return null
}

function findClosingParen(source: string, openParenIndex: number): number | null {
  let depth = 0
  let state: JavaScriptScanState = "code"

  for (let index = openParenIndex; index < source.length; index++) {
    const char = source[index]
    const nextChar = source[index + 1]

    if (state === "lineComment") {
      if (char === "\n" || char === "\r") state = "code"
      continue
    }
    if (state === "blockComment") {
      if (char === "*" && nextChar === "/") {
        state = "code"
        index++
      }
      continue
    }
    if (state === "singleQuotedString") {
      if (char === "\\") index++
      else if (char === "'") state = "code"
      continue
    }
    if (state === "doubleQuotedString") {
      if (char === "\\") index++
      else if (char === "\"") state = "code"
      continue
    }
    if (state === "templateString") {
      if (char === "\\") index++
      else if (char === "`") state = "code"
      continue
    }

    if (char === "/" && nextChar === "/") {
      state = "lineComment"
      index++
      continue
    }
    if (char === "/" && nextChar === "*") {
      state = "blockComment"
      index++
      continue
    }
    if (char === "'") {
      state = "singleQuotedString"
      continue
    }
    if (char === "\"") {
      state = "doubleQuotedString"
      continue
    }
    if (char === "`") {
      state = "templateString"
      continue
    }

    if (char === "(") {
      depth++
    }
    else if (char === ")") {
      depth--
      if (depth === 0) return index
    }
  }

  return null
}

function splitTopLevelArguments(source: string): string[] {
  const args: string[] = []
  let argStart = 0
  let parenDepth = 0
  let braceDepth = 0
  let bracketDepth = 0
  let state: JavaScriptScanState = "code"

  for (let index = 0; index < source.length; index++) {
    const char = source[index]
    const nextChar = source[index + 1]

    if (state === "lineComment") {
      if (char === "\n" || char === "\r") state = "code"
      continue
    }
    if (state === "blockComment") {
      if (char === "*" && nextChar === "/") {
        state = "code"
        index++
      }
      continue
    }
    if (state === "singleQuotedString") {
      if (char === "\\") index++
      else if (char === "'") state = "code"
      continue
    }
    if (state === "doubleQuotedString") {
      if (char === "\\") index++
      else if (char === "\"") state = "code"
      continue
    }
    if (state === "templateString") {
      if (char === "\\") index++
      else if (char === "`") state = "code"
      continue
    }

    if (char === "/" && nextChar === "/") {
      state = "lineComment"
      index++
      continue
    }
    if (char === "/" && nextChar === "*") {
      state = "blockComment"
      index++
      continue
    }
    if (char === "'") {
      state = "singleQuotedString"
      continue
    }
    if (char === "\"") {
      state = "doubleQuotedString"
      continue
    }
    if (char === "`") {
      state = "templateString"
      continue
    }

    if (char === "(") parenDepth++
    else if (char === ")") parenDepth--
    else if (char === "{") braceDepth++
    else if (char === "}") braceDepth--
    else if (char === "[") bracketDepth++
    else if (char === "]") bracketDepth--
    else if (char === "," && parenDepth === 0 && braceDepth === 0 && bracketDepth === 0) {
      args.push(source.slice(argStart, index))
      argStart = index + 1
    }
  }

  args.push(source.slice(argStart))
  return args
}

type JavaScriptScanState = "code" | "lineComment" | "blockComment" | "singleQuotedString" | "doubleQuotedString" | "templateString"

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
