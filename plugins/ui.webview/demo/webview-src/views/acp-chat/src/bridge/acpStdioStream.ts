// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { ndJsonStream, type Stream } from "@agentclientprotocol/sdk"
import { acpBridgeHost, setBridgePageHandlers, type ExitDto, type LineDto } from "./webviewApi"

/**
 * Bridges the Kotlin process stdio (delivered as ndjson lines over the WebView bridge) to a web `Stream` the ACP
 * TypeScript SDK can consume: agent stdout lines feed a `ReadableStream`; SDK output is split into lines and written
 * back to the agent's stdin via the host API.
 */
export interface AgentStdioStream {
  stream: Stream
  onExit(callback: (code: number | null) => void): void
  /** Tears down the input stream so the SDK rejects any in-flight request (e.g. a hanging `authenticate`). */
  close(): void
}

export function createAgentStdioStream(): AgentStdioStream {
  const encoder = new TextEncoder()
  const decoder = new TextDecoder()
  let inputController: ReadableStreamDefaultController<Uint8Array> | null = null
  let exitCallback: ((code: number | null) => void) | null = null

  const input = new ReadableStream<Uint8Array>({
    start(controller) {
      inputController = controller
    },
    cancel() {
      inputController = null
      setBridgePageHandlers(null)
    },
  })

  setBridgePageHandlers({
    onAgentStdout(params: LineDto) {
      inputController?.enqueue(encoder.encode(params.line + "\n"))
    },
    onAgentExit(params: ExitDto) {
      try {
        inputController?.close()
      }
      catch {
        // already closed
      }
      inputController = null
      exitCallback?.(params.code ?? null)
    },
  })

  const output = new WritableStream<Uint8Array>({
    write(chunk) {
      // The SDK writes complete newline-delimited JSON-RPC messages; forward each non-empty line to the agent stdin.
      const text = decoder.decode(chunk)
      for (const line of text.split("\n")) {
        if (line.length > 0) {
          void acpBridgeHost.sendStdin({ line })
        }
      }
    },
  })

  return {
    stream: ndJsonStream(output, input),
    onExit(callback) {
      exitCallback = callback
    },
    close() {
      try {
        // Erroring the readable end makes the SDK's read loop fail and reject pending requests, instead of hanging.
        inputController?.error(new Error("ACP connection closed"))
      }
      catch {
        // already closed/errored
      }
      inputController = null
      setBridgePageHandlers(null)
    },
  }
}
