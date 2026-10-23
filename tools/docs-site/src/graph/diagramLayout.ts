// The grid of a hand-laid diagram in pixels (ADR 0203). The author chose the columns and the rows;
// this module only converts them to positions, sizes, edge sides, and step classes. It imports no
// data and no React, so src/diagramLayout.test.ts loads it under node.

import type { Edge, Node } from '@xyflow/react'
import type { DiagramSide, HarvestedDiagram, HarvestedDiagramNode } from '../../scripts/diagram.ts'

/** The horizontal channel between two lanes; the edges between lanes run in it. */
export const COL_GAP = 144
/** The lane title row. */
export const LANE_HEADER = 44
/** The vertical channel between two rows. */
export const ROW_GAP = 36
/** The space between a lane edge and the card inside it. */
export const CELL_PAD_X = 16
/** The space a group box adds around its cells. */
export const GROUP_PAD = 12
/** The title row of a group box; the row above the group grows by it. */
export const GROUP_HEADER = 34
export const MIN_NODE_H = 64
export const BOTTOM_PAD = 20

// the same estimate as src/graph/views.ts: the canvas never measures the DOM (ADR 0097)
const CARD_VPAD = 18
const CARD_ROW_GAP = 2
// a 13px text-sm title
const TITLE_LINE_H = 20
const TITLE_CHAR_W = 7
// an 11px text-2xs text, line-height 1.3
const TEXT_LINE_H = 15
const TEXT_CHAR_W = 5.8
// a 10px text-3xs kicker
const KICKER_LINE_H = 14
/** Horizontal padding, borders, the accent stripe, and the kind glyph. */
const CARD_INNER_INSET = 50

export const HANDLE_ID = {
  source: { top: 's-top', right: 's-right', bottom: 's-bottom', left: 's-left' },
  target: { top: 't-top', right: 't-right', bottom: 't-bottom', left: 't-left' },
} as const satisfies Record<'source' | 'target', Record<DiagramSide, string>>

export interface DiagramLayout {
  /** Lanes and groups first, then the cards; every position is absolute. */
  nodes: Node[]
  edges: Edge[]
  width: number
  height: number
}

/** The data of a `diagramNode` card. */
export interface DiagramNodeData {
  node: HarvestedDiagramNode
  /** The lane color (`.layer-*` of canvas.css). */
  color: string
  laneTitle: string
  /** The 1-based steps the node appears in; the badges show them. */
  stepBadges: number[]
  [key: string]: unknown
}

function rows(text: string, charWidth: number, lineHeight: number, innerWidth: number): number {
  return Math.max(1, Math.ceil((text.length * charWidth) / innerWidth)) * lineHeight
}

/** The card height from its wrapped rows, as views.ts estimates a concept card. */
export function nodeHeight(node: HarvestedDiagramNode, width: number): number {
  const inner = width - CARD_INNER_INSET
  const parts = [rows(node.title, TITLE_CHAR_W, TITLE_LINE_H, inner)]
  if (node.text !== null) {
    parts.push(rows(node.text, TEXT_CHAR_W, TEXT_LINE_H, inner))
  }
  if (node.planned !== null) {
    parts.push(KICKER_LINE_H)
  }
  return Math.max(MIN_NODE_H, CARD_VPAD + parts.reduce((sum, row) => sum + row + CARD_ROW_GAP, -CARD_ROW_GAP))
}

/** The side an edge leaves or enters when the author gave none: by the grid direction. */
export function inferSides(
  from: { col: number, span: number, row: number },
  to: { col: number, span: number, row: number },
): { fromSide: DiagramSide, toSide: DiagramSide } {
  const fromRight = from.col + from.span - 1
  const toRight = to.col + to.span - 1
  if (toRight < from.col) {
    return { fromSide: 'left', toSide: 'right' }
  }
  if (to.col > fromRight) {
    return { fromSide: 'right', toSide: 'left' }
  }
  return to.row >= from.row ? { fromSide: 'bottom', toSide: 'top' } : { fromSide: 'top', toSide: 'bottom' }
}

export function layoutDiagram(diagram: HarvestedDiagram): DiagramLayout {
  const columnIndex = new Map(diagram.columns.map((column, index) => [column.id, index]))
  const laneX: number[] = []
  let x = 0
  for (const column of diagram.columns) {
    laneX.push(x)
    x += column.width + COL_GAP
  }
  const width = x - COL_GAP

  const nodeWidth = (node: HarvestedDiagramNode): number => {
    const start = columnIndex.get(node.col) ?? 0
    let total = 0
    for (let offset = 0; offset < node.span; offset++) {
      total += (diagram.columns[start + offset]?.width ?? 0) + (offset > 0 ? COL_GAP : 0)
    }
    return total - 2 * CELL_PAD_X
  }
  const rowCount = Math.max(1, ...diagram.nodes.map(node => node.row), ...diagram.groups.map(group => group.to.row))
  const rowHeight: number[] = Array.from({ length: rowCount + 1 }, () => 0)
  for (const node of diagram.nodes) {
    rowHeight[node.row] = Math.max(rowHeight[node.row], nodeHeight(node, nodeWidth(node)))
  }
  const groupStartsAt = new Set(diagram.groups.map(group => group.from.row))
  const rowTop: number[] = Array.from({ length: rowCount + 1 }, () => 0)
  let y = LANE_HEADER
  for (let row = 1; row <= rowCount; row++) {
    y += row === 1 ? 0 : ROW_GAP
    if (groupStartsAt.has(row)) {
      y += GROUP_HEADER
    }
    rowTop[row] = y
    y += rowHeight[row] === 0 ? MIN_NODE_H : rowHeight[row]
  }
  const height = y + BOTTOM_PAD

  const stepsOf = new Map<string, number[]>()
  diagram.steps.forEach((step, index) => {
    for (const id of step.nodes) {
      stepsOf.set(id, [...stepsOf.get(id) ?? [], index + 1])
    }
  })

  const nodes: Node[] = []
  for (const [index, column] of diagram.columns.entries()) {
    nodes.push({
      id: `lane:${column.id}`,
      type: 'archGroup',
      position: { x: laneX[index], y: 0 },
      width: column.width,
      height,
      selectable: false,
      focusable: false,
      draggable: false,
      zIndex: -2,
      data: { ref: `lane:${column.id}`, title: column.title, subtitle: column.module ?? undefined, color: column.color },
    })
  }
  for (const group of diagram.groups) {
    const fromCol = columnIndex.get(group.from.col) ?? 0
    const toCol = columnIndex.get(group.to.col) ?? fromCol
    const left = laneX[fromCol] + CELL_PAD_X - GROUP_PAD
    const right = laneX[toCol] + diagram.columns[toCol].width - CELL_PAD_X + GROUP_PAD
    const top = rowTop[group.from.row] - GROUP_HEADER
    const bottom = rowTop[group.to.row] + (rowHeight[group.to.row] || MIN_NODE_H) + GROUP_PAD
    nodes.push({
      id: `group:${group.id}`,
      type: 'archGroup',
      position: { x: left, y: top },
      width: right - left,
      height: bottom - top,
      selectable: false,
      focusable: false,
      draggable: false,
      zIndex: -1,
      data: { ref: `group:${group.id}`, title: group.title, color: group.color },
    })
  }
  const laneOf = new Map(diagram.columns.map(column => [column.id, column]))
  for (const node of diagram.nodes) {
    const start = columnIndex.get(node.col) ?? 0
    const w = nodeWidth(node)
    // every card of a row takes the row height, so the handles of one row sit on one line and an
    // edge between two cards of the row runs straight
    nodes.push({
      id: node.id,
      type: 'diagramNode',
      position: { x: laneX[start] + CELL_PAD_X, y: rowTop[node.row] },
      width: w,
      height: rowHeight[node.row],
      draggable: false,
      connectable: false,
      data: {
        node,
        color: laneOf.get(node.col)?.color ?? 'neutral',
        laneTitle: laneOf.get(node.col)?.title ?? '',
        stepBadges: stepsOf.get(node.id) ?? [],
      } satisfies DiagramNodeData,
    })
  }

  const byId = new Map(diagram.nodes.map(node => [node.id, node]))
  const edges: Edge[] = diagram.edges.map((edge) => {
    const from = byId.get(edge.from)!
    const to = byId.get(edge.to)!
    const inferred = inferSides(
      { col: columnIndex.get(from.col) ?? 0, span: from.span, row: from.row },
      { col: columnIndex.get(to.col) ?? 0, span: to.span, row: to.row },
    )
    const fromSide = edge.fromSide ?? inferred.fromSide
    const toSide = edge.toSide ?? inferred.toSide
    return {
      id: edge.id,
      source: edge.from,
      target: edge.to,
      sourceHandle: HANDLE_ID.source[fromSide],
      targetHandle: HANDLE_ID.target[toSide],
      type: 'arch',
      label: edge.label ?? undefined,
      className: edge.dashed ? 'edge-target' : undefined,
      data: { arrow: true, shape: 'step' },
    }
  })
  return { nodes, edges, width, height }
}

export interface StepClasses {
  nodes: Map<string, string>
  edges: Map<string, string>
}

/**
 * The state class of every card and edge at a step: the step's parts are active, the parts of an
 * earlier step form the trail, and the rest recede. The overview (no step) dims nothing.
 */
export function stepClasses(diagram: HarvestedDiagram, step: number | null): StepClasses {
  const nodes = new Map<string, string>()
  const edges = new Map<string, string>()
  if (step === null || step < 1 || step > diagram.steps.length) {
    return { nodes, edges }
  }
  const trailNodes = new Set(diagram.steps.slice(0, step - 1).flatMap(entry => entry.nodes))
  const trailEdges = new Set(diagram.steps.slice(0, step - 1).flatMap(entry => entry.edges))
  const active = diagram.steps[step - 1]
  const activeNodes = new Set(active.nodes)
  const activeEdges = new Set(active.edges)
  for (const node of diagram.nodes) {
    nodes.set(node.id, activeNodes.has(node.id) ? 'step-active' : trailNodes.has(node.id) ? 'step-trail' : 'sel-dimmed')
  }
  for (const edge of diagram.edges) {
    edges.set(edge.id, activeEdges.has(edge.id) ? 'sel-active' : trailEdges.has(edge.id) ? 'step-trail' : 'sel-dimmed')
  }
  return { nodes, edges }
}
