// Source-path helpers that the harvest (node) and the site (browser) share. All paths POSIX.
//
// Two path spaces exist: most references stay inside the content root of the site ("content-relative", the form
// stored in the harvested data; `contentRoot` of the site config, `plugins/air` for Air), but spec [@test] targets
// may climb out to other repo parts (community/platform, ...). Resolution therefore happens in repo-root space;
// `contentPathOf` recovers the content-relative form when a repo path is inside the content root.

/**
 * Resolves `relativePath` against a content-relative `baseDir`, returning a repo-root-relative
 * path (`plugins/air/<module>/...` or `community/...` for Air). `..` clamps at the repo root; both observed
 * spec spellings (`../../<module>/…` and `../../../../plugins/air/<module>/…`) normalize to
 * the same result.
 */
export function resolveRepoPath(contentRoot: string, baseDir: string, relativePath: string): string {
  const parts = [...contentRoot.split('/').filter(part => part !== '' && part !== '.'), ...(baseDir === '' ? [] : baseDir.split('/'))]
  for (const segment of relativePath.split('/')) {
    if (segment === '..') {
      parts.pop()
    }
    else if (segment !== '.' && segment !== '') {
      parts.push(segment)
    }
  }
  return parts.join('/')
}

/** The content-relative form of a repo-root-relative path, or null when the path is outside the content root. */
export function contentPathOf(contentRoot: string, repoPath: string): string | null {
  const prefix = contentRoot === '' || contentRoot === '.' ? '' : `${contentRoot}/`
  return repoPath.startsWith(prefix) ? repoPath.slice(prefix.length) : null
}

/**
 * A `[@test]` line of a spec: the indent, the path, and the optional names of the tests in parentheses, each in
 * backticks, for example `[@test] a/ATest.kt (`a test`, `another test`)`.
 */
export const TEST_LINK_LINE = /^([ \t]*)\[@test][ \t]+(\S+)(?:[ \t]+\(((?:`[^`]+`(?:,[ \t]*)?)+)\))?[ \t]*$/gm

/** The test names of the parenthesized part of a `[@test]` line, in order. */
export function testLinkNames(names: string | undefined): string[] {
  return names === undefined ? [] : [...names.matchAll(/`([^`]+)`/g)].map(match => match[1])
}

/**
 * The repo-root-relative form of a `targets` entry or a `[@test]` path of a spec. With `rootPaths`, as in an OpenSpec
 * root, the path is from the repository root already. Otherwise it is relative to the content-relative folder of the
 * spec.
 */
export function resolveSpecPath(contentRoot: string, specDir: string, rootPaths: boolean, path: string): string {
  return rootPaths ? resolveRepoPath('', '', path) : resolveRepoPath(contentRoot, specDir, path)
}

/**
 * The `[@test]` lines of a spec body, as repo-root-relative paths, unique and sorted. A path resolves against the
 * content-relative folder of the spec, or from the repository root with `rootPaths`. `accept` filters a target, for
 * example the harvest drops a missing file.
 */
export function specTestFiles(
  contentRoot: string,
  specDir: string,
  body: string,
  accept: (repoPath: string, target: string) => boolean = () => true,
  rootPaths: boolean = false,
): string[] {
  const files = new Set<string>()
  for (const match of body.matchAll(TEST_LINK_LINE)) {
    const repoPath = resolveSpecPath(contentRoot, specDir, rootPaths, match[2])
    if (accept(repoPath, match[2])) {
      files.add(repoPath)
    }
  }
  return [...files].sort()
}

/** The file name of a spec under an OpenSpec root: `<root>/specs/<capability>/spec.md`. */
export const OPENSPEC_SPEC_FILE = 'spec.md'

/** The part of `SiteConfig.specs` that a spec id needs. */
export interface SpecRootsConfig {
  roots: string[]
  suffix: string
  openspec?: { roots: string[] }
}

/**
 * The spec id of a repo-root-relative path, or null when no spec root holds it. Under an OpenSpec root the id is the
 * capability, the folder path under `specs/`, for example `cache` or `sessions/local-launch-socket`. Under a suffix
 * root the id is the path below the first root that holds the file, without the suffix.
 */
export function specIdOfRepoPath(specs: SpecRootsConfig, repoPath: string): string | null {
  for (const root of specs.openspec?.roots ?? []) {
    const prefix = `${root}/specs/`
    if (repoPath.startsWith(prefix) && repoPath.length > prefix.length + OPENSPEC_SPEC_FILE.length && repoPath.endsWith(`/${OPENSPEC_SPEC_FILE}`)) {
      return repoPath.slice(prefix.length, -OPENSPEC_SPEC_FILE.length - 1)
    }
  }
  if (!repoPath.endsWith(specs.suffix)) {
    return null
  }
  for (const root of specs.roots) {
    const prefix = root === '' ? '' : `${root}/`
    if (repoPath.startsWith(prefix)) {
      return repoPath.slice(prefix.length, -specs.suffix.length)
    }
  }
  return null
}

/** True when the repo-root-relative path is a spec of an OpenSpec root, whose paths are from the repository root. */
export function isOpenSpecPath(specs: SpecRootsConfig, repoPath: string): boolean {
  return (specs.openspec?.roots ?? []).some(root => repoPath.startsWith(`${root}/specs/`) && repoPath.endsWith(`/${OPENSPEC_SPEC_FILE}`))
}

/**
 * The group of an OpenSpec requirement heading `Requirement: <Group>: <behaviour>`: the text between the prefix and
 * the first `: `, or null for another heading or a requirement without a group. A link to `#<group slug>` names the
 * first requirement of the group, so the group keeps the anchor of the old group heading.
 */
export function requirementGroup(heading: string): string | null {
  const match = /^Requirement:\s+(.+?):\s/.exec(heading.trim())
  return match === null ? null : match[1].trim()
}

/** The repo-root-relative form of a content-relative path. A content root of `''` or `.` is the repository root. */
export function repoPathOf(contentRoot: string, contentPath: string): string {
  return contentRoot === '' || contentRoot === '.' ? contentPath : `${contentRoot}/${contentPath}`
}

const EXTENSION_LANGS: Record<string, string> = {
  kt: 'kotlin',
  kts: 'kotlin',
  java: 'java',
  ts: 'typescript',
  tsx: 'tsx',
  mjs: 'javascript',
  js: 'javascript',
  xml: 'xml',
  iml: 'xml',
  md: 'markdown',
  json: 'json',
  properties: 'properties',
  yaml: 'yaml',
  yml: 'yaml',
  css: 'css',
}

/** Shiki language id for a file path, or null when the extension has no grammar mapping. */
export function languageForPath(path: string): string | null {
  return EXTENSION_LANGS[path.slice(path.lastIndexOf('.') + 1)] ?? null
}

/** Matches relative-path-looking text that may reference a source or doc file. */
export const SOURCE_PATH_PATTERN = /^[\w./-]+\.(?:md|kt|kts|java|ts|tsx|mjs|js|xml|iml|properties|json|ya?ml|css|bzl|bazel|rs|cmd)(#[\w-]+)?$/
