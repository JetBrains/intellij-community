// Mobile nav (below lg): hamburger button opening a left Sheet with every section link flat.
// Links are plain <a> elements; the Sheet portal still bubbles clicks through the React tree,
// so the app-level click handler intercepts them for SPA navigation — the container onClick
// only closes the sheet.

import { MenuIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from './ui/button'
import { ScrollArea } from './ui/scroll-area'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from './ui/sheet'
import { cn } from '../lib/utils'
import { isNavGroup, isNavItemActive, type NavEntry, type NavItem } from '../nav'
import { hrefForRoute } from '../router'
import type { AnyRoute as Route } from '../routeTable.ts'

function NavLink({ item, route }: { item: NavItem, route: Route }) {
  const active = isNavItemActive(item, route)
  return (
    /* prose.css themes bare <a> in --primary; nav links must stay neutral */
    <a
      data-slot="mobile-nav-link"
      data-active={active || undefined}
      aria-current={active ? 'page' : undefined}
      href={hrefForRoute(item.route)}
      className={cn(
        'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-foreground hover:bg-muted no-underline',
        active && 'bg-muted/50 font-medium',
      )}
    >
      <item.icon className="size-4 text-muted-foreground" />
      {item.label}
    </a>
  )
}

export function MobileNav({ nav, route }: { nav: readonly NavEntry[], route: Route }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<Button variant="ghost" size="icon-sm" className="lg:hidden" aria-label="Navigation" />}
      >
        <MenuIcon />
      </SheetTrigger>
      <SheetContent side="left" className="w-64 gap-0">
        <SheetTitle className="sr-only">Navigation</SheetTitle>
        <ScrollArea className="min-h-0 flex-1">
          <nav aria-label="Primary" className="flex flex-col gap-0.5 p-3" onClick={() => setOpen(false)}>
            {nav.map(entry =>
              isNavGroup(entry)
                ? (
                    <div key={entry.label} className="flex flex-col gap-0.5">
                      <div className="px-2 pt-3 pb-1 text-xs font-medium text-muted-foreground">{entry.label}</div>
                      {entry.items.map(item => <NavLink key={item.label} item={item} route={route} />)}
                    </div>
                  )
                : <NavLink key={entry.label} item={entry} route={route} />,
            )}
          </nav>
        </ScrollArea>
      </SheetContent>
    </Sheet>
  )
}
