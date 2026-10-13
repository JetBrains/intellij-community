// Where a path-like code span of a doc links, and the text of the link. The module is pure, so a node test loads it.
// The rule of the folders is in scripts/codeSpans.ts.

import { contentPathOf, resolveRepoPath } from '../scripts/sourcePaths.ts'

export interface CodeSpanTarget {
  /** The repo-root-relative path that the span names. */
  repoPath: string
  /** The content-relative path, or null when the path is outside the content root. */
  contentPath: string | null
  /** The part after `#`, or undefined. */
  fragment: string | undefined
  /** The text of a code link: the span text when the doc folder does not resolve it, else the resolved path. */
  label: string
}

/**
 * The target of the code span `text` of a doc in the content-relative folder `baseDir`, or null when the span names
 * no file. `unresolved` are the spans of that folder that the harvest resolved elsewhere (`CodeSpanPaths` of
 * scripts/codeSpans.ts). A span with no entry resolves against `baseDir`.
 */
export function codeSpanTarget(
  text: string,
  baseDir: string,
  contentRoot: string,
  unresolved: Readonly<Record<string, string | null>> | undefined,
): CodeSpanTarget | null {
  const [pathPart, fragment] = text.split('#')
  if (unresolved !== undefined && Object.hasOwn(unresolved, pathPart)) {
    const repoPath = unresolved[pathPart]
    return repoPath === null ? null : { repoPath, contentPath: contentPathOf(contentRoot, repoPath), fragment, label: text }
  }
  const repoPath = resolveRepoPath(contentRoot, baseDir, pathPart)
  return { repoPath, contentPath: contentPathOf(contentRoot, repoPath), fragment, label: fragment ? `${repoPath}#${fragment}` : repoPath }
}
