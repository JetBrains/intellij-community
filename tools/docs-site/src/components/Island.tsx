// One island of the Islands theme of the IDE: a page area on the window (pageLayout.tsx).

import type { ComponentProps } from 'react'
import { cn } from '../lib/utils'

type IslandElement = 'div' | 'nav' | 'aside' | 'article'

/**
 * One island: `--card` with the island arc (10px) on the window. An island has no border, no ring and no shadow, as in
 * the IDE; its color alone separates it from the window. A panel inside an island is a `Card`, which is less round
 * (`rounded-lg`).
 */
export function Island<E extends IslandElement = 'div'>({ as, className, ...props }: { as?: E } & ComponentProps<E>) {
  const Element = (as ?? 'div') as 'div'
  return <Element {...props as ComponentProps<'div'>} className={cn('island rounded-xl bg-card text-card-foreground', className)} />
}
