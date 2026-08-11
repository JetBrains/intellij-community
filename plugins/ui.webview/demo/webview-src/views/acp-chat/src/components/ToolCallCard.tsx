// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { AuthCard } from "./AuthPrompt"

// Rendered by assistant-ui as the fallback for tool-call message parts. Reads the ACP tool details we packed into
// the part's `result` field (see useAcpChat.flushTurn). Props are loosely typed to tolerate assistant-ui versions.
export function ToolCallCard(props: any) {
  const result = props?.result ?? {}
  if (result.kind === "auth" && result.auth) {
    return <AuthCard auth={result.auth} />
  }
  const title: string = result.title ?? props?.toolName ?? "Tool call"
  const kind: string = result.kind ?? props?.toolName ?? "other"
  const status: string = result.status ?? "in_progress"
  const text: string | undefined = result.text
  const diff = result.diff as { path: string; oldText: string | null; newText: string } | undefined
  const hasDetails = Boolean(text) || Boolean(diff)
  const className = `acpTool acpTool--${status} acpTool--${kind}`

  if (!hasDetails) {
    return (
      <div className={`${className} acpTool--empty`}>
        <ToolHeader kind={kind} title={title} status={status} />
      </div>
    )
  }

  return (
    <details className={className}>
      <ToolHeader kind={kind} title={title} status={status} expandable />
      <div className="acpToolDetails">
        {text ? <pre className="acpToolText">{text}</pre> : null}
        {diff ? (
          <div className="acpToolDiff">
            <div className="acpToolDiffPath">{diff.path}</div>
            <pre className="acpToolDiffBody">{renderDiff(diff)}</pre>
          </div>
        ) : null}
      </div>
    </details>
  )
}

function ToolHeader(props: { kind: string; title: string; status: string; expandable?: boolean }) {
  const statusLabel = props.status.replace("_", " ")
  const content = (
    <>
      <span className={`acpToolIcon acpToolIcon--${props.kind}`} aria-hidden="true"><ToolKindIcon kind={props.kind} /></span>
      <span className="acpToolTitle" title={props.title}>{props.title}</span>
      <span className={`acpToolStatus acpToolStatus--${props.status}`} role="img" aria-label={statusLabel} title={statusLabel}>
        <StatusIcon status={props.status} />
      </span>
    </>
  )

  if (props.expandable) {
    return (
      <summary className="acpToolHeader" aria-label={`${props.title}. ${statusLabel}. Show tool call details`}>
        {content}
      </summary>
    )
  }
  return <div className="acpToolHeader">{content}</div>
}

function StatusIcon(props: { status: string }) {
  switch (props.status) {
    case "completed":
    case "success":
      return <SuccessIcon />
    case "failed":
      return <FailedIcon />
    case "pending":
    case "in_progress":
    default:
      return <SpinnerIcon />
  }
}

function ToolKindIcon(props: { kind: string }) {
  const kind = props.kind.toLocaleLowerCase()
  if (kind.includes("read") || kind.includes("open")) return <ReadIcon />
  if (kind.includes("search") || kind.includes("find") || kind.includes("grep")) return <SearchIcon />
  if (kind.includes("execute") || kind.includes("shell") || kind.includes("terminal") || kind.includes("bash")) return <ExecuteIcon />
  if (kind.includes("write") || kind.includes("edit") || kind.includes("patch") || kind.includes("diff")) return <EditIcon />
  return <OtherIcon />
}

function SuccessIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="m3.2 8.2 3.1 3.1 6.5-6.6" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function FailedIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M4.4 4.4 11.6 11.6M11.6 4.4 4.4 11.6" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

function SpinnerIcon() {
  return (
    <svg className="acpToolStatusSpinner" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <circle cx="8" cy="8" r="5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeOpacity="0.25" />
      <path d="M13 8a5 5 0 0 0-5-5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function ReadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M4 2.7h5.4L12 5.3v8H4z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="M9.4 2.8v2.6H12M6 8h4M6 10.5h4" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <circle cx="7" cy="7" r="3.7" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path d="m9.8 9.8 3 3" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  )
}

function ExecuteIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M2.8 4.2h10.4v7.6H2.8z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="m5.2 6.3 1.7 1.7-1.7 1.7M8.3 10h2.5" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function EditIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M3.5 12.5h2.3l6-6-2.3-2.3-6 6zM8.7 5l2.3 2.3" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function OtherIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M3.5 4.5h9M3.5 8h9M3.5 11.5h9" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  )
}

function renderDiff(diff: { oldText: string | null; newText: string }): string {
  const removed = (diff.oldText ?? "").split("\n").filter(line => line.length > 0).map(line => `- ${line}`)
  const added = diff.newText.split("\n").filter(line => line.length > 0).map(line => `+ ${line}`)
  return [...removed, ...added].join("\n")
}
