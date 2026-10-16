// Picture references in a bundled markdown body. The harvest copies each referenced picture under
// generated/assets/<content-relative path>, and the site maps it to a hashed URL (src/docAssets.ts of Air).
// Shared by scripts/harvestDocs.ts (node) and the markdown renderer of a site (browser), so it stays plain TypeScript.

import { contentPathOf, resolveRepoPath } from './sourcePaths.ts'

export interface PictureReference {
  /** The picture source as the markdown writes it. */
  source: string
  /** The content-relative path of the picture, or null when the source leaves the content root. */
  path: string | null
  /** The picture title, which the site shows as the caption, or null. */
  title: string | null
  /** The 1-based line of the reference in the body. */
  line: number
}

const FENCE = /^[ \t]*(`{3,}|~{3,})/
const CODE_SPAN = /(`+).*?\1/g
// `![alt](source "title")`: the source can sit in angle brackets, and the title in double or single quotes
const PICTURE = /!\[[^\]\n]*]\(\s*(<[^>\n]*>|[^\s)]+)(?:\s+("[^"\n]*"|'[^'\n]*'))?\s*\)/g

/** True when a picture source is a URL (`https:`, `data:`, `//host`), not a file of the repository. */
export function isExternalPicture(source: string): boolean {
  return /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(source)
}

/** The content-relative path of a local picture source, resolved from `baseDir`; null when it leaves `contentRoot`. */
export function picturePath(contentRoot: string, baseDir: string, source: string): string | null {
  if (source.startsWith('/')) {
    return null
  }
  let decoded: string
  try {
    decoded = decodeURIComponent(source)
  }
  catch {
    return null
  }
  return contentPathOf(contentRoot, resolveRepoPath(contentRoot, baseDir, decoded))
}

/** The lines of a markdown body with each fenced block and each code span removed; the line numbers stay. */
function linesWithoutCode(body: string): string[] {
  let fence: string | null = null
  return body.split('\n').map(line => {
    const marker = FENCE.exec(line)?.[1]
    if (fence !== null) {
      if (marker !== undefined && marker[0] === fence[0] && marker.length >= fence.length && line.trim() === marker) {
        fence = null
      }
      return ''
    }
    if (marker !== undefined) {
      fence = marker
      return ''
    }
    return line.replace(CODE_SPAN, '')
  })
}

/**
 * The local pictures that a markdown body references as `![alt](source "title")`, resolved from the
 * content-relative `baseDir` of the body. A URL source is skipped, and code is never read.
 */
export function collectPictureReferences(contentRoot: string, body: string, baseDir: string): PictureReference[] {
  const references: PictureReference[] = []
  for (const [index, line] of linesWithoutCode(body).entries()) {
    for (const match of line.matchAll(PICTURE)) {
      const source = match[1].startsWith('<') ? match[1].slice(1, -1) : match[1]
      if (isExternalPicture(source)) {
        continue
      }
      references.push({
        source,
        path: picturePath(contentRoot, baseDir, source),
        title: match[2] === undefined ? null : match[2].slice(1, -1),
        line: index + 1,
      })
    }
  }
  return references
}
