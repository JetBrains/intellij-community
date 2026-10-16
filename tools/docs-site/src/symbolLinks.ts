// Client-side symbol linking: resolves KDoc `[Symbol]` references and inline-code mentions through the symbol
// resolver of the site (RefLinks.symbols), and links them to their source. A site without a resolver links none.

import { site, type SourceLocation } from './site.ts'
import { sourceHref } from './sourceRef'

/** Resolves a symbol-ish text (simple name, `Type.member`, FQN; trailing `()`/`?` ignored). */
export function resolveSymbolText(text: string, contextFile?: string): SourceLocation | null {
  const symbols = site().refLinks.symbols
  const target = text.replace(/[()?!]+$/, '').trim()
  if (symbols === undefined || !symbols.isTarget(target)) {
    return null
  }
  return symbols.resolve(target, contextFile)
}

/**
 * Rewrites KDoc `[Symbol]` / `[text][Symbol]` references in markdown into source links
 * before rendering; unresolved references degrade to inline code. Regular markdown links,
 * images, and link definitions are left alone.
 */
export function linkKdocRefs(markdown: string, contextFile?: string): string {
  const symbols = site().refLinks.symbols
  if (symbols === undefined) {
    return markdown
  }
  return markdown.replace(/(!?)\[([^\]\n]+)](\[([^\]\n]*)]|[(:])?/g, (match, bang, text: string, tail: string | undefined, refTarget: string | undefined) => {
    if (bang === '!' || tail === '(' || tail === ':') {
      return match
    }
    const target = (refTarget || text).trim()
    if (!symbols.isTarget(target)) {
      return match
    }
    const location = symbols.resolve(target, contextFile)
    return location
      ? `[\`${text}\`](${sourceHref(location.file, location.line).href})`
      : `\`${text}\``
  })
}
