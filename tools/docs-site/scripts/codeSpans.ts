// The code spans of a doc that name a path. The markdown renderer links such a span, and resolves it against the
// folder of its doc first, because a doc names a file next to it that way. A span that names no file there resolves
// against the content root, and then against the repository root, such as `build/dev-build.json` in a guide or the
// command `./build/jpsModelToBazel.cmd`. A `../` span is doc-relative by its form, so it resolves against the doc
// folder only. A span that names no file at all, such as `META-INF/plugin.xml` inside a jar, stays code.
//
// The browser cannot test a file for existence, so the harvest resolves those spans and writes them to the
// `CODE_SPAN_PATHS_FILE` of the generated directory of the site. A span that the doc folder resolves has no entry.
//
// A file that git ignores counts as absent, such as a file under `out/`. Such a file is not a source, and the content
// image does not hold it. So the answer does not depend on the build outputs of a checkout.
//
// Node and bun run this file, and the browser reads its types, so it imports nothing from npm.

import { spawnSync } from 'node:child_process'
import { existsSync, lstatSync } from 'node:fs'
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

/** Tells whether a repository-relative path names a source file. */
export type IsSourceFile = (repoPath: string) => boolean

/** The path spans of `body`, before `#`, in the order of the doc. A span in a fenced block is code. */
function pathSpans(body: string): string[] {
  return [...withoutFencedBlocks(body).matchAll(CODE_SPAN)]
    .filter(match => SOURCE_PATH_PATTERN.test(match[1]))
    .map(match => match[1].split('#')[0])
}

/** The repository paths that a span can name, in the order of the resolution: the doc folder, the content root, the repository root. */
function spanCandidates(contentRoot: string, baseDir: string, path: string): { local: string, others: string[] } {
  const local = resolveRepoPath(contentRoot, baseDir, path)
  return { local, others: path.startsWith('../') ? [] : [resolveRepoPath(contentRoot, '', path), resolveRepoPath('', '', path)] }
}

/**
 * The path spans of `body` that name no file from the folder of the doc, each with the repository path of the file
 * that it names from the content root or from the repository root, or with null. A span that starts with `../` names
 * a file from the doc folder only, so it is null when that file does not exist.
 */
export function unresolvedCodeSpans(contentRoot: string, docPath: string, body: string, isSourceFile: IsSourceFile): Map<string, string | null> {
  const baseDir = docFolder(docPath)
  const spans = new Map<string, string | null>()
  for (const path of pathSpans(body)) {
    const { local, others } = spanCandidates(contentRoot, baseDir, path)
    if (spans.has(path) || isSourceFile(local)) {
      continue
    }
    spans.set(path, others.find(isSourceFile) ?? null)
  }
  return spans
}

/** Adds the unresolved spans of one doc to `paths`. */
export function addCodeSpanPaths(paths: Map<string, Map<string, string | null>>, contentRoot: string, docPath: string, body: string, isSourceFile: IsSourceFile): void {
  const spans = unresolvedCodeSpans(contentRoot, docPath, body, isSourceFile)
  if (spans.size === 0) {
    return
  }
  const folder = docFolder(docPath)
  paths.set(folder, new Map([...paths.get(folder) ?? [], ...spans]))
}

/**
 * The path that git answers for `path`: its first ancestor that is a symbolic link, or the path itself. Git refuses a
 * path beyond a link, and the link has the answer for each file below it.
 */
function gitQueryPath(root: string, path: string, links: Map<string, boolean>): string {
  const segments = path.split('/')
  for (let count = 1; count < segments.length; count++) {
    const ancestor = segments.slice(0, count).join('/')
    let link = links.get(ancestor)
    if (link === undefined) {
      try {
        link = lstatSync(join(root, ancestor)).isSymbolicLink()
      }
      catch {
        link = false
      }
      links.set(ancestor, link)
    }
    if (link) {
      return ancestor
    }
  }
  return path
}

/**
 * The paths of `paths` that git ignores in the work tree at `root`. A tracked file is never ignored. A path below a
 * symbolic link takes the answer of the link. Without git, or outside a work tree, the set is empty, as in the content
 * image, which holds no ignored file. Any other failure of git throws with its message.
 */
export function gitIgnoredPaths(root: string, paths: readonly string[]): Set<string> {
  if (paths.length === 0) {
    return new Set()
  }
  const links = new Map<string, boolean>()
  const queries = new Map(paths.map(path => [path, gitQueryPath(root, path, links)]))
  const result = spawnSync('git', ['-C', root, 'check-ignore', '-z', '--stdin'], {
    input: [...new Set(queries.values())].join('\0'),
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  })
  if (result.error !== undefined) {
    if ((result.error as NodeJS.ErrnoException).code === 'ENOENT') {
      return new Set()
    }
    throw new Error(`git check-ignore in ${root}: ${result.error.message}`)
  }
  if (result.status === 128 && /not a git repository/.test(result.stderr)) {
    return new Set()
  }
  // 0: some paths are ignored, 1: none is ignored. A message with 1 is a failure, such as the git shim of macOS
  // without the developer tools.
  if ((result.status !== 0 && result.status !== 1) || (result.status === 1 && result.stderr.trim() !== '')) {
    throw new Error(`git check-ignore in ${root} failed with the status ${result.status}: ${result.stderr.trim()}`)
  }
  const ignored = new Set(result.stdout.split('\0').filter(path => path.length > 0))
  return new Set(paths.filter(path => ignored.has(queries.get(path) ?? path)))
}

/**
 * The code-span paths of the docs in `bodies`, by doc path. A file counts when it exists in the repository at `root`,
 * and git does not ignore it. One git call answers for every span of the harvest.
 */
export function resolveCodeSpanPaths(contentRoot: string, bodies: ReadonlyMap<string, string>, root: string): Map<string, Map<string, string | null>> {
  const existing = new Set<string>()
  for (const [docPath, body] of bodies) {
    const baseDir = docFolder(docPath)
    for (const path of pathSpans(body)) {
      const { local, others } = spanCandidates(contentRoot, baseDir, path)
      for (const candidate of [local, ...others]) {
        if (!existing.has(candidate) && existsSync(join(root, candidate))) {
          existing.add(candidate)
        }
      }
    }
  }
  const ignored = gitIgnoredPaths(root, [...existing])
  const isSourceFile: IsSourceFile = repoPath => existing.has(repoPath) && !ignored.has(repoPath)
  const paths = new Map<string, Map<string, string | null>>()
  for (const [docPath, body] of bodies) {
    addCodeSpanPaths(paths, contentRoot, docPath, body, isSourceFile)
  }
  return paths
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
