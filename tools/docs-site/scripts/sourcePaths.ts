// Source-path helpers that the Air harvest (node) and the Air site (browser) share. All paths POSIX.
//
// Two path spaces exist: most references stay inside plugins/air ("air-relative", the form
// stored in arch-data.json), but spec [@test] targets may climb out to other repo parts
// (community/platform, ...). Resolution therefore happens in repo-root space; `airPathOf`
// recovers the air-relative form when a repo path is inside the plugin.

export const AIR_PREFIX = 'plugins/air/'

/**
 * Resolves `relativePath` against an air-relative `baseDir`, returning a repo-root-relative
 * path (`plugins/air/<module>/...` or `community/...`). `..` clamps at the repo root; both observed
 * spec spellings (`../../<module>/…` and `../../../../plugins/air/<module>/…`) normalize to
 * the same result.
 */
export function resolveRepoPath(baseDir: string, relativePath: string): string {
  const parts = ['plugins', 'air', ...(baseDir === '' ? [] : baseDir.split('/'))]
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

/** plugins/air-relative form of a repo-root-relative path, or null when outside the plugin. */
export function airPathOf(repoPath: string): string | null {
  return repoPath.startsWith(AIR_PREFIX) ? repoPath.slice(AIR_PREFIX.length) : null
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
export const SOURCE_PATH_PATTERN = /^[\w./-]+\.(?:md|kt|kts|java|ts|tsx|mjs|js|xml|iml|properties|json|ya?ml|css)(#[\w-]+)?$/
