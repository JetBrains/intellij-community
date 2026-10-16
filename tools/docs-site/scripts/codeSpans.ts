// The code spans of a doc that name a path. The markdown renderer links such a span, and resolves it against the
// folder of its doc first, because a doc names a file next to it that way. A span that names no file there resolves
// against the content root, and then against the repository root, such as `build/dev-build.json` in a guide or the
// command `./build/jpsModelToBazel.cmd`. A `../` span is doc-relative by its form, so it resolves against the doc
// folder only. A span that names no file at all, such as `META-INF/plugin.xml` inside a jar, stays code.
//
// The browser cannot test a file for existence, so the harvest resolves those spans and writes them to the
// `CODE_SPAN_PATHS_FILE` of the generated directory of the site. A span that the doc folder resolves has no entry.
//
// Node and bun run this file, and the browser reads its types, so it imports nothing from npm.

import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { withoutFencedBlocks } from './fence.ts'
import { resolveRepoPath, SOURCE_PATH_PATTERN } from './sourcePaths.ts'

/** The name of the file under `generated/` that holds the {@link CodeSpanPaths} of a site. */
export const CODE_SPAN_PATHS_FILE = 'code-span-paths.json'

/**
 * The code-span paths that the doc folder does not resolve, by the content-relative folder of the doc. Each key is
 * the text of a span before `#`. Each value is the repository-relative path that the span names, or null when the
 * span names no file.
 */
export type CodeSpanPaths = Record<string, Record<string, string | null>>

const CODE_SPAN = /`([^`\n]+)`/g

/** The content-relative folder of a content-relative doc path. */
export function docFolder(path: string): string {
  return path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
}

/**
 * The path spans of `body` that name no file from the folder of the doc, each with the repository path of the file
 * that it names from the content root or from the repository root, or with null. A span that starts with `../` names
 * a file from the doc folder only, so it is null when that file does not exist.
 */
export function unresolvedCodeSpans(contentRoot: string, docPath: string, body: string, root: string): Map<string, string | null> {
  const baseDir = docFolder(docPath)
  const spans = new Map<string, string | null>()
  for (const match of withoutFencedBlocks(body).matchAll(CODE_SPAN)) {
    if (!SOURCE_PATH_PATTERN.test(match[1])) {
      continue
    }
    const path = match[1].split('#')[0]
    if (spans.has(path) || existsSync(join(root, resolveRepoPath(contentRoot, baseDir, path)))) {
      continue
    }
    const candidates = path.startsWith('../') ? [] : [resolveRepoPath(contentRoot, '', path), resolveRepoPath('', '', path)]
    const named = candidates.find(candidate => existsSync(join(root, candidate)))
    spans.set(path, named ?? null)
  }
  return spans
}

/** Adds the unresolved spans of one doc to `paths`. */
export function addCodeSpanPaths(paths: Map<string, Map<string, string | null>>, contentRoot: string, docPath: string, body: string, root: string): void {
  const spans = unresolvedCodeSpans(contentRoot, docPath, body, root)
  if (spans.size === 0) {
    return
  }
  const folder = docFolder(docPath)
  paths.set(folder, new Map([...paths.get(folder) ?? [], ...spans]))
}

function byKey<T>([a]: [string, T], [b]: [string, T]): number {
  return a < b ? -1 : a > b ? 1 : 0
}

/** The JSON form of `paths`: the folders and the spans in sorted order, so a second harvest writes the same bytes. */
export function codeSpanPathsJson(...harvests: readonly ReadonlyMap<string, ReadonlyMap<string, string | null>>[]): string {
  const merged = new Map<string, Map<string, string | null>>()
  for (const paths of harvests) {
    for (const [folder, spans] of paths) {
      merged.set(folder, new Map([...merged.get(folder) ?? [], ...spans]))
    }
  }
  const json: CodeSpanPaths = Object.fromEntries([...merged].sort(byKey).map(([folder, spans]) => [folder, Object.fromEntries([...spans].sort(byKey))]))
  return JSON.stringify(json)
}
