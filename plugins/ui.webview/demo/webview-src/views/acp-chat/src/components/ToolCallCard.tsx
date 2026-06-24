// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Rendered by assistant-ui as the fallback for tool-call message parts. Reads the ACP tool details we packed into
// the part's `result` field (see useAcpChat.flushTurn). Props are loosely typed to tolerate assistant-ui versions.
export function ToolCallCard(props: any) {
  const result = props?.result ?? {}
  const title: string = result.title ?? props?.toolName ?? "Tool call"
  const kind: string = result.kind ?? props?.toolName ?? "other"
  const status: string = result.status ?? "in_progress"
  const text: string | undefined = result.text
  const diff = result.diff as { path: string; oldText: string | null; newText: string } | undefined

  return (
    <div className={`acpTool acpTool--${status}`}>
      <div className="acpToolHeader">
        <span className={`acpToolKind acpToolKind--${kind}`}>{kind}</span>
        <span className="acpToolTitle">{title}</span>
        <span className={`acpToolStatus acpToolStatus--${status}`}>{status.replace("_", " ")}</span>
      </div>
      {text ? <pre className="acpToolText">{text}</pre> : null}
      {diff ? (
        <div className="acpToolDiff">
          <div className="acpToolDiffPath">{diff.path}</div>
          <pre className="acpToolDiffBody">{renderDiff(diff)}</pre>
        </div>
      ) : null}
    </div>
  )
}

function renderDiff(diff: { oldText: string | null; newText: string }): string {
  const removed = (diff.oldText ?? "").split("\n").filter(line => line.length > 0).map(line => `- ${line}`)
  const added = diff.newText.split("\n").filter(line => line.length > 0).map(line => `+ ${line}`)
  return [...removed, ...added].join("\n")
}
