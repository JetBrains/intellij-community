// Sticky right-hand "On this page" table of contents. Links are routed anchor hrefs — the
// route change drives the host page's useAnchorScroll; pages that render it add `has-toc` to
// their .doc-page for the two-column layout (content first child, aside second). In the reading
// layout the aside is an island of its own; in the tool layout it is a column of the one island.
// Scroll-spy tracks the section nearest the top of the scrolling route outlet.

import { Fragment, type ReactNode, useEffect, useRef, useState } from 'react'
import { Island } from '../components/Island'
import { DocPage } from '../furniture'
import { cn } from '../lib/utils'
import { type AnchorHref, extractMarkdownHeadings } from '../markdown'
import { usePageChrome } from '../pageLayout'

export interface TocItem {
  href: string
  title: string
  group?: string
}

/** TOC items for a markdown body: one flat entry per h2; on duplicate ids the first wins
 *  (matching which element the anchor scroll finds). */
export function markdownTocItems(source: string, anchorHref: AnchorHref): TocItem[] {
  const seen = new Set<string>()
  return extractMarkdownHeadings(source)
    .filter(heading => heading.level === 2 && !seen.has(heading.id) && (seen.add(heading.id), true))
    .map(heading => ({ href: anchorHref(heading.id), title: heading.title }))
}

/** DocPage that shows a TocAside only when there are >= 2 entries; below that it renders as a
 *  plain DocPage. The `toc` prop must track the count — with the two-column layout on but no
 *  aside, the content column would flex into the reserved gutter. */
export function TocDocPage({ items, extra, className, children }: { items: TocItem[], extra?: ReactNode, className?: string, children: ReactNode }) {
  const hasToc = items.length >= 2
  return (
    <DocPage toc={hasToc} className={className}>
      {hasToc ? <div>{children}</div> : children}
      {hasToc && <TocAside items={items} extra={extra} />}
    </DocPage>
  )
}

// The in-page anchor is always the last route segment (feature id, glossary slug, spec group).
function anchorIdOf(href: string): string {
  return decodeURIComponent(href.split('/').pop() ?? '')
}

/** The table of contents; `extra` is a block under the list, such as the links of a decision record. */
export function TocAside({ items, extra }: { items: TocItem[], extra?: ReactNode }) {
  const ref = useRef<HTMLElement>(null)
  const island = usePageChrome().layout === 'reading'
  const [active, setActive] = useState(0)

  useEffect(() => {
    const aside = ref.current
    const container = aside?.closest('.route-outlet')
    if (!aside || !container) {
      return
    }
    const update = () => {
      const containerTop = container.getBoundingClientRect().top
      let current = 0
      for (const [index, item] of items.entries()) {
        const target = document.getElementById(anchorIdOf(item.href))
        if (target && target.getBoundingClientRect().top - containerTop <= 16) {
          current = index
        }
      }
      // at the very bottom the last section is the one being read even if it never reaches the top
      if (container.scrollTop > 0 && container.scrollTop + container.clientHeight >= container.scrollHeight - 2) {
        current = items.length - 1
      }
      setActive(current)
    }
    update()
    container.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    return () => {
      container.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
    }
  }, [items])

  // When the aside itself overflows, keep the active link visible by scrolling the aside only
  // (scrollIntoView would also scroll the route outlet).
  useEffect(() => {
    const aside = ref.current
    const link = aside?.querySelectorAll('a')[active]
    if (!aside || !link) {
      return
    }
    const asideBox = aside.getBoundingClientRect()
    const linkBox = link.getBoundingClientRect()
    if (linkBox.top < asideBox.top) {
      aside.scrollTop += linkBox.top - asideBox.top
    }
    else if (linkBox.bottom > asideBox.bottom) {
      aside.scrollTop += linkBox.bottom - asideBox.bottom
    }
  }, [active])

  const links = (
    <nav aria-label="On this page" className="grid leading-snug">
      <div className="mb-2 font-semibold text-foreground">On this page</div>
      {items.map((item, index) => (
        <Fragment key={item.href}>
          {item.group != null && item.group !== items[index - 1]?.group && (
            <div className="mt-2 mb-0.5 text-3xs uppercase tracking-[0.05em] text-muted-foreground">{item.group}</div>
          )}
          <a
            href={item.href}
            aria-current={index === active ? 'location' : undefined}
            className={cn(
              'block py-1 pl-3 hover:no-underline',
              index === active
                ? 'font-medium text-foreground shadow-[inset_2px_0_0_var(--primary)]'
                : 'text-muted-foreground shadow-[inset_1px_0_0_var(--border)] hover:text-foreground',
            )}
          >
            {item.title}
          </a>
        </Fragment>
      ))}
    </nav>
  )
  /* native overflow, not ui/scroll-area: the effects above read/write this element's own
     scrollTop; a ScrollArea would move scrolling into an inner viewport and silently kill
     the keep-active-link-visible math */
  const box = 'toc-aside hidden toc:block w-[220px] flex-none sticky self-start overflow-y-auto'
  return island
    ? <Island as="aside" ref={ref} className={cn(box, 'top-0 max-h-[calc(100svh-var(--topbar-height)-8px)] px-3.5 py-3.5')}>{links}{extra}</Island>
    : <aside ref={ref} className={cn(box, 'top-6 max-h-[calc(100svh-var(--topbar-height)-48px)]')}>{links}{extra}</aside>
}
