// The group box of a diagram (a lane area or a collapsible map area), and the four connection handles that every
// card of a diagram has. The hand-laid diagrams of the kernel and the map canvases of a site share them.

import { Handle, type NodeProps, Position } from '@xyflow/react'
import { Button } from '../components/ui/button'

/** The data of a group box: its title, its lane color, and an optional collapse action. */
export interface GroupNodeData {
  title: string
  /** Layer color token (indigo/sky/blue/green/amber/red) of the box. */
  color?: string
  onCollapse?: () => void
  [key: string]: unknown
}

export function NodeHandles() {
  return (
    <>
      <Handle type="target" position={Position.Top} className="arch-handle" />
      <Handle type="target" position={Position.Left} className="arch-handle" />
      <Handle type="source" position={Position.Bottom} className="arch-handle" />
      <Handle type="source" position={Position.Right} className="arch-handle" />
    </>
  )
}

export function GroupNode({ data, selected }: NodeProps & { data: GroupNodeData }) {
  return (
    <div className={`group-node layer-${data.color ?? 'neutral'}${selected ? ' selected' : ''} size-full rounded-xl border-[1.5px] border-dashed border-[color-mix(in_srgb,var(--layer-color,var(--muted-foreground))_55%,transparent)] bg-[color-mix(in_srgb,var(--layer-color,var(--muted-foreground))_5%,transparent)]`}>
      <NodeHandles />
      <header className="flex items-center justify-between gap-2 px-4 py-2.5 text-xs font-bold tracking-[0.06em] uppercase text-muted-foreground">
        {data.title}
        {typeof data.onCollapse === 'function' && (
          <Button
            variant="ghost"
            size="icon-xs"
            className="group-collapse nodrag text-inherit hover:text-foreground"
            title="Collapse area"
            onClick={event => {
              event.stopPropagation()
              ;(data.onCollapse as () => void)()
            }}
          >
            ▾
          </Button>
        )}
      </header>
    </div>
  )
}
