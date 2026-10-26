// Desktop primary nav (hidden below lg): flat section links plus the dropdown groups
// of the nav table of the site, active state derived from the current route. NavigationMenuLink
// renders a plain <a>, so the app-level click handler keeps intercepting for SPA navigation;
// `active` emits data-active + aria-current="page".

import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
  navigationMenuTriggerStyle,
} from './ui/navigation-menu'
import { cn } from '../lib/utils'
import { isNavGroup, isNavItemActive, type NavEntry, type NavGroup } from '../nav'
import { hrefForRoute } from '../router'
import type { AnyRoute as Route } from '../routeTable.ts'

/* prose.css themes bare <a> in --primary; nav links must stay neutral */
const topLevelLink = cn(
  navigationMenuTriggerStyle(),
  'text-muted-foreground hover:text-foreground no-underline data-active:bg-muted/50 data-active:text-foreground',
)

function NavGroupMenu({ group, route }: { group: NavGroup, route: Route }) {
  const groupActive = group.items.some(item => isNavItemActive(item, route))
  return (
    <NavigationMenuItem>
      <NavigationMenuTrigger
        data-active={groupActive || undefined}
        className="text-muted-foreground hover:text-foreground data-active:bg-muted/50 data-active:text-foreground"
      >
        {group.label}
      </NavigationMenuTrigger>
      <NavigationMenuContent>
        <ul className="grid w-48 gap-0.5">
          {group.items.map(item => (
            <li key={item.label}>
              <NavigationMenuLink
                href={hrefForRoute(item.route)}
                active={isNavItemActive(item, route)}
                closeOnClick
                className="text-foreground no-underline"
              >
                <item.icon className="text-muted-foreground" />
                {item.label}
              </NavigationMenuLink>
            </li>
          ))}
        </ul>
      </NavigationMenuContent>
    </NavigationMenuItem>
  )
}

export function TopNav({ nav, route }: { nav: readonly NavEntry[], route: Route }) {
  return (
    <NavigationMenu aria-label="Primary" className="max-lg:hidden">
      <NavigationMenuList>
        {nav.map(entry =>
          isNavGroup(entry)
            ? <NavGroupMenu key={entry.label} group={entry} route={route} />
            : (
                <NavigationMenuItem key={entry.label}>
                  <NavigationMenuLink
                    href={hrefForRoute(entry.route)}
                    active={isNavItemActive(entry, route)}
                    className={topLevelLink}
                  >
                    {entry.label}
                  </NavigationMenuLink>
                </NavigationMenuItem>
              ),
        )}
      </NavigationMenuList>
    </NavigationMenu>
  )
}
