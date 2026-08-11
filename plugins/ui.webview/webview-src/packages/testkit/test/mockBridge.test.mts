// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { beforeEach, describe, expect, test } from "bun:test"
import { apiId, type WebViewCallable, type WebViewImplementable } from "../../api/src/index"
import { defineWebViewMock, installMockWebViewBridge } from "../src"

interface HostApi extends WebViewCallable, WebViewImplementable {
  loadState(): Promise<{ value: string }>
  saveState(params: { value: string }): Promise<{ ok: boolean }>
}

interface PageApi extends WebViewCallable, WebViewImplementable {
  stateChanged(params: { value: string }): void
}

const hostApiId = apiId<HostApi>()("test.host")
const pageApiId = apiId<PageApi>()("test.page")

describe("mock WebView bridge", () => {
  beforeEach(() => {
    const testWindow = globalThis.window as typeof globalThis.window & { __WVI__?: unknown; __WVI_MOCK__?: unknown }
    delete testWindow.__WVI__
    delete testWindow.__WVI_MOCK__
  })

  test("routes page calls to mock host implementations", async () => {
    const context = installMockWebViewBridge()
    context.host.implement(hostApiId, {
      async loadState() {
        return { value: "initial" }
      },
      async saveState(params) {
        return { ok: params.value === "changed" }
      },
    })

    const host = context.bridge.callable(hostApiId)

    expect(await host.loadState()).toEqual({ value: "initial" })
    expect(await host.saveState({ value: "changed" })).toEqual({ ok: true })
    expect(context.calls.byMethod("test.host/saveState")).toEqual([
      { side: "host", method: "test.host/saveState", params: { value: "changed" } },
    ])
  })

  test("routes mock page calls to page implementations", async () => {
    const context = installMockWebViewBridge()
    const received: string[] = []
    context.bridge.implement(pageApiId, {
      stateChanged(params) {
        received.push(params.value)
      },
    })

    context.page.callable(pageApiId).stateChanged({ value: "next" })

    expect(received).toEqual(["next"])
    expect(context.calls.byMethod("test.page/stateChanged")).toEqual([
      { side: "page", method: "test.page/stateChanged", params: { value: "next" } },
    ])
  })

  test("applies mock setup", async () => {
    const context = installMockWebViewBridge()
    context.apply(defineWebViewMock(({ host }) => {
      host.implement(hostApiId, {
        async loadState() {
          return { value: "from-mock" }
        },
        async saveState() {
          return { ok: true }
        },
      })
    }))

    expect(await context.bridge.callable(hostApiId).loadState()).toEqual({ value: "from-mock" })
  })

  test("rejects missing methods with JSON-RPC-like code", async () => {
    const context = installMockWebViewBridge()

    try {
      await context.bridge.callable(hostApiId).loadState()
      throw new Error("Expected missing mock method to reject")
    }
    catch (error) {
      expect((error as { code?: unknown }).code).toBe(-32601)
    }
  })
})
