#!/usr/bin/env bun
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { runWebViewMockPreview } from "./node"

interface CliOptions {
  viewId: string
  mock: string
  port?: number
}

const options = parseArgs(process.argv.slice(2))
await runPreviewOrExit(options)

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

async function runPreviewOrExit(options: CliOptions): Promise<void> {
  try {
    await runWebViewMockPreview({
      importMetaUrl: import.meta.url,
      webviewSrcDir: process.cwd(),
      viewId: options.viewId,
      mock: options.mock,
      port: options.port,
    })
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

function usage(): never {
  console.error("Usage: bun webview-preview <view-id> [--mock <name-or-path>] [--port <port>]")
  process.exit(2)
}
