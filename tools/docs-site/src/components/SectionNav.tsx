// The section tree of a reading page (sectionTree.ts): the left column at the section-nav breakpoint, and a Sheet
// behind a button of the crumb row below it. The links are plain <a> elements, so the app-level click handler of the
// shell navigates; a Sheet portal still bubbles the click through the React tree.

import { ChevronRightIcon, PanelLeftIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { StatusDot } from './StatusDot'
import { Button } from './ui/button'
import { ScrollArea } from './ui/scroll-area'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from './ui/sheet'
import { cn } from '../lib/utils'
import { activeTreeLink, type SectionTree, type SectionTreeLink } from '../sectionTree'

/** A tree up to this size opens every group; a larger one opens only the group of the current page. */
const OPEN_ALL_LIMIT = 80

function initiallyOpen(tree: SectionTree, active: SectionTreeLink | null): Set<string> {
  const size = tree.groups.reduce((sum, group) => sum + group.links.length, 0)
  return new Set(tree.groups.filter(group => size <= OPEN_ALL_LIMIT || (active !== null && group.links.includes(active))).map(group => group.id))
}

/* prose.css themes bare <a> in --primary; tree rows stay neutral, as the rows of a stock sidebar menu */
const ROW = 'flex min-h-7 items-center gap-1.5 rounded-md px-2 py-0.5 text-foreground hover:bg-accent no-underline'

function TreeLinks({ tree, currentHref }: { tree: SectionTree, currentHref: string }) {
  const active = activeTreeLink(tree, currentHref)
  const [open, setOpen] = useState(() => initiallyOpen(tree, active))
  const activeGroup = active === null ? undefined : tree.groups.find(group => group.links.includes(active))?.id
  // a link to a page of a closed group opens its group, so the current page always shows in the tree
  useEffect(() => {
    if (activeGroup !== undefined) {
      setOpen(current => current.has(activeGroup) ? current : new Set([...current, activeGroup]))
    }
  }, [activeGroup])
  const toggle = (id: string) => setOpen((current) => {
    const next = new Set(current)
    if (!next.delete(id)) {
      next.add(id)
    }
    return next
  })
  const indexActive = active === null && new URL(currentHref, 'http://docs-site.local').pathname === new URL(tree.href, 'http://docs-site.local').pathname
  return (
    <div className="section-tree grid grid-cols-[minmax(0,1fr)] gap-0.5 text-sm leading-snug">
      <a
        href={tree.href}
        aria-current={indexActive ? 'page' : undefined}
        className={cn(ROW, 'section-tree-head font-semibold', indexActive && 'bg-sidebar-accent text-sidebar-accent-foreground')}
      >
        <span className="min-w-0 flex-1 truncate">{tree.label}</span>
        <span className="text-xs font-normal text-muted-foreground tabular-nums">{tree.groups.reduce((sum, group) => sum + group.links.length, 0)}</span>
      </a>
      {tree.groups.map(group => (
        <div key={group.id} className="section-tree-group grid grid-cols-[minmax(0,1fr)] gap-0.5" data-group={group.id}>
          <button
            type="button"
            aria-expanded={open.has(group.id)}
            onClick={() => toggle(group.id)}
            className={cn(ROW, 'w-full pl-1 text-left', open.has(group.id) && 'font-semibold')}
          >
            <ChevronRightIcon className={cn('size-3.5 flex-none text-muted-foreground transition-transform', open.has(group.id) && 'rotate-90')} aria-hidden="true" />
            <span className="min-w-0 flex-1 truncate">{group.label}</span>
            <span className="text-xs font-normal text-muted-foreground tabular-nums">{group.links.length}</span>
          </button>
          {open.has(group.id) && (
            <ul className="m-0 grid list-none grid-cols-[minmax(0,1fr)] gap-0.5 p-0">
              {group.links.map(link => (
                <li key={link.href}>
                  <a
                    href={link.href}
                    title={link.label}
                    aria-current={link === active ? 'page' : undefined}
                    className={cn(ROW, 'pl-6 text-muted-foreground', link === active && 'bg-sidebar-accent font-medium text-sidebar-accent-foreground')}
                  >
                    {link.status !== undefined && <StatusDot status={link.status} />}
                    {link.detail !== undefined && <span className="flex-none font-mono text-xs text-muted-foreground tabular-nums">{link.detail}</span>}
                    <span className="min-w-0 flex-1 truncate">{link.label}</span>
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  )
}

/**
 * The left column of the reading layout. It sticks under the top bar and scrolls by itself. The shell keeps it mounted
 * within one section, so its open groups and its scroll position stay when the reader opens another page.
 */
export function SectionNav({ tree, currentHref }: { tree: SectionTree, currentHref: string }) {
  const ref = useRef<HTMLElement>(null)
  // keep the current page in view by a scroll of the tree only; scrollIntoView would scroll the route outlet too
  useEffect(() => {
    const nav = ref.current
    const link = nav?.querySelector('[aria-current="page"]')
    if (!nav || !link) {
      return
    }
    const navBox = nav.getBoundingClientRect()
    const linkBox = link.getBoundingClientRect()
    if (linkBox.top < navBox.top || linkBox.bottom > navBox.bottom) {
      nav.scrollTop += linkBox.top - navBox.top - navBox.height / 3
    }
  }, [currentHref])
  return (
    <nav
      ref={ref}
      aria-label={`${tree.label} pages`}
      className="section-nav sticky top-0 max-h-[calc(100svh-var(--topbar-height))] overflow-y-auto px-2 py-5 [view-transition-name:section-nav]"
    >
      <TreeLinks tree={tree} currentHref={currentHref} />
    </nav>
  )
}

/** The section tree in a Sheet, below the section-nav breakpoint. The trigger sits in the crumb row of the document. */
export function SectionNavSheet({ tree, currentHref }: { tree: SectionTree, currentHref: string }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<Button variant="ghost" size="icon-sm" className="section-nav-trigger -ml-1.5 section-nav:hidden" aria-label={`${tree.label} pages`} />}
      >
        <PanelLeftIcon />
      </SheetTrigger>
      <SheetContent side="left" className="section-nav-sheet w-72 gap-0">
        <SheetTitle className="sr-only">{`${tree.label} pages`}</SheetTitle>
        <ScrollArea className="min-h-0 flex-1">
          <nav aria-label={`${tree.label} pages`} className="p-3" onClick={event => (event.target as HTMLElement).closest('a') && setOpen(false)}>
            <TreeLinks tree={tree} currentHref={currentHref} />
          </nav>
        </ScrollArea>
      </SheetContent>
    </Sheet>
  )
}
