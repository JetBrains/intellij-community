// Shared edge renderer for the ELK-based map/mini/embedded diagrams: the default bezier path
// plus the same slim barbed arrowhead FlowDiagram draws. The arrow lives inside the edge
// element (not a shared SVG marker def), so it follows the edge's color state — accent on
// hover/selection, dimmed with the selection focus — keeps a constant size regardless of
// stroke width, and lands in the Export PNG viewport snapshot.

import { BaseEdge, EdgeLabelRenderer, EdgeText, getBezierPath, getSmoothStepPath, Position, type EdgeProps } from '@xyflow/react'

/** The stroke ends this far before the node border, under the arrow body rather than past the tip. */
const ARROW_INSET = 8

/** Approach vector and arrow rotation per node side the edge attaches to. */
const APPROACH: Record<Position, { dx: number, dy: number, angle: number }> = {
  [Position.Top]: { dx: 0, dy: -1, angle: 90 },
  [Position.Bottom]: { dx: 0, dy: 1, angle: -90 },
  [Position.Left]: { dx: -1, dy: 0, angle: 0 },
  [Position.Right]: { dx: 1, dy: 0, angle: 180 },
}

export function ArchEdge({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition, label, data }: EdgeProps) {
  const options = data as { arrow?: boolean, shape?: 'bezier' | 'step', state?: string } | undefined
  const arrow = options?.arrow === true
  const approach = APPROACH[targetPosition]
  const inset = arrow ? ARROW_INSET : 0
  const ends = {
    sourceX,
    sourceY,
    sourcePosition,
    targetX: targetX + approach.dx * inset,
    targetY: targetY + approach.dy * inset,
    targetPosition,
  }
  // a hand-laid diagram (ADR 0204) routes its edges orthogonally, so they follow the grid
  const step = options?.shape === 'step'
  const [path, labelX, labelY] = step ? getSmoothStepPath({ ...ends, borderRadius: 10 }) : getBezierPath(ends)
  return (
    <>
      <BaseEdge path={path} />
      {arrow && (
        <polygon
          className="arch-edge-arrow"
          points="0,-4.5 10,0 0,4.5 2.5,0"
          transform={`translate(${targetX}, ${targetY}) rotate(${approach.angle}) translate(-10, 0)`}
        />
      )}
      {/* a hand-laid label lives in the HTML layer above every edge, so a later edge never crosses it;
          `data.state` carries the step class, because the layer sits outside the edge element */}
      {label != null && label !== '' && (step
        ? (
            <EdgeLabelRenderer>
              <div
                className={`arch-edge-label nodrag nopan absolute rounded-sm bg-background px-1 text-xs leading-tight text-foreground${options?.state ? ` ${options.state}` : ''}`}
                style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
              >
                {label}
              </div>
            </EdgeLabelRenderer>
          )
        : <EdgeText x={labelX} y={labelY} label={label} />)}
    </>
  )
}

export const archEdgeTypes = { arch: ArchEdge }
