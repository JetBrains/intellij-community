// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { afterEach, describe, expect, test } from "bun:test"
import { getPerfLogger } from "../src"

const originalConsoleTrace = console.trace
const originalPerformanceNow = performance.now

let traceMessages: string[] = []

console.trace = (...args: unknown[]) => {
  traceMessages.push(args.map(String).join(" "))
}

describe("getPerfLogger", () => {
  afterEach(() => {
    traceMessages = []
    console.trace = originalConsoleTrace
    Object.defineProperty(performance, "now", {
      configurable: true,
      value: originalPerformanceNow,
    })
  })

  test("logs scoped events with string details", () => {
    console.trace = (...args: unknown[]) => {
      traceMessages.push(args.map(String).join(" "))
    }

    getPerfLogger("markdown").event("contentChanged.received", "contentVersion=7")

    expect(traceMessages).toEqual(["event: markdown.contentChanged.received - contentVersion=7"])
  })

  test("logs scoped perf with rounded duration and flat object details", () => {
    console.trace = (...args: unknown[]) => {
      traceMessages.push(args.map(String).join(" "))
    }

    getPerfLogger("markdown").perf("render.afterFrame", 12.6, {
      contentVersion: 7,
      markdownChars: 42,
      cached: false,
      skipped: null,
      missing: undefined,
    })

    expect(traceMessages).toEqual(["perf: markdown.render.afterFrame = 13ms - contentVersion=7, markdownChars=42, cached=false"])
  })

  test("logs perf since start time", () => {
    console.trace = (...args: unknown[]) => {
      traceMessages.push(args.map(String).join(" "))
    }
    Object.defineProperty(performance, "now", {
      configurable: true,
      value: () => 120.4,
    })

    getPerfLogger("markdown").perfSince("pathLinks.collect", 100, { candidates: 3 })

    expect(traceMessages).toEqual(["perf: markdown.pathLinks.collect = 20ms - candidates=3"])
  })
})
