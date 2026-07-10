// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { expect, test } from "bun:test"
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, resolve } from "node:path"
import { pathToFileURL } from "node:url"
import type { WebViewMockPreviewServer } from "./core"
import {
  installWebViewMockPreviewSignalHandlers,
  resolveWebViewMockPath,
  resolveWebViewMockPreviewOptions,
} from "./node"

const tempDirs: string[] = []

test("infers webviewSrcDir from a nested preview module", () => {
  try {
    const webviewSrcDir = createTempWebViewSrcDir()
    const previewPath = join(webviewSrcDir, "test", "acp-chat", "preview.ts")
    writeFileSync(previewPath, "")

    const options = resolveWebViewMockPreviewOptions({
      importMetaUrl: pathToFileURL(previewPath).href,
      viewId: "acp-chat",
    })

    expect(options.webviewSrcDir).toBe(webviewSrcDir)
    expect(options.mock).toBe(join(webviewSrcDir, "test", "acp-chat", "mocks", "default.ts"))
    expect(options.open).toBe(false)
  }
  finally {
    cleanupTempDirs()
  }
})

test("resolves mock names and explicit mock paths", () => {
  try {
    const webviewSrcDir = createTempWebViewSrcDir()
    const explicitRelativeMock = "test/acp-chat/mocks/error.ts"
    const explicitAbsoluteMock = resolve(webviewSrcDir, "test", "acp-chat", "mocks", "empty.ts")

    expect(resolveWebViewMockPath(webviewSrcDir, "acp-chat", "default"))
      .toBe(join(webviewSrcDir, "test", "acp-chat", "mocks", "default.ts"))
    expect(resolveWebViewMockPath(webviewSrcDir, "acp-chat", explicitRelativeMock))
      .toBe(resolve(webviewSrcDir, explicitRelativeMock))
    expect(resolveWebViewMockPath(webviewSrcDir, "acp-chat", explicitAbsoluteMock))
      .toBe(explicitAbsoluteMock)
  }
  finally {
    cleanupTempDirs()
  }
})

test("signal cleanup closes the preview server once", async () => {
  const listeners = new Map<NodeJS.Signals, () => void>()
  let closeCount = 0
  let exitCode: number | undefined
  const server: WebViewMockPreviewServer = {
    url: "http://127.0.0.1:12345/",
    async close() {
      closeCount++
    },
  }
  const signalProcess = {
    once(signal: NodeJS.Signals, listener: () => void) {
      listeners.set(signal, listener)
    },
    off(signal: NodeJS.Signals, listener: () => void) {
      if (listeners.get(signal) === listener) {
        listeners.delete(signal)
      }
    },
    exit(code?: number) {
      exitCode = code
    },
  }

  const dispose = installWebViewMockPreviewSignalHandlers(server, signalProcess)
  listeners.get("SIGTERM")?.()
  listeners.get("SIGINT")?.()
  await Promise.resolve()
  dispose()

  expect(closeCount).toBe(1)
  expect(exitCode).toBe(0)
  expect(listeners.size).toBe(0)
})

function createTempWebViewSrcDir(): string {
  const webviewSrcDir = mkdtempSync(join(tmpdir(), "webview-mock-preview-"))
  tempDirs.push(webviewSrcDir)
  mkdirSync(join(webviewSrcDir, "test", "acp-chat", "mocks"), { recursive: true })
  writeFileSync(join(webviewSrcDir, "package.json"), "{}\n")
  writeFileSync(join(webviewSrcDir, "test", "acp-chat", "mocks", "default.ts"), "")
  return webviewSrcDir
}

function cleanupTempDirs(): void {
  for (const dir of tempDirs.splice(0)) {
    rmSync(dir, { force: true, recursive: true })
  }
}
