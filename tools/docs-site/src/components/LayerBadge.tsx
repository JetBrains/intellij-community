// The layer chip: a Badge in the lane color. The color comes from the `.layer-*` class→variable map in canvas.css,
// which the canvas node skins and the minimap fills read too, so the map stays in CSS while the chrome lives here.

import type { ReactNode } from 'react'
import { cn } from '../lib/utils'
import { Badge } from './ui/badge'

/** The layer chip of an entity header or a detail panel; a link when `href` is given. */
export function LayerBadge({ color, href, className, children }: { color: string, href?: string, className?: string, children: ReactNode }) {
  return (
    <Badge
      variant="outline"
      className={cn(
        'layer-chip border-transparent bg-(--layer-color)/16 font-bold text-(--layer-color) hover:no-underline [a]:hover:bg-(--layer-color)/28 [a]:hover:text-(--layer-color)',
        `layer-${color}`,
        className,
      )}
      render={href ? <a href={href} /> : undefined}
    >
      {children}
    </Badge>
  )
}
