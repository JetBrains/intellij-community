#!/usr/bin/env bun
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { resolve } from "node:path"
import { startWebViewMockPreview } from "./preview"

interface CliOptions {
  viewId: string
  mock: string
  port?: number
}

const options = parseArgs(process.argv.slice(2))
const webviewSrcDir = process.cwd()
const mock = resolveMockPath(webviewSrcDir, options.viewId, options.mock)
const server = await startPreviewOrExit({
  webviewSrcDir,
  viewId: options.viewId,
  mock,
  port: options.port,
})

console.log(`WebView mock preview: ${server.url}`)

process.on("SIGINT", () => {
  void server.close().finally(() => process.exit(0))
})
process.on("SIGTERM", () => {
  void server.close().finally(() => process.exit(0))
})

function parseArgs(args: string[]): CliOptions {
  const viewId = args[0]
  if (!viewId || viewId.startsWith("-")) {
    usage()
  }
  let mock = "default"
  let port: number | undefined
  for (let i = 1; i < args.length; i++) {
    const arg = args[i]
    if (arg === "--mock") {
      mock = requiredValue(args, ++i, "--mock")
    }
    else if (arg === "--port") {
      port = Number(requiredValue(args, ++i, "--port"))
      if (!Number.isInteger(port) || port <= 0) {
        throw new Error("--port must be a positive integer")
      }
    }
    else {
      throw new Error("Unknown webview-preview argument: " + arg)
    }
  }
  return { viewId, mock, port }
}

async function startPreviewOrExit(options: Parameters<typeof startWebViewMockPreview>[0]) {
  try {
    return await startWebViewMockPreview(options)
  }
  catch (error) {
    console.error(error instanceof Error ? error.message : String(error))
    process.exit(1)
  }
}

function requiredValue(args: string[], index: number, option: string): string {
  const value = args[index]
  if (!value || value.startsWith("-")) {
    throw new Error(option + " requires a value")
  }
  return value
}

function resolveMockPath(webviewSrcDir: string, viewId: string, mock: string): string {
  if (mock.includes("/") || mock.includes("\\") || mock.endsWith(".ts") || mock.endsWith(".tsx") || mock.endsWith(".mjs") || mock.endsWith(".js")) {
    return resolve(webviewSrcDir, mock)
  }
  return resolve(webviewSrcDir, "test", viewId, "mocks", `${mock}.ts`)
}

function usage(): never {
  console.error("Usage: bun webview-preview <view-id> [--mock <name-or-path>] [--port <port>]")
  process.exit(2)
}
