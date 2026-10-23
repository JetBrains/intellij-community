// The card of a hand-laid diagram (ADR 0203). Eight named handles, so an edge can leave or enter on
// the side the grid implies; the kind picks the skin, and the lane picks the accent color. The root
// carries `arch-node`, so the state rules of canvas.css (`.step-active`, `.sel-dimmed`, `.selected`)
// apply unchanged. The kinds and the planned badges come from the site config (`diagram`).

import { Handle, type NodeProps, Position } from '@xyflow/react'
import type { DiagramKindStyle, SiteConfig } from '../../scripts/siteConfig.ts'
import { HANDLE_ID, type DiagramNodeData } from './diagramLayout.ts'
import { nodeBase, nodeSmall, StepBadges } from './nodeBase.tsx'

/** The style of a kind that the site config does not name. The diagram check rejects such a kind, so only stale data shows it. */
const UNKNOWN_KIND: DiagramKindStyle = { id: '', label: '', glyph: null, className: 'border-border bg-muted' }

/** The glyph and the skin of each kind of the site config (`diagram.kinds`). The legend and the node card read the same table. */
export function kindStyles(diagram: SiteConfig['diagram']): (kind: string) => DiagramKindStyle {
  const byId = new Map(diagram.kinds.map(kind => [kind.id, kind]))
  return kind => byId.get(kind) ?? { ...UNKNOWN_KIND, id: kind, label: kind }
}

function Handles() {
  return (
    <>
      <Handle id={HANDLE_ID.target.top} type="target" position={Position.Top} className="arch-handle" />
      <Handle id={HANDLE_ID.target.right} type="target" position={Position.Right} className="arch-handle" />
      <Handle id={HANDLE_ID.target.bottom} type="target" position={Position.Bottom} className="arch-handle" />
      <Handle id={HANDLE_ID.target.left} type="target" position={Position.Left} className="arch-handle" />
      <Handle id={HANDLE_ID.source.top} type="source" position={Position.Top} className="arch-handle" />
      <Handle id={HANDLE_ID.source.right} type="source" position={Position.Right} className="arch-handle" />
      <Handle id={HANDLE_ID.source.bottom} type="source" position={Position.Bottom} className="arch-handle" />
      <Handle id={HANDLE_ID.source.left} type="source" position={Position.Left} className="arch-handle" />
    </>
  )
}

/** The card component of the diagram nodes, with the kinds and the planned badges of the site config. */
export function createDiagramNode(diagram: SiteConfig['diagram']) {
  const styleOf = kindStyles(diagram)
  const plannedBadge = new Map(diagram.planned.map(phase => [phase.id, phase.badge]))
  return function DiagramNode({ data, selected }: NodeProps & { data: DiagramNodeData }) {
    const { node } = data
    const style = styleOf(node.kind)
    return (
      <div
        className={`arch-node diagram-node kind-${node.kind} layer-${data.color}${selected ? ' selected' : ''}${node.dashed ? ' border-dashed' : ''} ${nodeBase} ${style.className} cursor-pointer`}
        title={node.text ?? node.title}
      >
        <Handles />
        <StepBadges data={{ stepBadges: data.stepBadges }} />
        {node.planned !== null && (
          <em className="diagram-node-planned text-3xs font-bold tracking-[0.05em] not-italic uppercase text-primary">{plannedBadge.get(node.planned) ?? node.planned}</em>
        )}
        <span className="flex items-start gap-1.5">
          {style.glyph !== null && <i className="diagram-node-glyph flex-none not-italic text-muted-foreground" aria-hidden="true">{style.glyph}</i>}
          <strong className={`wrap-anywhere ${style.mono === true ? 'font-mono text-xs' : 'leading-[1.2]'}`}>{node.title}</strong>
        </span>
        {node.text !== null && <small className={nodeSmall}>{node.text}</small>}
      </div>
    )
  }
}
