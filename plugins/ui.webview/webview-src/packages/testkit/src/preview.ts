// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { createServer as createNetServer } from "node:net"
import { createServer } from "vite"
import { defineWebViewViewConfig } from "../../build/src/index"
import { withWebViewMockBridge } from "./vite"
import type { StartWebViewMockPreviewOptions, WebViewMockPreviewServer } from "./core"

export async function startWebViewMockPreview(options: StartWebViewMockPreviewOptions): Promise<WebViewMockPreviewServer> {
  const port = options.port ?? await findAvailablePort()
  const config = withWebViewMockBridge(defineWebViewViewConfig({
    webviewSrcDir: options.webviewSrcDir,
    id: options.viewId,
  }), {
    mock: options.mock,
  })
  const server = await createServer({
    ...config,
    server: {
      ...config.server,
      host: "127.0.0.1",
      port,
      strictPort: true,
    },
  })
  await server.listen()
  const address = server.httpServer?.address()
  const actualPort = typeof address === "object" && address ? address.port : port
  if (typeof actualPort !== "number") {
    await server.close()
    throw new Error("Vite did not expose a preview server port")
  }
  return {
    url: `http://127.0.0.1:${actualPort}/`,
    close() {
      return server.close()
    },
  }
}

function findAvailablePort(): Promise<number> {
  return new Promise<number>((resolve, reject) => {
    const server = createNetServer()
    server.unref()
    server.on("error", reject)
    server.listen(0, "127.0.0.1", () => {
      const address = server.address()
      server.close(() => {
        if (typeof address === "object" && address) {
          resolve(address.port)
        }
        else {
          reject(new Error("Node did not expose an available preview port"))
        }
      })
    })
  }).catch((error) => {
    throw new Error("Cannot allocate WebView mock preview port: " + errorText(error))
  })
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
