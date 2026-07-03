// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { startWebViewMockPreview, type WebViewMockPreviewServer } from "@jetbrains/intellij-webview-testkit"

type Locator = {
  boundingBox(): Promise<BoundingBox | null>
  click(): Promise<void>
  dispatchEvent(type: string, eventInit?: Record<string, unknown>): Promise<void>
  scrollIntoViewIfNeeded(): Promise<void>
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

type Route = {
  fulfill(options: { body: string; contentType: string; status?: number }): Promise<void>
}

type ConsoleMessage = {
  type(): string
  text(): string
}

type Page = {
  goto(url: string): Promise<void>
  getByRole(role: string, options?: { name?: string | RegExp; exact?: boolean }): Locator
  locator(selector: string): Locator
  route(url: string | RegExp, handler: (route: Route) => Promise<void> | void): Promise<void>
  evaluate<Result>(pageFunction: () => Result | Promise<Result>): Promise<Result>
  on(event: "console", handler: (message: ConsoleMessage) => void): void
  on(event: "pageerror", handler: (error: Error) => void): void
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
const mockMarkdownPreviewImageSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="480" viewBox="0 0 960 480"><rect width="960" height="480" fill="#f7f8fa"/><rect x="72" y="72" width="816" height="336" rx="24" fill="#ffffff" stroke="#3871e1" stroke-width="12"/><path d="M144 384l168-168 120 120 96-96 288 228H144z" fill="#6db083"/><circle cx="690" cy="174" r="72" fill="#f4b400"/></svg>`

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

  const transformBeforeDrag = await mermaidTransform(page)
  await dragLocatorCenter(page, page.locator(".mermaidViewport svg"), 40, 28, "Markdown Mermaid SVG does not have a rendered bounding box")
  await page.waitForFunction(() => {
    const transform = document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? ""
    return transform.includes("translate(") && !transform.startsWith("translate(0,0)")
  })
  expect((await mermaidTransform(page)) !== transformBeforeDrag).toBe(true)
})

test("zooms standalone image blocks without changing inline or linked images", async ({ page }) => {
  if (!preview) {
    throw new Error("Markdown preview mock preview server was not started")
  }
  await page.route(/.*\/__markdown-preview-resource\/.*/, route => route.fulfill({ contentType: "image/svg+xml", body: mockMarkdownPreviewImageSvg }))
  await page.goto(preview.url)
  await page.waitForSelector(".markdownImageBlock img")
  await page.waitForFunction(() => (document.querySelector<HTMLElement>(".markdownImage")?.style.transform ?? "") === "translate(0px, 0px) scale(1)")
  await page.waitForFunction(() => document.querySelector<HTMLImageElement>(".markdownImageViewport img")?.naturalWidth === 960)

  await expect(page.getByRole("button", { name: "Zoom in image" })).toBeVisible()
  expect(await markdownImageToolbarIconsLoaded(page)).toBe(true)
  const imageState = await page.evaluate(() => {
    const block = document.querySelector(".markdownImageBlock.isInteractive")
    const inlineImage = document.querySelector('img[alt="Inline preview"]')
    const linkedImage = document.querySelector('a img[alt="Linked preview"]')
    return {
      resizeEnabled: block != null && getComputedStyle(block).resize === "vertical",
      inlineHasZoomBlock: inlineImage?.closest(".markdownImageBlock") != null,
      linkedHasZoomBlock: linkedImage?.closest(".markdownImageBlock") != null,
      imageToolbarCount: document.querySelectorAll('[aria-label="Image zoom controls"]').length,
    }
  })
  expect(imageState.resizeEnabled).toBe(true)
  expect(imageState.inlineHasZoomBlock).toBe(false)
  expect(imageState.linkedHasZoomBlock).toBe(false)
  expect(imageState.imageToolbarCount === 1).toBe(true)
  await page.waitForFunction(() => {
    const block = document.querySelector(".markdownImageBlock")
    const image = document.querySelector<HTMLImageElement>(".markdownImageViewport img")
    if (!block || !image || image.naturalWidth <= 0 || image.naturalHeight <= 0) return false
    const box = block.getBoundingClientRect()
    const expectedHeight = box.width * image.naturalHeight / image.naturalWidth
    return Math.abs(box.height - expectedHeight) <= 1
  })
  expect(await markdownImageFillsViewport(page)).toBe(true)

  await page.getByRole("button", { name: "Zoom in image" }).click()
  await page.waitForFunction(() => {
    const transform = document.querySelector<HTMLElement>(".markdownImage")?.style.transform ?? ""
    return transform.includes("scale(") && !transform.endsWith("scale(1)")
  })

  await page.getByRole("button", { name: "Reset image zoom" }).click()
  await page.waitForFunction(() => (document.querySelector<HTMLElement>(".markdownImage")?.style.transform ?? "") === "translate(0px, 0px) scale(1)")

  const transformBeforeWheel = await markdownImageTransform(page)
  await page.locator(".markdownImageViewport img").dispatchEvent("wheel", { deltaY: -120, clientX: 80, clientY: 80, bubbles: true, cancelable: true })
  expect((await markdownImageTransform(page)) === transformBeforeWheel).toBe(true)

  const transformBeforeDrag = await markdownImageTransform(page)
  await dragLocatorCenter(page, page.locator(".markdownImageViewport img"), 40, 28, "Markdown image does not have a rendered bounding box")
  await page.waitForFunction(() => {
    const transform = document.querySelector<HTMLElement>(".markdownImage")?.style.transform ?? ""
    return transform.includes("translate(") && !transform.startsWith("translate(0px, 0px)")
  })
  expect((await markdownImageTransform(page)) !== transformBeforeDrag).toBe(true)
})

test("renders only host-resolved code paths as navigation buttons", async ({ page }) => {
  if (!preview) {
    throw new Error("Markdown preview mock preview server was not started")
  }
  await page.goto(preview.url)
  await page.waitForSelector(".markdownPathLink")

  const pathLinkState = await page.evaluate(() => {
    const labels = Array.from(document.querySelectorAll<HTMLButtonElement>(".markdownPathLink"))
      .map(button => button.textContent ?? "")
    const mermaidBlock = document.querySelector(".mermaidBlock")
    return {
      labels,
      hasMissingPathButton: labels.includes("missing/Nope.kt"),
      hasMermaidPathButton: mermaidBlock?.querySelector(".markdownPathLink") != null,
    }
  })

  expect(pathLinkState.labels.includes("docs/guide.md:12")).toBe(true)
  expect(pathLinkState.labels.includes("src/Main.kt")).toBe(true)
  expect(pathLinkState.labels.includes("src\\WindowsPath.kt")).toBe(true)
  expect(pathLinkState.labels.includes("index.html")).toBe(true)
  expect(pathLinkState.labels.includes("index.h")).toBe(false)
  expect(pathLinkState.labels.includes("my_django_app/")).toBe(true)
  expect(pathLinkState.labels.includes("_app/")).toBe(false)
  expect(pathLinkState.labels.includes("style.css")).toBe(true)
  expect(pathLinkState.labels.includes("requirements.txt")).toBe(true)
  expect(pathLinkState.hasMissingPathButton).toBe(false)
  expect(pathLinkState.hasMermaidPathButton).toBe(false)

  await page.getByRole("button", { name: "docs/guide.md:12", exact: true }).click()
  await page.waitForFunction(() => document.querySelector("#mock-run-log")?.textContent?.includes("navigatePathLink") ?? false)
  const navigation = await page.evaluate(() => JSON.parse(document.querySelector("#mock-run-log")?.textContent ?? "{}") as {
    rawPath?: string
    clientX?: number
    clientY?: number
  })

  expect(navigation.rawPath === "docs/guide.md:12").toBe(true)
  expect(Number.isFinite(navigation.clientX) && Number.isFinite(navigation.clientY)).toBe(true)
})

test("updates after KaTeX rendering without React DOM commit errors", async ({ page }) => {
  if (!preview) {
    throw new Error("Markdown preview mock preview server was not started")
  }
  const errors: string[] = []
  page.on("pageerror", error => errors.push(error.message))
  page.on("console", message => {
    if (message.type() === "error") {
      errors.push(message.text())
    }
  })

  await page.goto(preview.url)
  await page.waitForSelector(".katex")
  await page.waitForSelector(".markdownPathLink")
  await page.getByRole("button", { name: "Toggle theme" }).click()
  await page.waitForFunction(() => document.querySelector(".katex") != null && document.querySelector(".markdownPathLink") != null)

  expect(errors.length === 0).toBe(true)
})

function mermaidTransform(page: Page): Promise<string> {
  return page.evaluate(() => document.querySelector(".mermaidPanZoom")?.getAttribute("transform") ?? "")
}

async function dragLocatorCenter(page: Page, locator: Locator, deltaX: number, deltaY: number, missingBoxMessage: string): Promise<void> {
  await locator.scrollIntoViewIfNeeded()
  const box = await locator.boundingBox()
  if (!box) throw new Error(missingBoxMessage)
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + deltaX, box.y + box.height / 2 + deltaY)
  await page.mouse.up()
}

function markdownImageTransform(page: Page): Promise<string> {
  return page.evaluate(() => document.querySelector<HTMLElement>(".markdownImage")?.style.transform ?? "")
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

function markdownImageToolbarIconsLoaded(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const icons = Array.from(document.querySelectorAll<HTMLImageElement>(".markdownImageToolbar img"))
    return icons.length === 3 && icons.every(icon => icon.complete && icon.naturalWidth > 0 && icon.naturalHeight > 0)
  })
}

function markdownImageFillsViewport(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const viewport = document.querySelector(".markdownImageViewport")
    const image = document.querySelector(".markdownImageViewport img")
    if (!viewport || !image) return false
    const viewportBox = viewport.getBoundingClientRect()
    const imageBox = image.getBoundingClientRect()
    return Math.abs(imageBox.width - viewportBox.width) <= 1 && Math.abs(imageBox.height - viewportBox.height) <= 1
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
