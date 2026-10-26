// The page layouts of the docs sites: a flat page on --background under the top bar. The route table gives each page its
// layout (`PageLayout` of routeTable.ts); this module draws it.
//
// - reading: the section tree, the document, and the table of contents, three columns 8px apart. The shell draws the
//   tree, and `DocPage` of furniture.tsx draws the document and the table of contents.
// - tool: one column across the page, at text-sm.
// - canvas: the page fills the outlet and draws its own panes, for example the prose and the diagram of a flow.
//
// The route outlet is the one scroller of a reading and a tool page. The side columns stick under the top bar.

import { createContext, use, type ReactNode } from 'react'
import { PageBreadcrumb } from './components/PageBreadcrumb'
import { SectionNav, SectionNavSheet } from './components/SectionNav'
import type { RouteCrumb } from './crumbs'
import { cn } from './lib/utils'
import type { PageLayout } from './routeTable.ts'
import type { SectionTree } from './sectionTree'

/** The measure of a reading document: `prose` is 70ch, `wide` about 960px for a page of cards. */
export type PageMeasure = 'prose' | 'wide'

/** What the shell knows about the page that a page part draws. */
export interface PageChrome {
  /** The layout of the page. A `catalog` route is the reading layout with the wide measure. */
  layout: Exclude<PageLayout, 'catalog'>
  measure: PageMeasure
  /** The in-page crumb trail. A canvas page owns its trail, so it gets none. */
  crumbs: RouteCrumb[]
  /** The section tree of a reading page, or null. */
  tree: SectionTree | null
  /** The URL of the current route, which marks the current page in the tree. */
  href: string
}

const PageChromeContext = createContext<PageChrome>({ layout: 'tool', measure: 'prose', crumbs: [], tree: null, href: '' })

export const PageChromeProvider = PageChromeContext.Provider

/** The page chrome of the current route. */
export function usePageChrome(): PageChrome {
  return use(PageChromeContext)
}

/**
 * The top row of a document: the button of the section tree below the section-nav breakpoint, and the crumb trail. It
 * renders nothing when the page has neither.
 */
export function PageCrumbRow({ className }: { className?: string }) {
  const { crumbs, tree, href } = usePageChrome()
  if (crumbs.length === 0 && tree === null) {
    return null
  }
  return (
    <div className={cn('page-breadcrumb-bar mb-4 flex min-h-7 items-center gap-1', tree !== null && crumbs.length === 0 && 'section-nav:hidden', className)}>
      {tree !== null && <SectionNavSheet tree={tree} currentHref={href} />}
      <PageBreadcrumb crumbs={crumbs} />
    </div>
  )
}

/**
 * The reading layout: the section tree, then the page, as one group in the middle of the page. The shell keeps it
 * mounted across the pages of one section, so the tree does not blink and keeps its state. A page without a tree has
 * no tree column, so the document and the table of contents take the room. The page (DocPage) gives the width of the
 * group: its measure and its table of contents.
 */
export function ReadingLayout({ children }: { children: ReactNode }) {
  const { tree, href } = usePageChrome()
  return (
    <div className="reading-layout flex items-start justify-center gap-2 px-2">
      {tree !== null && (
        <div className="hidden w-[240px] flex-none self-stretch section-nav:block">
          <SectionNav key={tree.id} tree={tree} currentHref={href} />
        </div>
      )}
      <div className="reading-column min-w-0 flex-[0_1_auto]">
        {children}
      </div>
    </div>
  )
}

/** The tool layout: one column across the page, at least as tall as the outlet, at the text-sm of a stock tool page. */
export function ToolLayout({ children }: { children: ReactNode }) {
  return <div className="tool-layout min-h-full text-sm">{children}</div>
}
