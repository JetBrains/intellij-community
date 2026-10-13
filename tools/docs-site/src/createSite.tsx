// The entry of a docs site: `createSite(config, parts)` installs the routes and the parts of the site, sets the first
// theme, and mounts the shell into `#root`. A site keeps its own home page, nav, routes, pages, fence renderers,
// reference links, and search providers; the kernel gives the shell and the generic pages.
//
// The site imports its CSS entry and the kernel styles in its own `main.tsx` before it calls this function, so the
// cascade order stays in the site's hands.

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import type { CrumbLeaves, FenceRenderer, PaletteText, RefLinks, SiteRuntime } from './site.ts'
import { installSite } from './site.ts'
import { installRoutes } from './router'
import type { RouteExtension } from './routeTable.ts'
import { kernelSearchProviders, type SearchProvider } from './search'
import { Shell, type ShellParts } from './Shell'
import { siteIndex, type SiteData } from './siteData.ts'
import { initTheme } from './theme'

/** The parts of a site. */
export interface SiteParts extends ShellParts {
  /** The routes of the site pages. They come before the kernel routes in the route table. */
  routes: readonly RouteExtension[]
  /** The crumb leaves of the site pages, by route page. */
  crumbs?: Readonly<Record<string, CrumbLeaves>>
  /** The renderers of the site fences, by fence name. The kernel renders ```diagram itself. */
  fenceRenderers?: Readonly<Record<string, FenceRenderer>>
  /** The links that only the site can resolve. */
  refLinks?: RefLinks
  /**
   * The search providers, in palette order. The default is the kernel providers of the docs, the specs, the launch
   * targets when the site has a launch picker, and the symbols.
   */
  searchProviders?: readonly SearchProvider[]
  /** The row under a diagram step that binds to a site entity. */
  stepRef?: SiteRuntime['stepRef']
  /** The texts of the command palette. */
  palette?: PaletteText
  /** The read model of the kernel pages. */
  data: SiteData
}

const DEFAULT_PALETTE: PaletteText = {
  title: 'Search the docs',
  description: 'Search guides, explainers, decisions, specs, and symbols',
  placeholder: 'Search guides, explainers, decisions, specs, symbols…',
}

/**
 * Installs the site and mounts its shell into `#root`. The launch picker is on only when the config names one and the
 * site data has launch entries.
 */
export function createSite(siteConfig: SiteConfig, parts: SiteParts): void {
  const config = siteConfig.launchPicker !== null && (parts.data.launch?.length ?? 0) === 0 ? { ...siteConfig, launchPicker: null } : siteConfig
  installRoutes(config, parts.routes)
  installSite({
    config,
    index: siteIndex(parts.data, config),
    refLinks: parts.refLinks ?? {},
    fenceRenderers: parts.fenceRenderers ?? {},
    searchProviders: parts.searchProviders ?? kernelSearchProviders(config),
    crumbs: parts.crumbs ?? {},
    stepRef: parts.stepRef ?? null,
    palette: parts.palette ?? DEFAULT_PALETTE,
  })
  initTheme(config.themeStorageKey)
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Shell config={config} parts={parts} />
    </StrictMode>,
  )
}
