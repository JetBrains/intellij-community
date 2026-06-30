// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { startWebViewMockPreview, type WebViewMockPreviewServer } from "@jetbrains/intellij-webview-testkit"

type Locator = {
  boundingBox(): Promise<BoundingBox | null>
  click(): Promise<void>
  dispatchEvent(type: string, eventInit?: Record<string, unknown>): Promise<void>
}

type BoundingBox = {
  x: number
  y: number
  width: number
  height: number
}

type Mouse = {
  down(): Promise<void>
  move(x: number, y: number): Promise<void>
  up(): Promise<void>
}

type Page = {
  goto(url: string): Promise<void>
  getByRole(role: string, options?: { name?: string | RegExp; exact?: boolean }): Locator
  locator(selector: string): Locator
  evaluate<Result>(pageFunction: () => Result | Promise<Result>): Promise<Result>
  waitForFunction(pageFunction: () => boolean): Promise<unknown>
  waitForSelector(selector: string): Promise<unknown>
  mouse: Mouse
}

type TestApi = {
  (title: string, handler: (fixtures: { page: Page }) => Promise<void> | void): void
  beforeAll(handler: () => Promise<void> | void): void
  afterAll(handler: () => Promise<void> | void): void
}

type ExpectApi = {
  (actual: Locator): { toBeVisible(): Promise<void> }
  (actual: boolean): { toBe(expected: boolean): void }
}

type PlaywrightTestModule = {
  expect: ExpectApi
  test: TestApi
}

const playwrightTestPackage: string = "@playwright/test"
const { expect, test } = await import(playwrightTestPackage) as unknown as PlaywrightTestModule

const testDir = dirname(fileURLToPath(import.meta.url))
const webviewSrcDir = resolve(testDir, "../..")

let preview: WebViewMockPreviewServer | undefined

test.beforeAll(async () => {
  preview = await startWebViewMockPreview({
    webviewSrcDir,
    viewId: "markdown-preview",
    mock: resolve(webviewSrcDir, "views/markdown-preview/src/markdownPreviewMock.ts"),
  })
})

test.afterAll(async () => {
  await preview?.close()
})

test("zooms, pans, and exposes native resize for Mermaid diagrams", async ({ page }) => {
  if (!preview) {
    throw new Error("Markdown preview mock preview server was not started")
  }
  await page.goto(preview.url)
  await page.waitForSelector(".mermaidBlock svg")

  await expect(page.getByRole("button", { name: "Zoom in diagram" })).toBeVisible()
  expect(await mermaidToolbarIconsLoaded(page)).toBe(true)
  expect(await mermaidHasNoRunGutter(page)).toBe(true)
  const resizeEnabled = await page.evaluate(() => {
    const block = document.querySelector(".mermaidBlock.isInteractive")
    return block != null && getComputedStyle(block).resize === "vertical"
  })
  expect(resizeEnabled).toBe(true)
  expect(await mermaidSvgFillsViewport(page)).toBe(true)

  await page.getByRole("button", { name: "Zoom in diagram" }).click()
  await page.waitForFunction(() => {
    const transform = document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? ""
    return transform.includes("scale(") && !transform.endsWith("scale(1)")
  })

  await page.getByRole("button", { name: "Reset diagram zoom" }).click()
  await page.waitForFunction(() => (document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? "") === "translate(0,0) scale(1)")
  expect(await mermaidViewBoxContainsContent(page)).toBe(true)

  const transformBeforeWheel = await mermaidTransform(page)
  await page.locator(".mermaidViewport svg").dispatchEvent("wheel", { deltaY: -120, clientX: 80, clientY: 80, bubbles: true, cancelable: true })
  expect((await mermaidTransform(page)) === transformBeforeWheel).toBe(true)

  const svg = page.locator(".mermaidViewport svg")
  const box = await svg.boundingBox()
  if (!box) throw new Error("Markdown Mermaid SVG does not have a rendered bounding box")
  const transformBeforeDrag = await mermaidTransform(page)
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2 + 28)
  await page.mouse.up()
  await page.waitForFunction(() => {
    const transform = document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? ""
    return transform.includes("translate(") && !transform.startsWith("translate(0,0)")
  })
  expect((await mermaidTransform(page)) !== transformBeforeDrag).toBe(true)
})

function mermaidTransform(page: Page): Promise<string> {
  return page.evaluate(() => document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? "")
}

function mermaidSvgFillsViewport(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const viewport = document.querySelector(".mermaidViewport")
    const svg = document.querySelector(".mermaidViewport svg")
    if (!viewport || !svg) return false
    return Math.abs(svg.getBoundingClientRect().width - viewport.getBoundingClientRect().width) <= 1
  })
}

function mermaidToolbarIconsLoaded(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const icons = Array.from(document.querySelectorAll<HTMLImageElement>(".mermaidToolbar img"))
    return icons.length === 3 && icons.every(icon => icon.complete && icon.naturalWidth > 0 && icon.naturalHeight > 0)
  })
}

function mermaidHasNoRunGutter(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const block = document.querySelector(".mermaidBlock")
    const hasMermaidRunButton = Array.from(document.querySelectorAll<HTMLButtonElement>(".markdownRunButton"))
      .some(button => button.title.toLowerCase().includes("mermaid"))
    return block != null && block.closest("pre") == null && block.closest(".codeFenceWithCommands") == null && !hasMermaidRunButton
  })
}

function mermaidViewBoxContainsContent(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const svg = document.querySelector<SVGSVGElement>(".mermaidViewport svg")
    const content = document.querySelector<SVGGraphicsElement>(".mermaidPanZoom")
    if (!svg || !content) return false
    const viewBox = svg.viewBox.baseVal
    const box = content.getBBox()
    return viewBox.x <= box.x
      && viewBox.y <= box.y
      && viewBox.x + viewBox.width >= box.x + box.width
      && viewBox.y + viewBox.height >= box.y + box.height
  })
}
