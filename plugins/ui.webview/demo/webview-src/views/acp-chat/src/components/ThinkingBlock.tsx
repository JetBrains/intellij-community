// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Rendered by assistant-ui for `reasoning` message parts (ACP agent_thought_chunk). Loosely typed for version tolerance.
export function ThinkingBlock(props: any) {
  const text: string = props?.text ?? ""
  if (!text) return null
  return (
    <details className="acpThinking" open>
      <summary className="acpThinkingSummary">Thinking</summary>
      <div className="acpThinkingBody">{text}</div>
    </details>
  )
}
