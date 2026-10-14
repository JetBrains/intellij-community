import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { HarvestedDiagram, HarvestedDiagramNode } from '../scripts/diagram.ts'
import { CELL_PAD_X, COL_GAP, HANDLE_ID, inferSides, LANE_HEADER, layoutDiagram, nodeHeight, stepClasses } from './graph/diagramLayout.ts'

function node(id: string, col: string, row: number, extra: Partial<HarvestedDiagramNode> = {}): HarvestedDiagramNode {
  return {
    id,
    title: id.toUpperCase(),
    text: null,
    kind: 'service',
    col,
    row,
    span: 1,
    symbol: null,
    source: null,
    line: null,
    module: null,
    spec: null,
    adr: null,
    concept: null,
    planned: null,
    dashed: false,
    detail: null,
    symbolRef: null,
    sourceRef: null,
    adrTitle: null,
    adrPath: null,
    ...extra,
  }
}

const diagram: HarvestedDiagram = {
  id: 'sample',
  title: 'Sample',
  summary: 'A sample.',
  columns: [
    { id: 'a', title: 'A', module: null, color: 'sky', width: 200 },
    { id: 'b', title: 'B', module: null, color: 'indigo', width: 300 },
  ],
  groups: [{ id: 'g', title: 'G', color: 'neutral', from: { col: 'b', row: 2 }, to: { col: 'b', row: 2 } }],
  nodes: [node('x', 'a', 1), node('y', 'b', 1, { text: 'a text that wraps over more than one line of the card' }), node('z', 'b', 2, { planned: 'phase-2' }), node('w', 'a', 2, { span: 2 })],
  edges: [
    { id: 'x->y', from: 'x', to: 'y', fromSide: null, toSide: null, label: 'go', dashed: false },
    { id: 'y->z', from: 'y', to: 'z', fromSide: null, toSide: null, label: null, dashed: true },
    { id: 'z->x', from: 'z', to: 'x', fromSide: 'left', toSide: null, label: null, dashed: false },
  ],
  steps: [
    { title: 'One', caption: 'First.', nodes: ['x', 'y'], edges: ['x->y'], flowStep: null, flowRef: null },
    { title: 'Two', caption: 'Second.', nodes: ['z'], edges: ['y->z'], flowStep: null, flowRef: null },
  ],
}

describe('layoutDiagram', () => {
  const layout = layoutDiagram(diagram)
  const byId = new Map(layout.nodes.map(entry => [entry.id, entry]))

  it('places the lanes left to right and the cards inside them', () => {
    assert.equal(byId.get('lane:a')?.position.x, 0)
    assert.equal(byId.get('lane:b')?.position.x, 200 + COL_GAP)
    assert.equal(byId.get('x')?.position.x, CELL_PAD_X)
    assert.equal(byId.get('x')?.position.y, LANE_HEADER)
    assert.equal(byId.get('x')?.width, 200 - 2 * CELL_PAD_X)
    assert.equal(layout.width, 200 + COL_GAP + 300)
  })

  it('gives every card of a row the height of its tallest card and a spanning card the width of both lanes', () => {
    assert.equal(byId.get('x')?.height, byId.get('y')?.height)
    assert.ok((byId.get('y')?.height ?? 0) >= nodeHeight(diagram.nodes[1], 268))
    assert.ok((byId.get('x')?.height ?? 0) > nodeHeight(diagram.nodes[0], 168))
    assert.equal(byId.get('w')?.width, 200 + COL_GAP + 300 - 2 * CELL_PAD_X)
    assert.equal(byId.get('w')?.position.y, byId.get('z')?.position.y)
  })

  it('draws lanes and groups before the cards, with a group around its cells', () => {
    assert.deepEqual(layout.nodes.slice(0, 3).map(entry => entry.type), ['archGroup', 'archGroup', 'archGroup'])
    const group = byId.get('group:g')!
    const z = byId.get('z')!
    assert.ok(group.position.x < z.position.x && group.position.y < z.position.y)
    assert.ok(group.position.x + (group.width ?? 0) > z.position.x + (z.width ?? 0))
  })

  it('names a handle that the card declares on every edge', () => {
    const handles = new Set<string>([...Object.values(HANDLE_ID.source), ...Object.values(HANDLE_ID.target)])
    for (const edge of layout.edges) {
      assert.ok(handles.has(edge.sourceHandle ?? ''), `${edge.id} source ${edge.sourceHandle}`)
      assert.ok(handles.has(edge.targetHandle ?? ''), `${edge.id} target ${edge.targetHandle}`)
    }
    const across = layout.edges.find(edge => edge.id === 'x->y')!
    assert.equal(across.sourceHandle, 's-right')
    assert.equal(across.targetHandle, 't-left')
    const down = layout.edges.find(edge => edge.id === 'y->z')!
    assert.equal(down.sourceHandle, 's-bottom')
    assert.equal(down.className, 'edge-target')
    const explicit = layout.edges.find(edge => edge.id === 'z->x')!
    assert.equal(explicit.sourceHandle, 's-left')
    assert.equal(explicit.targetHandle, 't-right')
  })

  it('infers the side from the grid direction', () => {
    assert.deepEqual(inferSides({ col: 0, span: 1, row: 1 }, { col: 1, span: 1, row: 3 }), { fromSide: 'right', toSide: 'left' })
    assert.deepEqual(inferSides({ col: 1, span: 1, row: 3 }, { col: 1, span: 1, row: 1 }), { fromSide: 'top', toSide: 'bottom' })
    assert.deepEqual(inferSides({ col: 0, span: 2, row: 1 }, { col: 1, span: 1, row: 2 }), { fromSide: 'bottom', toSide: 'top' })
  })

  it('counts the steps of a card for its badges', () => {
    assert.deepEqual((byId.get('x')?.data as { stepBadges: number[] }).stepBadges, [1])
    assert.deepEqual((byId.get('w')?.data as { stepBadges: number[] }).stepBadges, [])
  })
})

describe('stepClasses', () => {
  it('dims nothing on the overview', () => {
    const classes = stepClasses(diagram, null)
    assert.equal(classes.nodes.size, 0)
    assert.equal(classes.edges.size, 0)
  })

  it('lights the step, keeps the trail, and recedes the rest', () => {
    const classes = stepClasses(diagram, 2)
    assert.equal(classes.nodes.get('z'), 'step-active')
    assert.equal(classes.nodes.get('x'), 'step-trail')
    assert.equal(classes.nodes.get('w'), 'sel-dimmed')
    assert.equal(classes.edges.get('y->z'), 'sel-active')
    assert.equal(classes.edges.get('x->y'), 'step-trail')
    assert.equal(classes.edges.get('z->x'), 'sel-dimmed')
  })
})
