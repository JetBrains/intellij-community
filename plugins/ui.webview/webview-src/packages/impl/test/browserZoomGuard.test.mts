// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { describe, expect, test } from "bun:test"

interface ListenerEntry {
  type: string
  listener: EventListener
  options?: boolean | AddEventListenerOptions
}

class FakeWindow {
  readonly listeners: ListenerEntry[] = []

  addEventListener(type: string, listener: EventListener, options?: boolean | AddEventListenerOptions): void {
    this.listeners.push({ type, listener, options })
  }

  dispatchWheel(event: TestWheelEvent): void {
    for (const entry of this.listeners) {
      if (entry.type === "wheel") {
        entry.listener(event as unknown as Event)
      }
    }
  }
}

class TestWheelEvent {
  defaultPrevented = false

  constructor(readonly ctrlKey: boolean, readonly cancelable: boolean = true) {
  }

  preventDefault(): void {
    this.defaultPrevented = true
  }
}

const windowStub = new FakeWindow()
Object.defineProperty(globalThis, "window", {
  configurable: true,
  value: windowStub,
})

const { installWebViewBrowserZoomGuard } = await import("../src/browserZoomGuard.ts")

describe("WebView browser zoom guard", () => {
  test("prevents only cancelable ctrl-wheel browser zoom default", () => {
    installWebViewBrowserZoomGuard("webkit")
    expect(windowStub.listeners.length).toBe(0)

    installWebViewBrowserZoomGuard("webview2")
    installWebViewBrowserZoomGuard("webview2")

    expect(windowStub.listeners.length).toBe(1)
    expect(windowStub.listeners[0].options).toEqual({ passive: false })

    const browserZoomWheel = new TestWheelEvent(true)
    windowStub.dispatchWheel(browserZoomWheel)
    expect(browserZoomWheel.defaultPrevented).toBe(true)

    const plainWheel = new TestWheelEvent(false)
    windowStub.dispatchWheel(plainWheel)
    expect(plainWheel.defaultPrevented).toBe(false)

    const nonCancelableBrowserZoomWheel = new TestWheelEvent(true, false)
    windowStub.dispatchWheel(nonCancelableBrowserZoomWheel)
    expect(nonCancelableBrowserZoomWheel.defaultPrevented).toBe(false)
  })
})
