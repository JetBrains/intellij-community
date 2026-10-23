// The app routes of a docs site, bound to the deploy base. The URL rules live in one table, routeTable.ts, which
// `createSite` installs with the route extensions of the site. This module keeps the named builders of the kernel
// pages, and the browser navigation. A site keeps the builders of its own pages and calls `hrefForRoute` here.

import type { SiteConfig } from '../scripts/siteConfig.ts'
import {
  decisionPaths,
  siteRouteTable,
  stripBase,
  type AnyRoute,
  type DecisionPaths,
  type DecisionStatusFilter,
  type KernelRoute,
  type LaunchRoute,
  type PageLayout,
  type RouteExtension,
  type RouteParent,
  type RouteTable,
  type SourceLineSelection,
} from './routeTable.ts'

export { catalogAnchor, isCatalogAnchor } from './routeTable.ts'
export type { AnyRoute, DecisionStatusFilter, KernelRoute, LaunchRoute, PageLayout, RouteParent, SourceLineSelection } from './routeTable.ts'

/** The event that `navigate` sends. The shell reads the new route on it. */
export const ROUTE_CHANGE_EVENT = 'docs-site-route-change'

interface InstalledRoutes {
  table: RouteTable
  basePrefix: string
  heads: Set<string>
  decisions: DecisionPaths
}

let installed: InstalledRoutes | null = null

/** Installs the route table of the site. `createSite` calls it once, before the first render. */
export function installRoutes(config: Pick<SiteConfig, 'base' | 'docs' | 'launchPicker'>, extensions: readonly RouteExtension[]): void {
  const basePath = config.base.endsWith('/') ? config.base : `${config.base}/`
  const table = siteRouteTable(config, extensions)
  installed = {
    table,
    basePrefix: basePath === '/' ? '' : basePath.slice(0, -1),
    heads: new Set(table.heads()),
    decisions: decisionPaths(config.docs.decisions),
  }
}

function routes(): InstalledRoutes {
  if (installed === null) {
    throw new Error('The site routes are not installed. Call createSite before a route is built or parsed.')
  }
  return installed
}

export function parseRoute(input: string): AnyRoute {
  const { table, basePrefix } = routes()
  return table.parse(input, basePrefix)
}

export function hrefForRoute(route: AnyRoute): string {
  const { table, basePrefix } = routes()
  return table.build(route, basePrefix)
}

/** The top-nav section that marks the route as active, or null when no nav item owns the page. */
export function sectionOf(route: AnyRoute): string | null {
  return routes().table.entryFor(route).section
}

/** The layout of the page of the route. */
export function layoutOf(route: AnyRoute): PageLayout {
  return routes().table.entryFor(route).layout
}

/** The head crumb of the route, or null for a page with no section head. */
export function parentOf(route: AnyRoute): RouteParent | null {
  return routes().table.entryFor(route).parent
}

/** True for the content-relative path of a decision record. */
export function isDecisionPath(path: string): boolean {
  return routes().decisions.isDecisionPath(path)
}

/** The `decision` route slug of a decision record path: the file name without `.md`. */
export function decisionSlug(path: string): string {
  return routes().decisions.decisionSlug(path)
}

function kernelHref(route: KernelRoute): string {
  return hrefForRoute(route)
}

// --- builders ------------------------------------------------------------------------------------

/** The Home page of the site. */
export function homeRoute(): string {
  return kernelHref({ page: 'home' })
}

export function guideRoute(slug: string, anchor?: string): string {
  return kernelHref({ page: 'guide', slug, anchor: anchor ?? null })
}

/** Guides-index route; `anchor` must be a `group-` catalog anchor (guide slugs never are). */
export function guidesRoute(anchor?: string): string {
  return kernelHref({ page: 'guides', anchor: anchor ?? null })
}

/** The explainers index (ADR 0203 of Air). */
export function explainersRoute(anchor?: string): string {
  return kernelHref({ page: 'explainers', anchor: anchor ?? null })
}

export interface ExplainerRouteOptions {
  /** The 1-based step of the chapter's diagram; null for the overview. */
  step?: number | null
  /** The id of the selected diagram node, for the detail card. */
  node?: string | null
}

/** One explainer at a chapter; no chapter opens the first one. */
export function explainerRoute(slug: string, chapter: string | null = null, options: ExplainerRouteOptions = {}): string {
  return kernelHref({ page: 'explainer', slug, chapter, step: options.step ?? null, node: options.node ?? null })
}

/** Source-viewer route for a content-relative file, optionally pinned to inclusive 1-based lines. */
export function sourceRoute(path: string, selection?: SourceLineSelection | number | null): string {
  return kernelHref({
    page: 'source',
    path,
    selection: typeof selection === 'number' ? { start: selection, end: selection } : selection ?? null,
  })
}

export interface DecisionsRouteOptions {
  status?: DecisionStatusFilter | null
  topic?: string | null
}

/** The Decisions index; `topic` null hides the records of the hidden topic, `all` shows every topic. */
export function decisionsRoute(anchor?: string, options: DecisionsRouteOptions = {}): string {
  return kernelHref({ page: 'decisions', anchor: anchor ?? null, status: options.status ?? null, topic: options.topic ?? null })
}

/** One decision record by its content-relative path. */
export function decisionRoute(path: string, anchor?: string): string {
  return kernelHref({ page: 'decision', slug: decisionSlug(path), anchor: anchor ?? null })
}

export function docRoute(path: string, anchor?: string): string {
  return kernelHref({ page: 'doc', path, anchor: anchor ?? null })
}

/** The launch picker, with the search text `query`. Only a site with a launch picker has the route. */
export function launchRoute(query?: string): string {
  const route: LaunchRoute = { page: 'launch', q: query ?? null }
  return hrefForRoute(route)
}

// --- links and navigation ------------------------------------------------------------------------

export function legacyHashHref(hash: string): string | null {
  return hash.startsWith('#/') ? hrefForRoute(parseRoute(hash)) : null
}

/** Base-aware app route normalizer for markdown-authored links. */
export function normalizeMarkdownAppHref(href: string): string | null {
  if (href.startsWith('#/')) {
    return legacyHashHref(href)
  }
  if (!href.startsWith('/')) {
    return null
  }
  const { basePrefix, heads } = routes()
  const url = new URL(href, 'http://docs-site.local')
  const path = stripBase(url.pathname, basePrefix)
  const [head] = path.split('/').filter(Boolean)
  if (head !== undefined && !heads.has(head)) {
    return null
  }
  return hrefForRoute(parseRoute(`${path}${url.search}`))
}

export function isAppHref(href: string): boolean {
  if (href.startsWith('#/')) {
    return true
  }
  if (href.startsWith('#')) {
    return false
  }
  const url = new URL(href, window.location.href)
  if (url.origin !== window.location.origin) {
    return false
  }
  return isAppPathHref(`${url.pathname}${url.search}`)
}

export function isAppPathHref(href: string): boolean {
  if (!href.startsWith('/')) {
    return false
  }
  const { basePrefix } = routes()
  const url = new URL(href, 'http://docs-site.local')
  return basePrefix === ''
    ? url.pathname.startsWith('/')
    : url.pathname === basePrefix || url.pathname.startsWith(`${basePrefix}/`)
}

export function navigate(href: string, options: { replace?: boolean } = {}): void {
  const target = href.startsWith('#/') ? hrefForRoute(parseRoute(href)) : href
  const url = new URL(target, window.location.href)
  const next = `${url.pathname}${url.search}`
  const current = `${window.location.pathname}${window.location.search}`
  if (next !== current || window.location.hash) {
    if (options.replace) {
      window.history.replaceState(null, '', next)
    }
    else {
      window.history.pushState(null, '', next)
    }
  }
  window.dispatchEvent(new Event(ROUTE_CHANGE_EVENT))
}
