// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { spawn } from "node:child_process"
import { existsSync } from "node:fs"
import { dirname, isAbsolute, resolve } from "node:path"
import process from "node:process"
import { fileURLToPath } from "node:url"
import type { StartWebViewMockPreviewOptions, WebViewMockPreviewServer } from "./core.ts"
import { startWebViewMockPreview } from "./preview.ts"

export interface RunWebViewMockPreviewOptions {
  importMetaUrl?: string
  webviewSrcDir?: string
  viewId: string
  mock?: string
  port?: number
  open?: boolean
}

export interface ResolvedWebViewMockPreviewOptions extends StartWebViewMockPreviewOptions {
  open: boolean
}

export interface WebViewMockPreviewSignalProcess {
  once(signal: NodeJS.Signals, listener: () => void): unknown
  off(signal: NodeJS.Signals, listener: () => void): unknown
  exit(code?: number): unknown
}

const previewSignals: NodeJS.Signals[] = ["SIGINT", "SIGTERM"]
const explicitMockPathPattern = /(?:[/\\]|\.[cm]?[jt]sx?)$/

export async function runWebViewMockPreview(options: RunWebViewMockPreviewOptions): Promise<WebViewMockPreviewServer> {
  const resolvedOptions = resolveWebViewMockPreviewOptions(options)
  const previewServer = await startWebViewMockPreview(resolvedOptions)
  let disposeSignalHandlers = () => {}
  let closePromise: Promise<void> | undefined
  const server: WebViewMockPreviewServer = {
    url: previewServer.url,
    async close() {
      disposeSignalHandlers()
      closePromise ??= previewServer.close()
      await closePromise
    },
  }
  disposeSignalHandlers = installWebViewMockPreviewSignalHandlers(server)
  console.log(`WebView mock preview: ${server.url}`)
  if (resolvedOptions.open) {
    openBrowser(server.url)
  }
  return server
}

export function resolveWebViewMockPreviewOptions(options: RunWebViewMockPreviewOptions): ResolvedWebViewMockPreviewOptions {
  const webviewSrcDir = options.webviewSrcDir
    ? resolve(options.webviewSrcDir)
    : resolveWebViewSrcDir(options.importMetaUrl)
  return {
    webviewSrcDir,
    viewId: options.viewId,
    mock: resolveWebViewMockPath(webviewSrcDir, options.viewId, options.mock ?? "default"),
    port: options.port,
    open: options.open ?? false,
  }
}

export function resolveWebViewMockPath(webviewSrcDir: string, viewId: string, mock: string): string {
  if (isExplicitMockPath(mock)) {
    return isAbsolute(mock) ? mock : resolve(webviewSrcDir, mock)
  }
  return resolve(webviewSrcDir, "test", viewId, "mocks", `${mock}.ts`)
}

export function resolveWebViewSrcDir(importMetaUrl: string | undefined): string {
  if (!importMetaUrl) {
    throw new Error("runWebViewMockPreview requires importMetaUrl or webviewSrcDir")
  }
  let current = dirname(fileURLToPath(importMetaUrl))
  while (true) {
    if (existsSync(resolve(current, "package.json"))) {
      return current
    }
    const parent = dirname(current)
    if (parent === current) {
      throw new Error("Cannot find webview-src package.json for " + importMetaUrl)
    }
    current = parent
  }
}

export function installWebViewMockPreviewSignalHandlers(
  server: WebViewMockPreviewServer,
  signalProcess: WebViewMockPreviewSignalProcess = process,
): () => void {
  let closing = false
  const closeAndExit = () => {
    if (closing) {
      return
    }
    closing = true
    void server.close().finally(() => {
      signalProcess.exit(0)
    })
  }
  for (const signal of previewSignals) {
    signalProcess.once(signal, closeAndExit)
  }
  return () => {
    for (const signal of previewSignals) {
      signalProcess.off(signal, closeAndExit)
    }
  }
}

function isExplicitMockPath(mock: string): boolean {
  return explicitMockPathPattern.test(mock)
}

function openBrowser(url: string): void {
  const command = process.platform === "darwin" ? "open" : process.platform === "win32" ? "cmd" : "xdg-open"
  const args = process.platform === "win32" ? ["/c", "start", "", url] : [url]
  const child = spawn(command, args, { detached: true, stdio: "ignore" })
  child.on("error", () => {})
  child.unref()
}
