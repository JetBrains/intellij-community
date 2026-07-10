// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { expect, test } from "bun:test"
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { resolveWebViewMockIconSetAsset } from "./iconSetAssetResolver"

const tempDirs: string[] = []

test("resolves full classpath icon resources and dark variants", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()
    writeIcon(viewResourceRoot, "sampleIcon.svg", "light-icon")
    writeIcon(viewResourceRoot, "sampleIcon_dark.svg", "dark-icon")

    const lightResponse = resolveWebViewMockIconSetAsset(
      "/__ij-icons/SampleIcons/light/webview/views/sample-view/assets/sampleIcon.svg",
      { viewResourceRoot },
    )
    const darkResponse = resolveWebViewMockIconSetAsset(
      "/__ij-icons/SampleIcons/dark/webview/views/sample-view/assets/sampleIcon.svg",
      { viewResourceRoot },
    )

    expect(lightResponse?.statusCode).toBe(200)
    expect(lightResponse?.contentType).toBe("image/svg+xml")
    expect(lightResponse?.body.toString()).toBe("light-icon")
    expect(darkResponse?.statusCode).toBe(200)
    expect(darkResponse?.body.toString()).toBe("dark-icon")
  }
  finally {
    cleanupTempDirs()
  }
})

test("resolves view-relative icon resources and png content type", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()
    writeIcon(viewResourceRoot, "avatar.png", "png-bytes")

    const response = resolveWebViewMockIconSetAsset(
      "/__ij-icons/SampleIcons/light/assets/avatar.png?cache=ignored",
      { viewResourceRoot },
    )

    expect(response?.statusCode).toBe(200)
    expect(response?.contentType).toBe("image/png")
    expect(response?.body.toString()).toBe("png-bytes")
  }
  finally {
    cleanupTempDirs()
  }
})

test("falls back to light icon when dark resource is missing", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()
    writeIcon(viewResourceRoot, "lightOnly.svg", "light-only")

    const response = resolveWebViewMockIconSetAsset(
      "/__ij-icons/SampleIcons/dark/webview/views/sample-view/assets/lightOnly.svg",
      { viewResourceRoot },
    )

    expect(response?.statusCode).toBe(200)
    expect(response?.body.toString()).toBe("light-only")
  }
  finally {
    cleanupTempDirs()
  }
})

test("resolves platform AllIcons resources from checkout roots", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()
    writeResource(viewResourceRoot, "../../../../community/platform/icons/src/graph/zoomIn.svg", "platform-zoom-in")

    const response = resolveWebViewMockIconSetAsset(
      "/__ij-icons/AllIcons/light/graph/zoomIn.svg",
      { viewResourceRoot },
    )

    expect(response?.statusCode).toBe(200)
    expect(response?.contentType).toBe("image/svg+xml")
    expect(response?.body.toString()).toBe("platform-zoom-in")
  }
  finally {
    cleanupTempDirs()
  }
})

test("returns forbidden for malformed icon requests", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()
    const invalidRequests = [
      "/__ij-icons",
      "/__ij-icons/1Bad/light/assets/icon.svg",
      "/__ij-icons/SampleIcons/contrast/assets/icon.svg",
      "/__ij-icons/SampleIcons/light/assets//icon.svg",
      "/__ij-icons/SampleIcons/light/assets/%2E%2E/icon.svg",
      "/__ij-icons/SampleIcons/light/assets/icon.gif",
    ]

    for (const request of invalidRequests) {
      const response = resolveWebViewMockIconSetAsset(request, { viewResourceRoot })
      expect(response?.statusCode).toBe(403)
    }
  }
  finally {
    cleanupTempDirs()
  }
})

test("returns not found for missing icons and ignores non-icon routes", () => {
  try {
    const viewResourceRoot = createTempViewResourceRoot()

    expect(resolveWebViewMockIconSetAsset("/view.js", { viewResourceRoot })).toBeUndefined()
    expect(resolveWebViewMockIconSetAsset(
      "/__ij-icons/SampleIcons/light/webview/views/sample-view/assets/missing.svg",
      { viewResourceRoot },
    )?.statusCode).toBe(404)
  }
  finally {
    cleanupTempDirs()
  }
})

function createTempViewResourceRoot(): string {
  const tempDir = mkdtempSync(join(tmpdir(), "webview-icon-assets-"))
  tempDirs.push(tempDir)
  const viewResourceRoot = join(tempDir, "resources", "webview", "views", "sample-view")
  mkdirSync(join(viewResourceRoot, "assets"), { recursive: true })
  return viewResourceRoot
}

function writeIcon(viewResourceRoot: string, name: string, content: string): void {
  writeResource(viewResourceRoot, join("assets", name), content)
}

function writeResource(viewResourceRoot: string, resourcePath: string, content: string): void {
  const path = join(viewResourceRoot, resourcePath)
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, content)
}

function cleanupTempDirs(): void {
  for (const dir of tempDirs.splice(0)) {
    rmSync(dir, { force: true, recursive: true })
  }
}
