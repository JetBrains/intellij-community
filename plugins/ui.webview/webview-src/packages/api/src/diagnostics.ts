// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export type WebViewLogDetails = string | Record<string, string | number | boolean | null | undefined>

export interface WebViewLogger {
  event(metric: string, details?: WebViewLogDetails): void
  perf(metric: string, durationMs: number, details?: WebViewLogDetails): void
  perfSince(metric: string, startedAtMs: number, details?: WebViewLogDetails): void
}

export function getPerfLogger(scope: string): WebViewLogger {
  return {
    event(metric, details) {
      console.trace(formatWebViewLogMessage("event", scope, metric, details))
    },
    perf(metric, durationMs, details) {
      console.trace(formatWebViewPerfMessage(scope, metric, durationMs, details))
    },
    perfSince(metric, startedAtMs, details) {
      this.perf(metric, performance.now() - startedAtMs, details)
    },
  }
}

function formatWebViewPerfMessage(scope: string, metric: string, durationMs: number, details?: WebViewLogDetails): string {
  return withDetails(`perf: ${scope}.${metric} = ${Math.round(durationMs)}ms`, details)
}

function formatWebViewLogMessage(kind: string, scope: string, metric: string, details?: WebViewLogDetails): string {
  return withDetails(`${kind}: ${scope}.${metric}`, details)
}

function withDetails(message: string, details?: WebViewLogDetails): string {
  const formattedDetails = formatWebViewLogDetails(details)
  return formattedDetails.length === 0 ? message : `${message} - ${formattedDetails}`
}

function formatWebViewLogDetails(details?: WebViewLogDetails): string {
  if (details === undefined) return ""
  if (typeof details === "string") return details

  return Object.entries(details)
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => `${key}=${value}`)
    .join(", ")
}
