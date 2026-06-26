// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { beforeEach, describe, expect, test } from "bun:test"
import { AllIcons, IconSet, type WebViewTheme, type WebViewThemeApi } from "../src"

let currentTheme: WebViewTheme = "light"

const themeApi: WebViewThemeApi = {
  get current() {
    return currentTheme
  },
  onChanged() {
    return { close() {} }
  },
}

Object.defineProperty(globalThis, "window", {
  configurable: true,
  value: { __WVI_THEME__: themeApi },
})

describe("IconSet", () => {
  beforeEach(() => {
    currentTheme = "light"
  })

  test("creates readable light and dark icon URLs", () => {
    const allIcons = IconSet.define("AllIcons")

    expect(allIcons.src("expui/breakpoints/breakpoint.svg")).toBe("./__ij-icons/AllIcons/light/expui/breakpoints/breakpoint.svg")
    currentTheme = "dark"
    expect(allIcons.src("expui/breakpoints/breakpoint.svg")).toBe("./__ij-icons/AllIcons/dark/expui/breakpoints/breakpoint.svg")
  })

  test("exports predefined AllIcons icon set", () => {
    expect(AllIcons.id).toBe("AllIcons")
    expect(AllIcons.src("expui/breakpoints/breakpoint.svg")).toBe("./__ij-icons/AllIcons/light/expui/breakpoints/breakpoint.svg")
  })

  test("escapes resource path segments without collapsing separators", () => {
    const pluginIcons = IconSet.define("MyPluginIcons")

    expect(pluginIcons.src("icons/my icon #1.svg")).toBe("./__ij-icons/MyPluginIcons/light/icons/my%20icon%20%231.svg")
  })

  test("rejects invalid icon set ids", () => {
    expectThrownMessage(() => IconSet.define("1AllIcons"), "Invalid WebView icon set id: 1AllIcons")
    expectThrownMessage(() => IconSet.define("All Icons"), "Invalid WebView icon set id: All Icons")
  })

  test("rejects invalid resource paths", () => {
    const allIcons = IconSet.define("AllIcons")

    expectThrownMessage(() => allIcons.src("/expui/breakpoints/breakpoint.svg"), "Invalid WebView icon resource path: /expui/breakpoints/breakpoint.svg")
    expectThrownMessage(() => allIcons.src("http:icon.svg"), "Invalid WebView icon resource path: http:icon.svg")
    expectThrownMessage(() => allIcons.src("expui/../breakpoint.svg"), "Invalid WebView icon resource path: expui/../breakpoint.svg")
    expectThrownMessage(() => allIcons.src("expui//breakpoint.svg"), "Invalid WebView icon resource path: expui//breakpoint.svg")
    expectThrownMessage(() => allIcons.src("expui/breakpoints/breakpoint.gif"), "Unsupported WebView icon resource extension: expui/breakpoints/breakpoint.gif")
  })
})

function expectThrownMessage(action: () => unknown, message: string): void {
  try {
    action()
  }
  catch (error) {
    expect(error instanceof Error ? error.message : String(error)).toBe(message)
    return
  }
  throw new Error(`Expected action to throw: ${message}`)
}
