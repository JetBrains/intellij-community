// The single route → breadcrumb-trail model. The head crumb comes from the route table (parentOf in router.ts). The
// leaves add the data-driven middle and leaf crumbs: the kernel gives the leaves of its pages, and a site gives the
// leaves of its own pages through `createSite`. Leaves are human titles (or the raw identifier where that IS the
// name, such as a path); raw ids only survive as mono fallbacks when a lookup fails, so "Unknown X" error pages
// still get a sane trail.
//
// The shell shows no in-page crumb on a canvas route, and an index page returns []: the top nav already names the
// section.

import { adrNumberedTitle } from '../scripts/docMeta.ts'
import { repoPathOf } from '../scripts/sourcePaths.ts'
import { catalogAnchor, guidesRoute, hrefForRoute, parentOf } from './router'
import type { AnyRoute, KernelRoute } from './routeTable.ts'
import { site } from './site.ts'

export interface RouteCrumb {
  label: string
  /** Absent on the leaf (current page) and on label-only heads (Source has no index page). */
  href?: string
  /** Monospace — the label is a code-like identifier (module name, EP FQN, flag key, path). */
  mono?: boolean
}

type KernelPage = KernelRoute['page']
type KernelRouteOf<P extends KernelPage> = Extract<KernelRoute, { page: P }>

/** The crumbs after the head, per kernel page. A page with no entry has no trail. */
const LEAVES: { [P in KernelPage]?: (route: KernelRouteOf<P>) => RouteCrumb[] } = {
  'guide': route => {
    const doc = site().index.docForGuideSlug(route.slug)
    return [doc ? { label: doc.title } : { label: route.slug, mono: true }]
  },
  'explainer': route => {
    const doc = site().index.docForExplainerSlug(route.slug)
    return [doc ? { label: doc.title } : { label: route.slug, mono: true }]
  },
  'decision': route => {
    const doc = site().index.docForDecisionSlug(route.slug)
    return [doc ? { label: adrNumberedTitle(doc.adr, doc.title) } : { label: route.slug, mono: true }]
  },
  'doc': route => {
    const doc = site().index.docsByPath.get(route.path)
    // the label and the anchor match the Working notes section of the Guides index (GuidesPage)
    const group = doc?.kind === 'note'
      ? { label: 'Working notes', href: guidesRoute(catalogAnchor('working-notes')) }
      : null
    return [
      ...group ? [group] : [],
      doc ? { label: doc.title } : { label: route.path, mono: true },
    ]
  },
  'source': route => [{ label: repoPathOf(site().config.contentRoot, route.path), mono: true }],
}

export function crumbsForRoute(route: AnyRoute): RouteCrumb[] {
  // the page selects the leaves; TypeScript cannot correlate the lookup with the narrowed route
  const leaves = site().crumbs[route.page] ?? (LEAVES as Record<string, ((route: AnyRoute) => RouteCrumb[]) | undefined>)[route.page]
  if (leaves === undefined) {
    return []
  }
  return [...headCrumbs(route), ...leaves(route)]
}

/** The head crumb of the route: its section label, linked to the section index when it has one. */
export function headCrumbs(route: AnyRoute): RouteCrumb[] {
  const parent = parentOf(route)
  if (parent === null) {
    return []
  }
  return [parent.route === undefined ? { label: parent.label } : { label: parent.label, href: hrefForRoute(parent.route) }]
}
