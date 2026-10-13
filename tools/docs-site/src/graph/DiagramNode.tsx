// The card of a hand-laid diagram (ADR 0203). Eight named handles, so an edge can leave or enter on
// the side the grid implies; the kind picks the skin, and the lane picks the accent color. The root
// carries `arch-node`, so the state rules of canvas.css (`.step-active`, `.sel-dimmed`, `.selected`)
// apply unchanged.

import { Handle, type NodeProps, Position } from '@xyflow/react'
import type { DiagramNodeKind } from '../../scripts/diagram.ts'
import { HANDLE_ID, type DiagramNodeData } from './diagramLayout.ts'
import { nodeBase, nodeSmall, StepBadges } from './nodeBase.tsx'

/** The glyph and the skin of each kind. The legend of HandLaidDiagram reads the same table. */
export const NODE_KIND_STYLE: Record<DiagramNodeKind, { label: string, glyph: string | null, className: string }> = {
  service: { label: 'Service', glyph: null, className: 'border-border bg-muted' },
  state: { label: 'State', glyph: null, className: 'border-border border-l-4 border-l-(--layer-color) bg-muted' },
  store: { label: 'Store', glyph: '▤', className: 'border-(--layer-color) border-l-4 bg-muted' },
  reducer: { label: 'Reducer', glyph: 'ƒ', className: 'rounded-xl border-border bg-[color-mix(in_srgb,var(--primary)_7%,var(--muted))]' },
  rpc: { label: 'RPC', glyph: '⇄', className: 'border-dotted border-input bg-transparent' },
  reaction: { label: 'Reaction', glyph: '↻', className: 'border-border bg-transparent' },
  surface: { label: 'Surface', glyph: '▭', className: 'rounded-2xl border-border bg-card' },
  writer: { label: 'Writer', glyph: '✎', className: 'border-border bg-module' },
  action: { label: 'Action', glyph: '▸', className: 'border-border border-t-4 border-t-(--layer-color) bg-muted' },
  log: { label: 'Log', glyph: '≣', className: 'border-border border-b-4 border-b-double bg-muted' },
  actor: { label: 'Actor', glyph: '◉', className: 'rounded-full border-dashed border-border bg-transparent' },
  module: { label: 'Module', glyph: null, className: 'border-border bg-module' },
  target: { label: 'Planned part', glyph: null, className: 'border-dashed border-input bg-transparent text-muted-foreground' },
}

const PLANNED_LABEL: Record<string, string> = {
  'phase-1': 'Planned · phase 1',
  'phase-2': 'Planned · phase 2',
  'phase-4': 'Planned · phase 4',
  'open': 'Open decision',
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

export function DiagramNode({ data, selected }: NodeProps & { data: DiagramNodeData }) {
  const { node } = data
  const style = NODE_KIND_STYLE[node.kind]
  const mono = node.kind === 'module'
  return (
    <div
      className={`arch-node diagram-node kind-${node.kind} layer-${data.color}${selected ? ' selected' : ''}${node.dashed ? ' border-dashed' : ''} ${nodeBase} ${style.className} cursor-pointer`}
      title={node.text ?? node.title}
    >
      <Handles />
      <StepBadges data={{ stepBadges: data.stepBadges }} />
      {node.planned !== null && (
        <em className="diagram-node-planned text-3xs font-bold tracking-[0.05em] not-italic uppercase text-primary">{PLANNED_LABEL[node.planned]}</em>
      )}
      <span className="flex items-start gap-1.5">
        {style.glyph !== null && <i className="diagram-node-glyph flex-none not-italic text-muted-foreground" aria-hidden="true">{style.glyph}</i>}
        <strong className={`wrap-anywhere ${mono ? 'font-mono text-detail' : 'leading-[1.2]'}`}>{node.title}</strong>
      </span>
      {node.text !== null && <small className={nodeSmall}>{node.text}</small>}
    </div>
  )
}
