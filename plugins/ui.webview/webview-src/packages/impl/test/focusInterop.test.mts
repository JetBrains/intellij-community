// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/// <reference path="./bun-test.d.ts" />

import {afterEach, beforeEach, describe, expect, test} from "bun:test"
import type {
  WebViewBridge,
  WebViewFocusEntry,
  WebViewFocusExit,
  WebViewMessageRegistration,
} from "@jetbrains/intellij-webview"

interface FocusPageImplementation {
  enter(params: WebViewFocusEntry): void
  leave(): void
}

class FakeElement {
  readonly tagName: string
  readonly style: Record<string, string> = {}
  readonly attributes = new Map<string, string>()
  readonly children: FakeElement[] = []
  parentElement: FakeElement | null = null
  shadowRoot: FakeShadowRoot | null = null
  offsetWidth = 10
  offsetHeight = 10
  blurCount = 0
  private root: FakeDocument | FakeShadowRoot

  constructor(tagName: string, readonly ownerDocument: FakeDocument) {
    this.tagName = tagName.toUpperCase()
    this.root = ownerDocument
  }

  get id(): string {
    return this.getAttribute("id") ?? ""
  }

  set id(value: string) {
    this.setAttribute("id", value)
  }

  setAttribute(name: string, value: unknown): void {
    this.attributes.set(name, String(value))
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null
  }

  hasAttribute(name: string): boolean {
    return this.attributes.has(name)
  }

  appendChild(child: FakeElement): FakeElement {
    child.parentElement = this
    child.setRoot(this.root)
    this.children.push(child)
    return child
  }

  attachShadow(): FakeShadowRoot {
    const shadowRoot = new FakeShadowRoot(this, this.ownerDocument)
    this.shadowRoot = shadowRoot
    return shadowRoot
  }

  focus(): void {
    const root = this.getRootNode()
    if (root instanceof FakeShadowRoot) {
      root.activeElement = this as unknown as Element
      this.ownerDocument.activeElement = root.host as unknown as Element
    }
    else {
      this.ownerDocument.activeElement = this as unknown as Element
    }
  }

  blur(): void {
    this.blurCount++
    const root = this.getRootNode()
    const element = this as unknown as Element
    if (root instanceof FakeShadowRoot) {
      if (root.activeElement === element) {
        root.activeElement = null
      }
      if (this.ownerDocument.activeElement === root.host as unknown as Element) {
        this.ownerDocument.activeElement = null
      }
    }
    else if (this.ownerDocument.activeElement === element) {
      this.ownerDocument.activeElement = null
    }
  }

  getClientRects(): unknown[] {
    return this.offsetWidth > 0 || this.offsetHeight > 0 ? [{}] : []
  }

  getRootNode(): FakeDocument | FakeShadowRoot {
    return this.root
  }

  setRoot(root: FakeDocument | FakeShadowRoot): void {
    this.root = root
    for (const child of this.children) {
      child.setRoot(root)
    }
  }
}

class FakeShadowRoot {
  readonly children: FakeElement[] = []
  activeElement: Element | null = null

  constructor(readonly host: FakeElement, private readonly ownerDocument: FakeDocument) {
  }

  appendChild(child: FakeElement): FakeElement {
    child.parentElement = null
    child.setRoot(this)
    this.children.push(child)
    return child
  }

  createElement(tagName: string): FakeElement {
    return new FakeElement(tagName, this.ownerDocument)
  }
}

class FakeDocument {
  readonly documentElement = new FakeElement("html", this)
  readonly body = new FakeElement("body", this)
  activeElement: Element | null = null
  private readonly listeners = new Map<string, Array<(event: Event) => void>>()

  createElement(tagName: string): FakeElement {
    return new FakeElement(tagName, this)
  }

  addEventListener(type: string, listener: (event: Event) => void): void {
    let listeners = this.listeners.get(type)
    if (!listeners) {
      listeners = []
      this.listeners.set(type, listeners)
    }
    listeners.push(listener)
  }

  dispatchKeyDown(init: Partial<KeyboardEvent>): FakeKeyboardEvent {
    const event = new FakeKeyboardEvent(init)
    this.dispatchToListeners("keydown", event as unknown as Event)
    return event
  }

  dispatchPointerDown(target?: FakeElement, afterCapture?: (event: FakeEvent) => void): FakeEvent {
    const event = new FakeEvent(target)
    this.dispatchToListeners("pointerdown", event as unknown as Event)
    afterCapture?.(event)
    return event
  }

  dispatch(type: string): void {
    this.dispatchToListeners(type, new FakeEvent() as unknown as Event)
  }

  private dispatchToListeners(type: string, event: Event): void {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }
}

class FakeWindow {
  private readonly listeners = new Map<string, Array<(event: Event) => void>>()
  private readonly dispatchedCounts = new Map<string, number>()

  getComputedStyle(element: FakeElement): { display: string, visibility: string } {
    return {
      display: element.style.display ?? "block",
      visibility: element.style.visibility ?? "visible",
    }
  }

  addEventListener(type: string, listener: (event: Event) => void): void {
    let listeners = this.listeners.get(type)
    if (!listeners) {
      listeners = []
      this.listeners.set(type, listeners)
    }
    listeners.push(listener)
  }

  removeEventListener(type: string, listener: (event: Event) => void): void {
    const listeners = this.listeners.get(type)
    if (!listeners) return
    const index = listeners.indexOf(listener)
    if (index >= 0) {
      listeners.splice(index, 1)
    }
  }

  dispatchEvent(event: Event): boolean {
    this.dispatch(event.type, event)
    return true
  }

  dispatch(type: string, event: Event = new Event(type)): void {
    this.dispatchedCounts.set(type, this.dispatchedCount(type) + 1)
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }

  dispatchedCount(type: string): number {
    return this.dispatchedCounts.get(type) ?? 0
  }

  resetDispatchedCounts(): void {
    this.dispatchedCounts.clear()
  }
}

class FakeKeyboardEvent {
  readonly key: string
  readonly shiftKey: boolean
  readonly altKey: boolean
  readonly ctrlKey: boolean
  readonly metaKey: boolean
  readonly isComposing: boolean
  defaultPrevented = false

  constructor(init: Partial<KeyboardEvent>) {
    this.key = init.key ?? ""
    this.shiftKey = init.shiftKey ?? false
    this.altKey = init.altKey ?? false
    this.ctrlKey = init.ctrlKey ?? false
    this.metaKey = init.metaKey ?? false
    this.isComposing = init.isComposing ?? false
  }

  preventDefault(): void {
    this.defaultPrevented = true
  }
}

class FakeEvent {
  defaultPrevented = false

  constructor(readonly target: FakeElement | null = null) {
  }

  preventDefault(): void {
    this.defaultPrevented = true
  }

  composedPath(): FakeElement[] {
    if (!this.target) {
      return []
    }
    const path: FakeElement[] = []
    for (let current: FakeElement | null = this.target; current; current = current.parentElement) {
      path.push(current)
    }
    const root = this.target.getRootNode()
    if (root instanceof FakeShadowRoot) {
      path.push(root.host)
    }
    return path
  }
}

class FakeBridge {
  readonly exits: WebViewFocusExit[] = []
  readonly activations: string[] = []
  private pageApi: FocusPageImplementation | undefined

  callable(): { activated(): void, exit(params: WebViewFocusExit): void } {
    return {
      activated: () => {
        this.activations.push("activated")
      },
      exit: (params) => {
        this.exits.push(params)
      },
    }
  }

  implement(_id: unknown, implementation: FocusPageImplementation): WebViewMessageRegistration {
    this.pageApi = implementation
    return {
      close() {
      }
    }
  }

  enter(params: WebViewFocusEntry): void {
    this.pageApi?.enter(params)
  }

  leave(): void {
    this.pageApi?.leave()
  }
}

const windowStub = new FakeWindow()

let documentStub = new FakeDocument()
const originalConsoleDebug = console.debug

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

Object.defineProperty(globalThis, "ShadowRoot", {
  configurable: true,
  value: FakeShadowRoot,
})

const {collectTabbableElements, installWebViewFocusInterop} = await import("../src/focusInterop.ts")

describe("WebView focus interop", () => {
  beforeEach(() => {
    documentStub = new FakeDocument()
    console.debug = () => {
    }
    windowStub.dispatch("blur")
    windowStub.resetDispatchedCounts()
  })

  afterEach(() => {
    console.debug = originalConsoleDebug
  })

  test("collects boundary tabbables using browser-like order", () => {
    const positive = appendElement("button", "positive")
    positive.setAttribute("tabindex", "2")
    appendElement("button", "first")
    appendElement("a", "link").setAttribute("href", "https://example.test")
    appendElement("button", "disabled").setAttribute("disabled", "")
    appendElement("button", "negative").setAttribute("tabindex", "-1")
    appendElement("input", "hidden-input").setAttribute("type", "hidden")

    const hiddenParent = appendElement("div", "hidden-parent")
    hiddenParent.setAttribute("hidden", "")
    hiddenParent.appendChild(button("hidden-child"))

    expect(collectTabbableElements().map((element) => element.id)).toEqual(["positive", "first", "link"])
  })

  test("includes focusable elements from open shadow roots", () => {
    const host = appendElement("div", "host")
    const shadowButton = button("shadow-button")
    host.attachShadow().appendChild(shadowButton)

    expect(collectTabbableElements().map((element) => element.id)).toEqual(["shadow-button"])
  })

  test("focuses boundary elements on host entry and exits on tab boundary", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const first = appendElement("button", "first")
    const last = appendElement("button", "last")

    bridge.enter({direction: "forward"})
    expect(documentStub.activeElement).toBe(first)

    last.focus()
    const event = documentStub.dispatchKeyDown({key: "Tab"})

    expect(event.defaultPrevented).toBe(true)
    expect(bridge.exits).toEqual([{direction: "forward"}])
  })

  test("does not exit when active element opts out to native tab handling", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const editor = appendElement("textarea", "editor")
    editor.setAttribute("data-webview-focus-boundary", "native")
    editor.focus()

    const event = documentStub.dispatchKeyDown({key: "Tab"})

    expect(event.defaultPrevented).toBe(false)
    expect(bridge.exits).toEqual([])
  })

  test("notifies host when webview document is activated", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    appendElement("button", "first")

    documentStub.dispatch("pointerdown")

    expect(documentStub.activeElement).toBe(null)
    expect(bridge.exits).toEqual([])
    expect(bridge.activations).toEqual(["activated"])
  })

  test("focuses clicked focusable element after host activation", async () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const input = appendElement("input", "clicked")

    documentStub.dispatchPointerDown(input)
    await flushPointerFocus()

    expect(documentStub.activeElement).toBe(input)
    expect(bridge.exits).toEqual([])
    expect(bridge.activations).toEqual(["activated"])
  })

  test("does not force focus when pointer default action is prevented", async () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const trigger = appendElement("button", "clicked")

    const event = documentStub.dispatchPointerDown(trigger, (event) => event.preventDefault())
    await flushPointerFocus()

    expect(event.defaultPrevented).toBe(true)
    expect(documentStub.activeElement).toBe(null)
    expect(bridge.exits).toEqual([])
    expect(bridge.activations).toEqual(["activated"])
  })

  test("does not force focus inside native focus boundary", async () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const editor = appendElement("textarea", "editor")
    editor.setAttribute("data-webview-focus-boundary", "native")

    documentStub.dispatchPointerDown(editor)
    await flushPointerFocus()

    expect(documentStub.activeElement).toBe(null)
    expect(bridge.exits).toEqual([])
    expect(bridge.activations).toEqual(["activated"])
  })

  test("synthesizes window blur when host focus leaves the page", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const first = appendElement("button", "first")

    bridge.enter({direction: "forward"})
    bridge.leave()

    expect(documentStub.activeElement).toBeNull()
    expect(windowStub.dispatchedCount("blur")).toBe(1)

    bridge.leave()

    expect(windowStub.dispatchedCount("blur")).toBe(1)
    expect(bridge.exits).toEqual([])
    expect(first.id).toBe("first")
  })

  test("blurs native controls inside shadow roots when host focus leaves the page", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    const first = appendElement("button", "first")
    const selectHost = appendElement("jb-select", "status-filter")
    const shadowSelect = selectHost.attachShadow().appendChild(documentStub.createElement("select"))

    bridge.enter({direction: "forward"})
    expect(documentStub.activeElement).toBe(first)

    bridge.leave()

    expect(first.blurCount).toBe(1)
    expect(shadowSelect.blurCount).toBe(1)
    expect(windowStub.dispatchedCount("blur")).toBe(1)
  })

  test("does not synthesize duplicate window blur after native blur", () => {
    const bridge = new FakeBridge()
    installWebViewFocusInterop(bridge as unknown as WebViewBridge)
    appendElement("button", "first")

    bridge.enter({direction: "forward"})
    windowStub.dispatch("blur")
    windowStub.resetDispatchedCounts()
    bridge.leave()

    expect(windowStub.dispatchedCount("blur")).toBe(0)
    expect(bridge.exits).toEqual([])
  })
})

function appendElement(tagName: string, id: string): FakeElement {
  const element = documentStub.createElement(tagName)
  element.id = id
  documentStub.body.appendChild(element)
  return element
}

function button(id: string): FakeElement {
  const element = documentStub.createElement("button")
  element.id = id
  return element
}

function flushPointerFocus(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
