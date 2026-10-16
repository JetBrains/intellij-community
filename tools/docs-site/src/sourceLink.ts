// The single home of the GitHub source-link template. A site gives the repository base and the path of its content
// root in the site config (`github`). Line links point at master and may drift as code moves — they are convenience
// links, not stable references.

import type { SourceLineSelection } from './routeTable.ts'
import { site } from './site.ts'

function githubLineFragment(selection: SourceLineSelection | number | null | undefined): string {
  if (selection == null) {
    return ''
  }
  if (typeof selection === 'number') {
    return selection > 0 ? `#L${selection}` : ''
  }
  return selection.start === selection.end ? `#L${selection.start}` : `#L${selection.start}-L${selection.end}`
}

/** GitHub URL for a content-relative file, optionally pinned to inclusive 1-based lines. */
export function githubUrl(relFile: string, selection?: SourceLineSelection | number | null): string {
  const { repoBase, pathPrefix } = site().config.github
  return `${repoBase}${pathPrefix}${relFile}${githubLineFragment(selection)}`
}

/** GitHub URL for a repo-root-relative file (spec test targets may leave the content root). */
export function githubRepoUrl(repoFile: string, line?: number | null): string {
  return `${site().config.github.repoBase}${repoFile}${line ? `#L${line}` : ''}`
}

/** GitHub URL for a content-relative directory. */
export function githubDirUrl(relDir: string): string {
  const { repoBase, pathPrefix } = site().config.github
  // a GitHub directory URL takes `tree` where a file URL takes `blob`
  return `${repoBase.replace('/blob/', '/tree/')}${pathPrefix}${relDir}`
}
