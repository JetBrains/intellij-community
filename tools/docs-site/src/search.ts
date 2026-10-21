// Command-palette search across everything the site knows. Each search provider owns some result kinds. The kernel
// gives the providers of the docs, the specs, and the code symbols, and a site puts its own providers around them.
// The provider order is the result order and the group order of the palette.

import { ADR_STATUSES, adrLabel, adrNumberedTitle } from '../scripts/docMeta.ts'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import { decisionRoute, docRoute, explainerRoute, guideRoute, launchRoute } from './router'
import { site } from './site.ts'
import { guideSlug } from './siteData.ts'
import { sourceHref } from './sourceRef'

export interface SearchResult {
  ref: string
  /** The palette group, for example `Guide`. */
  kind: string
  title: string
  detail: string
  route: string
  /** External link (GitHub source) — opens in a new tab. */
  external?: boolean
}

/** One source of search results. */
export interface SearchProvider {
  /** The result kinds, in palette group order. */
  kinds: readonly string[]
  /** The results for `needle`, a lowercase query of two characters or more. */
  search(needle: string): SearchResult[]
}

/** The explainers, the guides, the decision records, and the other bundled docs. */
export const docSearch: SearchProvider = {
  kinds: ['Explainer', 'Guide', 'Decision', 'Doc'],
  search(needle) {
    const results: SearchResult[] = []
    const docsRoot = `${site().config.docs.root}/`
    for (const doc of site().index.data.docs) {
      if (doc.kind !== 'guide' && doc.kind !== 'note' && doc.kind !== 'adr' && doc.kind !== 'explainer') {
        continue
      }
      if (doc.adr) {
        const bare = needle.replace(/^adr\s*/, '')
        const byNumber = doc.adr.number !== null && (bare === doc.adr.number || bare === String(Number(doc.adr.number)))
        if (byNumber || `${doc.title} ${doc.path}`.toLowerCase().includes(needle)) {
          results.push({
            ref: `doc:${doc.path}`,
            kind: 'Decision',
            title: adrNumberedTitle(doc.adr, doc.title),
            detail: `${adrLabel(doc.adr)} · ${ADR_STATUSES.find(status => status.id === doc.adr?.status)?.label ?? doc.adr.status}`,
            route: decisionRoute(doc.path),
          })
        }
        continue
      }
      if (doc.kind === 'explainer') {
        const chapters = doc.chapters ?? []
        const chapter = chapters.find(candidate => candidate.title.toLowerCase().includes(needle)) ?? null
        if (chapter !== null || `${doc.title} ${doc.description} ${doc.path}`.toLowerCase().includes(needle)) {
          results.push({
            ref: `doc:${doc.path}`,
            kind: 'Explainer',
            title: chapter === null ? doc.title : `${doc.title}: ${chapter.title}`,
            detail: doc.description || doc.path,
            route: explainerRoute(guideSlug(doc.path), chapter?.id ?? null),
          })
        }
        continue
      }
      if (`${doc.title} ${doc.description} ${doc.path}`.toLowerCase().includes(needle)) {
        const isGuidePath = doc.path.startsWith(docsRoot) && !doc.path.includes('/decisions/')
        results.push({
          ref: `doc:${doc.path}`,
          kind: doc.kind === 'guide' ? 'Guide' : 'Doc',
          title: doc.title,
          detail: doc.description || doc.path,
          route: isGuidePath ? guideRoute(guideSlug(doc.path)) : docRoute(doc.path),
        })
      }
    }
    return results
  },
}

/** The specs that the site links (RefLinks.specLink). */
export const specSearch: SearchProvider = {
  kinds: ['Spec'],
  search(needle) {
    const { config, index, refLinks } = site()
    const results: SearchResult[] = []
    for (const spec of index.data.specs) {
      const link = refLinks.specLink?.(spec.id) ?? null
      if (link !== null && `${spec.name} ${spec.id} ${spec.description}`.toLowerCase().includes(needle)) {
        results.push({
          ref: `spec:${spec.id}`,
          kind: 'Spec',
          title: spec.name,
          detail: spec.path.endsWith(config.specs.suffix) ? spec.path.slice(0, -config.specs.suffix.length) : spec.path,
          route: link.href,
        })
      }
    }
    return results
  },
}

/** The harvested code symbols, linked to the source viewer or to GitHub. */
export const symbolSearch: SearchProvider = {
  kinds: ['Symbol'],
  search(needle) {
    const results: SearchResult[] = []
    for (const symbol of site().index.data.symbols) {
      if (symbol.name.toLowerCase().includes(needle) || symbol.fqn.toLowerCase().includes(needle)) {
        const { href, external } = sourceHref(symbol.file, symbol.line)
        results.push({
          ref: `symbol:${symbol.fqn}`,
          kind: 'Symbol',
          title: symbol.name,
          detail: symbol.file,
          route: href,
          external,
        })
      }
    }
    return results
  },
}

/** The launch targets of the launch picker. A result opens the picker with the name of the target as the search text. */
export const launchSearch: SearchProvider = {
  kinds: ['Launch'],
  search(needle) {
    const { config, index } = site()
    if (config.launchPicker === null) {
      return []
    }
    return (index.data.launch ?? [])
      .filter(entry => `${entry.name} ${entry.target}`.toLowerCase().includes(needle))
      .map(entry => ({ ref: `launch:${entry.target}`, kind: 'Launch', title: entry.name, detail: entry.target, route: launchRoute(entry.name) }))
  },
}

/** The providers of a site that names none and has no launch picker. */
export const KERNEL_SEARCH_PROVIDERS: readonly SearchProvider[] = [docSearch, specSearch, symbolSearch]

/** The providers of a site that names none: the launch targets come after the specs when the site has a launch picker. */
export function kernelSearchProviders(config: Pick<SiteConfig, 'launchPicker'>): readonly SearchProvider[] {
  return config.launchPicker === null ? KERNEL_SEARCH_PROVIDERS : [docSearch, specSearch, launchSearch, symbolSearch]
}

/** The result kinds of the site, in palette group order. */
export function searchKinds(): string[] {
  return [...new Set(site().searchProviders.flatMap(provider => provider.kinds))]
}

export function searchSite(query: string, limit = 16): SearchResult[] {
  const needle = query.trim().toLowerCase()
  if (needle.length < 2) {
    return []
  }
  return site().searchProviders.flatMap(provider => provider.search(needle)).slice(0, limit)
}
