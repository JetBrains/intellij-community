// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/// <reference path="./bun-test.d.ts" />

import { beforeEach, describe, expect, test } from "bun:test"
import type { WebViewBridge, WebViewTheme, WebViewThemeApi } from "@jetbrains/intellij-webview"

type PostedMessage = Record<string, unknown>

interface TestWebViewBridge extends WebViewBridge {
  __deliver(raw: string): void
}

interface TestWindow {
  location: { search: string }
  __WVI__?: TestWebViewBridge
  __WVI_THEME__?: WebViewThemeApi
  chrome?: unknown
  __wviJcefQuery?: unknown
  webkit?: {
    messageHandlers: {
      webviewIpc: {
        postMessage(message: string): void
      }
    }
  }
}

const postedMessages: PostedMessage[] = []

class FakeElement {
  readonly tagName: string
  id = ""
  textContent: string | null = null
  readonly style: Record<string, string> = {}
  readonly attributes = new Map<string, string>()
  readonly children: FakeElement[] = []
  parentNode: FakeElement | null = null

  constructor(tagName: string) {
    this.tagName = tagName
  }

  get firstChild(): FakeElement | null {
    return this.children[0] ?? null
  }

  setAttribute(name: string, value: unknown): void {
    this.attributes.set(name, String(value))
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null
  }

  insertBefore(child: FakeElement, before: FakeElement | null): FakeElement {
    child.parentNode?.removeChild(child)
    child.parentNode = this
    if (before == null) {
      this.children.push(child)
      return child
    }
    const index = this.children.indexOf(before)
    if (index < 0) {
      this.children.push(child)
    }
    else {
      this.children.splice(index, 0, child)
    }
    return child
  }

  appendChild(child: FakeElement): FakeElement {
    return this.insertBefore(child, null)
  }

  removeChild(child: FakeElement): FakeElement {
    const index = this.children.indexOf(child)
    if (index >= 0) {
      this.children.splice(index, 1)
      child.parentNode = null
    }
    return child
  }
}

class FakeDocument {
  readonly documentElement = new FakeElement("html")
  readonly head = new FakeElement("head")
  readonly body = new FakeElement("body")
  readonly listeners = new Map<string, EventListener[]>()

  createElement(tagName: string): FakeElement {
    return new FakeElement(tagName)
  }

  getElementById(id: string): FakeElement | null {
    return findById(this.documentElement, id) ?? findById(this.head, id) ?? findById(this.body, id)
  }

  addEventListener(type: string, listener: EventListener): void {
    let listeners = this.listeners.get(type)
    if (!listeners) {
      listeners = []
      this.listeners.set(type, listeners)
    }
    listeners.push(listener)
  }

  removeEventListener(type: string, listener: EventListener): void {
    const listeners = this.listeners.get(type)
    if (!listeners) {
      return
    }
    const index = listeners.indexOf(listener)
    if (index >= 0) {
      listeners.splice(index, 1)
    }
  }
}

function findById(element: FakeElement, id: string): FakeElement | null {
  if (element.id === id) {
    return element
  }
  for (const child of element.children) {
    const found = findById(child, id)
    if (found) {
      return found
    }
  }
  return null
}

const windowStub: TestWindow = {
  location: { search: "" },
}

let documentStub = new FakeDocument()

Object.defineProperty(globalThis, "window", {
  configurable: true,
  value: windowStub,
})

Object.defineProperty(globalThis, "document", {
  configurable: true,
  get() {
    return documentStub
  },
})

function resetEnvironment() {
  postedMessages.length = 0
  documentStub = new FakeDocument()
  windowStub.__WVI__ = undefined
  windowStub.__WVI_THEME__ = undefined
  windowStub.chrome = undefined
  windowStub.__wviJcefQuery = undefined
  windowStub.location = { search: "" }
  windowStub.webkit = {
    messageHandlers: {
      webviewIpc: {
        postMessage(message: string) {
          postedMessages.push(JSON.parse(message) as PostedMessage)
        },
      },
    },
  }
}

describe("WebView bridge bootstrap", () => {
  beforeEach(() => {
    resetEnvironment()
  })

  test("installs core bridge without theme or platform style side effects", async () => {
    const { installWebViewBridge } = await import("../src/bridge.ts")

    const bridge = installWebViewBridge() as TestWebViewBridge

    expect(windowStub.__WVI__).toBe(bridge)
    expect(bridge.__installed).toBe(true)
    expect(windowStub.__WVI_THEME__).toBeUndefined()
    expect(documentStub.documentElement.getAttribute("data-theme")).toBeNull()
    expect(documentStub.getElementById("__wvi-ij-themes")).toBeNull()
    expect(postedMessages).toContainEqual({ jsonrpc: "2.0", method: "$/webview/runtimeInfoRequest" })
    expect(postedMessages.some((message) => message.method === "webview.theme/themeRequest")).toBe(false)
  })

  test("installs theme as a separate feature on the existing bridge", async () => {
    windowStub.location = { search: "?__webviewTheme=light" }
    const [{ installWebViewBridge }, { installIJTheming }] = await Promise.all([
      import("../src/bridge.ts"),
      import("../src/theme.ts"),
    ])

    const bridge = installWebViewBridge() as TestWebViewBridge
    const bridgeMessageCount = postedMessages.length
    installIJTheming(bridge)

    const themeApi = requireInstalledThemeApi()
    expect(windowStub.__WVI__).toBe(bridge)
    expect(themeApi.current).toBe("light")
    expect(documentStub.getElementById("__wvi-ij-themes")).not.toBeNull()
    expect(documentStub.documentElement.getAttribute("data-theme")).toBe("light")
    expect(postedMessages.slice(bridgeMessageCount)).toContainEqual({
      jsonrpc: "2.0",
      id: 1,
      method: "webview.theme/themeRequest",
    })

    const changes: WebViewTheme[] = []
    const registration = themeApi.onChanged((theme) => {
      changes.push(theme)
    })
    bridge.__deliver(JSON.stringify({
      jsonrpc: "2.0",
      method: "webview.theme/themeChanged",
      params: { theme: "dark" },
    }))

    expect(themeApi.current).toBe("dark")
    expect(changes).toEqual(["dark"])
    registration.close()
  })
})

function requireInstalledThemeApi(): WebViewThemeApi {
  const themeApi = windowStub.__WVI_THEME__
  if (!themeApi) {
    throw new Error("Expected WebView theme API to be installed")
  }
  return themeApi
}
