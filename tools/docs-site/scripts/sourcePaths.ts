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
 * The `[@test] <relative-path>` lines of a spec body, resolved against the content-relative folder of the spec: the
 * repo-root-relative paths, unique and sorted. `accept` filters a target, for example the harvest drops a missing file.
 */
export function specTestFiles(
  contentRoot: string,
  specDir: string,
  body: string,
  accept: (repoPath: string, target: string) => boolean = () => true,
): string[] {
  const files = new Set<string>()
  for (const match of body.matchAll(/^[ \t]*\[@test][ \t]+(\S+)[ \t]*$/gm)) {
    const repoPath = resolveRepoPath(contentRoot, specDir, match[1])
    if (accept(repoPath, match[1])) {
      files.add(repoPath)
    }
  }
  return [...files].sort()
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
