// The one place deciding where a source reference leads: the in-site viewer when the file is
// bundled (the `sources` of the site data), GitHub otherwise. The viewer itself offers "Open on GitHub",
// so routing in-site never loses anything.

import { contentPathOf } from '../scripts/sourcePaths.ts'
import { sourceRoute } from './router'
import { site } from './site.ts'
import { githubRepoUrl, githubUrl } from './sourceLink'
import { isBundledSource } from './sources'

export interface SourceHref {
  href: string
  /** True when the href leaves the site (GitHub) and should open in a new tab. */
  external: boolean
}

/** Href for a content-relative source file — most harvested locations. */
export function sourceHref(file: string, line?: number | null): SourceHref {
  return isBundledSource(file)
    ? { href: sourceRoute(file, line), external: false }
    : { href: githubUrl(file, line), external: true }
}

/** Href for a repo-root-relative source file — spec test targets that may leave the content root. */
export function repoSourceHref(repoFile: string, line?: number | null): SourceHref {
  const contentPath = contentPathOf(site().config.contentRoot, repoFile)
  return contentPath
    ? sourceHref(contentPath, line)
    : { href: githubRepoUrl(repoFile, line), external: true }
}
