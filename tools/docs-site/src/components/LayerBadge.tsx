// The layer chip: an outline Badge with a dot in the lane color. The lane color is a mark, so the label keeps the text
// color. The color comes from the `.layer-*` class→variable map in canvas.css, which the canvas node skins and the
// minimap fills read too, so the map stays in CSS while the chrome lives here.

import type { ReactNode } from 'react'
import { cn } from '../lib/utils'
import { Badge } from './ui/badge'

/** The layer chip of an entity header or a detail panel; a link when `href` is given. */
export function LayerBadge({ color, href, className, children }: { color: string, href?: string, className?: string, children: ReactNode }) {
  return (
    <Badge
      variant="outline"
      className={cn('layer-chip no-underline', `layer-${color}`, className)}
      render={href ? <a href={href} /> : undefined}
    >
      <span className="size-2 flex-none rounded-full bg-(--layer-color)" aria-hidden="true" />
      {children}
    </Badge>
  )
}
