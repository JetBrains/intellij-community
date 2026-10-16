// The nav model of the top-nav shell: TopNav (desktop) and MobileNav (sheet) both derive from the nav table of the
// site, so section labels and order stay in one place. The route table gives each route its nav section
// (routeTable.ts), and that section marks the active item. The brand links the Home page, so Home has no entry.
//
// An item holds a route, not an href: the site builds its nav table before `createSite` installs the routes, and
// the shell builds the href at render time.

import type { LucideIcon } from 'lucide-react'
import { sectionOf } from './router'
import type { AnyRoute } from './routeTable.ts'

export interface NavItem {
  label: string
  route: AnyRoute
  icon: LucideIcon
  /** The nav section that the route table gives the pages of this item. */
  section: string
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

export type NavEntry = NavItem | NavGroup

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return 'items' in entry
}

export function isNavItemActive(item: NavItem, route: AnyRoute): boolean {
  return item.section === sectionOf(route)
}
