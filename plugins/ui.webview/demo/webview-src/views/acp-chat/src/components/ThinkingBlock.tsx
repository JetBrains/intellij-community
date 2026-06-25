// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useMessage, useMessagePartReasoning, useSmooth } from "@assistant-ui/react"

// Rendered by assistant-ui for `reasoning` message parts (ACP agent_thought_chunk). Loosely typed for version tolerance.
const SMOOTH_TEXT_OPTIONS = { drainMs: 250, maxCharIntervalMs: 5, minCommitMs: 33 }

export function ThinkingBlock() {
  const { text, status } = useSmooth(useMessagePartReasoning(), SMOOTH_TEXT_OPTIONS)
  const messageStatus = useMessage(message => message.status)
  const running = status.type === "running" || messageStatus?.type === "running"
  if (!text && !running) return null
  return (
    <details className="acpThinking" open>
      <summary className="acpThinkingSummary">Thinking</summary>
      <div className="acpThinkingBody">
        {text}
        {running ? <span aria-hidden="true" className="acpStreamingCaret" /> : null}
      </div>
    </details>
  )
}
