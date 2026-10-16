// The parts of one site that the kernel pages read at run time: the config, the read model, the reference links, the
// fence renderers, the search providers, and the crumbs of the site pages. `createSite` installs them once, before
// the first render. A kernel module reads them through `site()` inside a function, never at module load, because the
// site modules load before `createSite` runs.

import type { ComponentType, ReactNode } from 'react'
import type { HarvestedDiagramStep } from '../scripts/diagram.ts'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import type { RouteCrumb } from './crumbs.ts'
import type { AnyRoute } from './routeTable.ts'
import type { SearchProvider } from './search.ts'
import type { SiteIndex } from './siteData.ts'

/** A link to a site entity, with the title to show. */
export interface RefLink {
  href: string
  title: string
}

/** A place in a source file, relative to `contentRoot`. */
export interface SourceLocation {
  file: string
  line: number
}

/**
 * The links that only a site can resolve. A kernel page asks for them, and shows no link when the site gives none.
 * Each function returns null for an id that the site does not know.
 */
export interface RefLinks {
  /** The route of a doc that a site page shows (a glossary, a home doc), or null for the kernel doc route. */
  docPage?(path: string): string | null
  /** The page of a module, with its display name. */
  moduleLink?(name: string): RefLink | null
  /** The page of a concept, with its title. */
  conceptLink?(id: string): RefLink | null
  /** The page of a spec, with its name. */
  specLink?(id: string): RefLink | null
  /** The module that owns a source file, for the source viewer. */
  sourceModule?(path: string): RefLink | null
  /** The code symbols that inline code and a `[Symbol]` reference link to. */
  symbols?: {
    /** True when `text` can name a symbol. */
    isTarget(text: string): boolean
    /** The declaration of `target`, or null when it is unknown or ambiguous. */
    resolve(target: string, contextFile?: string): SourceLocation | null
  }
}

/** Renders the body of a fenced block, for example ```air-view. */
export type FenceRenderer = (body: string) => ReactNode

/** The leaf crumbs of a site page, after the head crumb of the route table. */
export type CrumbLeaves = (route: AnyRoute) => RouteCrumb[]

/** The texts of the command palette. */
export interface PaletteText {
  title: string
  description: string
  placeholder: string
}

/** The installed parts of the site. */
export interface SiteRuntime {
  config: SiteConfig
  index: SiteIndex
  refLinks: RefLinks
  fenceRenderers: Readonly<Record<string, FenceRenderer>>
  searchProviders: readonly SearchProvider[]
  crumbs: Readonly<Record<string, CrumbLeaves>>
  /** The row under a diagram step that binds to a site entity, or null when the site has none. */
  stepRef: ComponentType<{ step: HarvestedDiagramStep, className?: string }> | null
  palette: PaletteText
}

let installed: SiteRuntime | null = null

/** Installs the parts of the site. `createSite` calls it once. */
export function installSite(runtime: SiteRuntime): void {
  installed = runtime
}

/** The installed parts of the site. */
export function site(): SiteRuntime {
  if (installed === null) {
    throw new Error('The site is not installed. Call createSite before a kernel page renders.')
  }
  return installed
}
