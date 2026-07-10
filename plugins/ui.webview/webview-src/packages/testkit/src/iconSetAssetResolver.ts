// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { existsSync, readFileSync } from "node:fs"
import { dirname, isAbsolute, relative, resolve, sep } from "node:path"

export interface WebViewMockIconSetAssetResolverOptions {
  viewResourceRoot: string
}

export interface WebViewMockIconSetAssetResponse {
  statusCode: number
  contentType?: string
  headers?: Record<string, string>
  body: Buffer | string
}

export function resolveWebViewMockIconSetAsset(
  requestPath: string,
  options: WebViewMockIconSetAssetResolverOptions,
): WebViewMockIconSetAssetResponse | undefined {
  const resourceRoots = iconResourceRoots(options.viewResourceRoot)
  const route = parseIconRoute(requestPath)
  if (!route) return undefined

  if (!route.resourcePath) {
    return forbidden("Invalid icon path")
  }

  const actualResourcePath = route.flavor === "dark"
    ? darkIconResourcePath(resourceRoots, route.resourcePath) ?? route.resourcePath
    : route.resourcePath
  const iconPath = iconResourceFilePath(resourceRoots, actualResourcePath)
  if (!iconPath) {
    return notFound("Icon not found")
  }

  return {
    statusCode: 200,
    contentType: iconContentType(actualResourcePath),
    headers: { "Cache-Control": "no-cache" },
    body: readFileSync(iconPath),
  }
}

interface IconRoute {
  flavor: "light" | "dark"
  resourcePath?: string
}

function parseIconRoute(requestPath: string): IconRoute | undefined {
  const path = requestPath.split("?", 1)[0]?.replace(/^\/+/, "")
  if (path === "__ij-icons") return { flavor: "light" }
  if (!path?.startsWith("__ij-icons/")) return undefined

  const parts = path.split("/", 4)
  if (parts.length < 4 || parts[0] !== "__ij-icons") return { flavor: "light" }
  const [, iconSetId, flavor] = parts
  if (!isValidIconSetId(iconSetId)) return { flavor: "light" }
  if (flavor !== "light" && flavor !== "dark") return { flavor: "light" }

  const encodedResourcePath = path.substring(`__ij-icons/${iconSetId}/${flavor}/`.length)
  const resourcePath = decodeIconResourcePath(encodedResourcePath)
  return { flavor, resourcePath }
}

function iconResourceRoots(viewResourceRoot: string): string[] {
  const resolvedViewResourceRoot = resolve(viewResourceRoot)
  return Array.from(new Set([
    resolvedViewResourceRoot,
    resolve(resolvedViewResourceRoot, "../../.."),
    ...platformIconResourceRoots(resolvedViewResourceRoot),
  ]))
}

function platformIconResourceRoots(startPath: string): string[] {
  const roots: string[] = []
  let current = resolve(startPath)
  while (true) {
    addExistingRoot(roots, resolve(current, "platform/icons/src"))
    addExistingRoot(roots, resolve(current, "community/platform/icons/src"))

    const parent = dirname(current)
    if (parent === current) return roots
    current = parent
  }
}

function addExistingRoot(roots: string[], candidate: string): void {
  if (existsSync(candidate)) roots.push(candidate)
}

function decodeIconResourcePath(encodedPath: string): string | undefined {
  let resourcePath: string
  try {
    resourcePath = encodedPath.split("/").map(segment => decodeURIComponent(segment)).join("/")
  }
  catch {
    return undefined
  }
  if (!resourcePath.endsWith(".svg") && !resourcePath.endsWith(".png")) return undefined
  if (resourcePath.startsWith("/") || resourcePath.includes("\\")) return undefined
  if (resourcePath.split("/").some(segment => !segment || segment === "." || segment === "..")) return undefined
  return resourcePath
}

function darkIconResourcePath(resourceRoots: readonly string[], resourcePath: string): string | undefined {
  const extensionStart = resourcePath.lastIndexOf(".")
  const candidate = `${resourcePath.substring(0, extensionStart)}_dark${resourcePath.substring(extensionStart)}`
  return iconResourceFilePath(resourceRoots, candidate) ? candidate : undefined
}

function iconResourceFilePath(resourceRoots: readonly string[], resourcePath: string): string | undefined {
  for (const resourceRoot of resourceRoots) {
    const candidatePath = resolve(resourceRoot, resourcePath)
    if (isInsideResourceRoot(resourceRoot, candidatePath) && existsSync(candidatePath)) return candidatePath
  }
  return undefined
}

function isInsideResourceRoot(resourceRoot: string, path: string): boolean {
  const relativePath = relative(resourceRoot, path)
  return relativePath === "" || (relativePath !== ".." && !relativePath.startsWith(`..${sep}`) && !isAbsolute(relativePath))
}

function isValidIconSetId(iconSetId: string): boolean {
  return /^[A-Za-z][A-Za-z0-9._-]*$/.test(iconSetId)
}

function iconContentType(path: string): string {
  return path.endsWith(".svg") ? "image/svg+xml" : "image/png"
}

function forbidden(message: string): WebViewMockIconSetAssetResponse {
  return { statusCode: 403, body: message }
}

function notFound(message: string): WebViewMockIconSetAssetResponse {
  return { statusCode: 404, body: message }
}
