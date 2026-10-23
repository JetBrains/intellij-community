// The app shell of a docs site: the top bar on the window (the brand, the top nav, the mobile nav, the palette button,
// the theme toggle), and the route outlet with the islands of the page layout (pageLayout.tsx). The kernel renders its
// own pages; a site gives its home page and the renderers of its own pages. Every routed page is a lazy chunk: only
// the shell (nav, palette, furniture, data) loads eagerly, so the entry stays small and shared modules split into
// common chunks.

import { SearchIcon } from 'lucide-react'
import { lazy, Suspense, useLayoutEffect, useRef, useState, type ComponentType, type MouseEvent, type ReactNode } from 'react'
import { flushSync } from 'react-dom'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import { Island } from './components/Island'
import { MobileNav } from './components/MobileNav'
import { ThemeToggle } from './components/ThemeToggle'
import { TopNav } from './components/TopNav'
import { Kbd } from './components/ui/kbd'
import { TooltipProvider } from './components/ui/tooltip'
import { crumbsForRoute } from './crumbs'
import { DocPage as DocPageShell } from './furniture'
import type { NavEntry } from './nav'
import { PageChromeProvider, ReadingLayout, ToolLayout, type PageChrome } from './pageLayout'
import { CommandPalette, usePalette } from './palette/CommandPalette'
import {
  homeRoute,
  hrefForRoute,
  isAppHref,
  layoutOf,
  legacyHashHref,
  navigate,
  parseRoute,
  ROUTE_CHANGE_EVENT,
} from './router'
import type { AnyRoute, KernelRoute, LaunchRoute } from './routeTable.ts'
import { KERNEL_SECTION_TREES, sectionTreeOf, type SectionTreeSource } from './sectionTree'

/** The component a page module exports under `name`, as a lazy chunk. */
export function lazyPage<Module extends Record<Name, ComponentType<any>>, Name extends keyof Module & string>(
  load: () => Promise<Module>,
  name: Name,
) {
  return lazy(() => load().then(module => ({ default: module[name] })))
}

const SourcePage = lazy(() => import('./pages/SourcePage'))
const DecisionPage = lazyPage(() => import('./pages/DecisionPage'), 'DecisionPage')
const DecisionsPage = lazyPage(() => import('./pages/DecisionsPage'), 'DecisionsPage')
const DocPage = lazyPage(() => import('./pages/DocPage'), 'DocPage')
const GuidePage = lazyPage(() => import('./pages/GuidePage'), 'GuidePage')
const ExplainerPage = lazyPage(() => import('./pages/ExplainerPage'), 'ExplainerPage')
const ExplainersPage = lazyPage(() => import('./pages/ExplainersPage'), 'ExplainersPage')
const GuidesPage = lazyPage(() => import('./pages/GuidesPage'), 'GuidesPage')
const LaunchPage = lazyPage(() => import('./pages/LaunchPage'), 'LaunchPage')

/** Renders the page of one route. */
export type PageRenderer<R extends AnyRoute = any> = (route: R) => ReactNode

/** The parts of a site that the shell renders. */
export interface ShellParts {
  /** The home page of the site. The kernel never dictates it. */
  home: ComponentType
  /** The nav table: the items one click away and the dropdown groups. */
  nav: readonly NavEntry[]
  /** The renderers of the site pages, by route page. The layout of a page comes from its entry in the route table. */
  pages: Readonly<Record<string, PageRenderer>>
  /**
   * The section trees of the site sections, by route page: the left island of a reading page. They come before the
   * kernel trees of the guides and the decisions. A reading page without a tree keeps an empty column.
   */
  sectionTrees?: Readonly<Record<string, SectionTreeSource>>
  /**
   * The part of a route that names the page; a change of it cross-fades, a step or a selection does not. Return
   * undefined for the default: the explainer slug, else the path of the route URL.
   */
  pageIdentity?: (route: AnyRoute) => string | undefined
}

type KernelRouteOf<P extends KernelRoute['page'] | LaunchRoute['page']> = Extract<KernelRoute | LaunchRoute, { page: P }>

/** One renderer per kernel page; the mapped type makes a new kernel route a compile error until it gets one. */
const KERNEL_PAGES: { [P in Exclude<KernelRoute['page'] | LaunchRoute['page'], 'home'>]: (route: KernelRouteOf<P>) => ReactNode } = {
  'guides': route => <GuidesPage anchor={route.anchor} />,
  'guide': route => <GuidePage slug={route.slug} anchor={route.anchor} />,
  'explainers': route => <ExplainersPage anchor={route.anchor} />,
  'explainer': route => <ExplainerPage slug={route.slug} chapter={route.chapter} step={route.step} node={route.node} />,
  'decisions': route => <DecisionsPage anchor={route.anchor} status={route.status} topic={route.topic} />,
  'decision': route => <DecisionPage slug={route.slug} anchor={route.anchor} />,
  'source': route => (
    <Suspense fallback={<DocPageShell className="text-muted-foreground">Loading source…</DocPageShell>}>
      <SourcePage path={route.path} selection={route.selection} />
    </Suspense>
  ),
  'doc': route => <DocPage path={route.path} anchor={route.anchor} />,
  'launch': route => <LaunchPage query={route.q} />,
}

function currentRoute(): AnyRoute {
  const legacyHref = legacyHashHref(window.location.hash)
  if (legacyHref) {
    window.history.replaceState(null, '', legacyHref)
    return parseRoute(legacyHref)
  }
  return parseRoute(`${window.location.pathname}${window.location.search}`)
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * The routed state. A change to another page cross-fades through a view transition, where the browser has
 * them and the user did not ask for reduced motion.
 */
function useBrowserRoute(pageIdentity: (route: AnyRoute) => string): AnyRoute {
  const [route, setRoute] = useState<AnyRoute>(() => currentRoute())
  const shown = useRef(route)
  useLayoutEffect(() => {
    const onRouteChange = () => {
      const next = currentRoute()
      const animate = typeof document.startViewTransition === 'function'
        && pageIdentity(next) !== pageIdentity(shown.current)
        && !prefersReducedMotion()
      shown.current = next
      if (animate) {
        document.startViewTransition(() => flushSync(() => setRoute(next)))
      }
      else {
        setRoute(next)
      }
    }
    window.addEventListener('popstate', onRouteChange)
    window.addEventListener('hashchange', onRouteChange)
    window.addEventListener(ROUTE_CHANGE_EVENT, onRouteChange)
    return () => {
      window.removeEventListener('popstate', onRouteChange)
      window.removeEventListener('hashchange', onRouteChange)
      window.removeEventListener(ROUTE_CHANGE_EVENT, onRouteChange)
    }
  }, [pageIdentity])
  return route
}

function handleAppClick(event: MouseEvent<HTMLElement>): void {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey) {
    return
  }
  const link = (event.target as HTMLElement).closest('a')
  const href = link?.getAttribute('href') ?? ''
  if (!link || href === '' || link.hasAttribute('download')) {
    return
  }
  const target = link.getAttribute('target')
  if (target && target !== '_self') {
    return
  }
  if (!isAppHref(href)) {
    return
  }
  event.preventDefault()
  navigate(href)
}

export function Shell({ config, parts }: { config: SiteConfig, parts: ShellParts }) {
  const [identity] = useState(() => (route: AnyRoute): string => {
    const custom = parts.pageIdentity?.(route)
    if (custom !== undefined) {
      return custom
    }
    // an explainer is one canvas page; a chapter change moves inside it
    if (route.page === 'explainer') {
      return `explainer:${(route as KernelRouteOf<'explainer'>).slug}`
    }
    return new URL(hrefForRoute(route), 'http://docs-site.local').pathname
  })
  const route = useBrowserRoute(identity)
  const [paletteOpen, setPaletteOpen] = usePalette()
  const Home = parts.home
  const layout = layoutOf(route)
  const treeSource = layout === 'reading' ? parts.sectionTrees?.[route.page] ?? KERNEL_SECTION_TREES[route.page] : undefined
  const chrome: PageChrome = {
    layout,
    crumbs: layout === 'canvas' ? [] : crumbsForRoute(route),
    tree: treeSource === undefined ? null : sectionTreeOf(treeSource),
    href: hrefForRoute(route),
  }
  const renderPage = (): ReactNode => {
    if (route.page === 'home') {
      return <Home />
    }
    const page = parts.pages[route.page] ?? (KERNEL_PAGES as Record<string, PageRenderer | undefined>)[route.page]
    if (page === undefined) {
      throw new Error(`No page renders the route ${JSON.stringify(route)}`)
    }
    return page(route)
  }
  // the fallback must not carry marker classes (.doc-page etc.): the e2e readiness helpers select on them and would
  // take the fallback for the routed page
  const routed = (
    <Suspense fallback={<Island className="route-loading p-10 text-muted-foreground">Loading…</Island>}>
      {renderPage()}
    </Suspense>
  )

  return (
    <TooltipProvider delay={600}>
      <div className="app-shell flex h-svh min-h-svh min-w-0 flex-col overflow-hidden bg-window [--topbar-height:44px]" onClick={handleAppClick}>
        {/* the main toolbar of the IDE lies on the window, so the top bar is no island and has no border */}
        <header className="topbar flex h-(--topbar-height) flex-none items-center gap-2 px-2 md:px-3">
          <MobileNav nav={parts.nav} route={route} />
          <a
            className="brand flex flex-none items-center gap-2 rounded-md p-1 font-semibold text-foreground hover:no-underline"
            href={homeRoute()}
          >
            <span
              className="product-mark inline-grid h-6 min-w-6 flex-none place-items-center rounded-md px-0.5 bg-(image:--brand-gradient) text-3xs font-bold tracking-[0.04em] text-white shadow-sm"
              aria-hidden="true"
            >
              {config.brandMark}
            </span>
            <span className="brand-title text-base font-[650] tracking-[-0.01em] max-md:hidden">{config.brandMark} Docs</span>
          </a>
          <TopNav nav={parts.nav} route={route} />
          <div className="topbar-actions ml-auto flex flex-none items-center gap-2">
            {/* a search field on the window, as the search of the IDE toolbar: the island color with a ring */}
            <button
              type="button"
              className="palette-button inline-flex h-7 w-[248px] items-center gap-2 rounded-md bg-background pr-1 pl-2 text-muted-foreground ring-1 ring-foreground/10 hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring max-lg:w-auto"
              onClick={() => setPaletteOpen(true)}
            >
              <SearchIcon className="size-3.5" aria-hidden="true" />
              <span className="flex-1 text-left max-sm:sr-only">Search the docs</span>
              <Kbd className="bg-hover text-2xs max-md:hidden">{navigator.platform.startsWith('Mac') ? '⌘K' : 'Ctrl K'}</Kbd>
            </button>
            <ThemeToggle />
          </div>
        </header>

        <PageChromeProvider value={chrome}>
          {layout === 'canvas'
            ? <div className="route-outlet route-outlet-canvas flex min-h-0 flex-1 flex-col overflow-hidden px-2 pb-2">{routed}</div>
            : (
                <div className="route-outlet min-h-0 flex-1 overflow-y-auto">
                  {layout === 'reading' ? <ReadingLayout>{routed}</ReadingLayout> : <ToolLayout>{routed}</ToolLayout>}
                </div>
              )}
        </PageChromeProvider>

        <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      </div>
    </TooltipProvider>
  )
}
