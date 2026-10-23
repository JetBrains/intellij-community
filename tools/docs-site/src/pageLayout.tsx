// The layers of the Islands theme of the IDE: the window (`bg-window`) behind the islands, and each page area as an
// island on it. The route table gives each page its layout (`PageLayout` of routeTable.ts); this module draws it.
//
// - reading: the section tree, the document, and the table of contents, three islands 8px apart. The shell draws the
//   tree, and `DocPage` of furniture.tsx draws the document and the table of contents.
// - tool: one island across the window. `DocPage` inside it draws no island of its own.
// - canvas: the page fills the outlet and draws its own islands, for example the prose and the diagram of a flow.
//
// The route outlet is the one scroller of a reading and a tool page. The side islands stick under the top bar.

import { createContext, use, type ReactNode } from 'react'
import { Island } from './components/Island'
import { PageBreadcrumb } from './components/PageBreadcrumb'
import { SectionNav, SectionNavSheet } from './components/SectionNav'
import type { RouteCrumb } from './crumbs'
import { cn } from './lib/utils'
import type { PageLayout } from './routeTable.ts'
import type { SectionTree } from './sectionTree'

/** What the shell knows about the page that a page part draws. */
export interface PageChrome {
  layout: PageLayout
  /** The in-page crumb trail. A canvas page owns its trail, so it gets none. */
  crumbs: RouteCrumb[]
  /** The section tree of a reading page, or null. */
  tree: SectionTree | null
  /** The URL of the current route, which marks the current page in the tree. */
  href: string
}

const PageChromeContext = createContext<PageChrome>({ layout: 'tool', crumbs: [], tree: null, href: '' })

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
 * The reading layout: the section tree, then the page. The shell keeps it mounted across the pages of one section, so
 * the tree does not blink and keeps its state. The column of the tree is reserved on a page without a tree, so the left
 * edge of the document stays put between the reading pages.
 */
export function ReadingLayout({ children }: { children: ReactNode }) {
  const { tree, href } = usePageChrome()
  return (
    <div className="reading-layout flex items-start justify-center gap-2 px-2 pb-2">
      <div className="hidden w-[240px] flex-none self-stretch section-nav:block">
        {tree !== null && <SectionNav key={tree.id} tree={tree} currentHref={href} />}
      </div>
      {/* the document column (70ch and its padding), and the table of contents with its gap at the toc breakpoint */}
      <div className="reading-column min-w-0 flex-1 max-w-[calc(70ch+80px)] toc:max-w-[calc(70ch+80px+228px)]">
        {children}
      </div>
    </div>
  )
}

/** The tool layout: one island across the window, at least as tall as the outlet. */
export function ToolLayout({ children }: { children: ReactNode }) {
  return (
    <div className="tool-layout min-h-full px-2 pb-2">
      <Island className="tool-island min-h-[calc(100svh-var(--topbar-height)-8px)]">
        {children}
      </Island>
    </div>
  )
}
