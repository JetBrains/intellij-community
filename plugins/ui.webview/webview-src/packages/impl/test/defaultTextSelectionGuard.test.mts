// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { beforeEach, describe, expect, test } from "bun:test"

class FakeElement {
  readonly children: FakeElement[] = []
  readonly attributes = new Map<string, string>()
  textContent = ""

  constructor(readonly tagName: string) {
  }

  setAttribute(name: string, value: string): void {
    this.attributes.set(name, value)
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null
  }

  appendChild(child: FakeElement): FakeElement {
    this.children.push(child)
    return child
  }

  querySelector(selector: string): FakeElement | null {
    return this.children.find(child => matchesSelector(child, selector)) ?? null
  }
}

class FakeDocument {
  readonly head = new FakeElement("head")

  createElement(tagName: string): FakeElement {
    return new FakeElement(tagName)
  }
}

let documentStub: FakeDocument
Object.defineProperty(globalThis, "document", {
  configurable: true,
  get: () => documentStub,
})

const { DEFAULT_TEXT_SELECTION_GUARD_CSS, installWebViewDefaultTextSelectionGuard } = await import("../src/defaultTextSelectionGuard.ts")

describe("WebView default text selection guard", () => {
  beforeEach(() => {
    documentStub = new FakeDocument()
  })

  test("injects selectable text guard styles once by default", () => {
    installWebViewDefaultTextSelectionGuard()
    installWebViewDefaultTextSelectionGuard()

    expect(documentStub.head.children.length).toBe(1)
    const style = documentStub.head.children[0]
    expect(style.tagName).toBe("style")
    expect(style.getAttribute("data-wvi-default-text-selection-guard")).toBe("")
    expect(style.textContent).toBe(DEFAULT_TEXT_SELECTION_GUARD_CSS)
  })

  test("skips style injection when view opts out", () => {
    const meta = documentStub.createElement("meta")
    meta.setAttribute("name", "wvi-enable-default-text-selection-guard")
    meta.setAttribute("content", "false")
    documentStub.head.appendChild(meta)

    installWebViewDefaultTextSelectionGuard()

    expect(documentStub.head.children.length).toBe(1)
    expect(documentStub.head.children[0]).toBe(meta)
  })

  test("keeps root guard and explicit text opt-ins in generated CSS", () => {
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes(":where(body.ij-webview-root)")).toBe(true)
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes("user-select: none")).toBe(true)
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes("-webkit-user-select: none")).toBe(true)
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes(".webview-selectable-text")).toBe(true)
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes("[data-webview-selectable=\"true\"]")).toBe(true)
    expect(DEFAULT_TEXT_SELECTION_GUARD_CSS.includes("user-select: text")).toBe(true)
  })
})

function matchesSelector(element: FakeElement, selector: string): boolean {
  if (selector === "style[data-wvi-default-text-selection-guard]") {
    return element.tagName === "style" && element.attributes.has("data-wvi-default-text-selection-guard")
  }
  if (selector === "meta[name=\"wvi-enable-default-text-selection-guard\"]") {
    return element.tagName === "meta" && element.getAttribute("name") === "wvi-enable-default-text-selection-guard"
  }
  return false
}
