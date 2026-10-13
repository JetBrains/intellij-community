import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { edgeId, findAirDiagramFences, parseAirDiagramFence, parseFlowStepRef, validateDiagram } from './diagram.ts'

function minimal(): Record<string, unknown> {
  return {
    id: 'sample',
    title: 'A sample',
    summary: 'One sentence.',
    columns: [{ id: 'a', title: 'Left', color: 'sky' }, { id: 'b', title: 'Right' }],
    nodes: [
      { id: 'x', title: 'X', kind: 'state', at: 'a,1', symbol: 'ChannelStore' },
      { id: 'y', title: 'Y', kind: 'reducer', at: 'b,1', text: 'reduces' },
      { id: 'z', title: 'Z', kind: 'target', at: 'b,2', planned: 'phase-2' },
    ],
    edges: [{ from: 'x', to: 'y.left', label: 'dispatch' }, { from: 'y', to: 'z', dashed: true }],
    steps: [
      { title: 'One', caption: 'The first.', nodes: ['x', 'y'], edges: ['x->y'] },
      { title: 'Two', caption: 'The second.', nodes: ['z'] },
    ],
  }
}

describe('validateDiagram', () => {
  it('accepts a well-formed diagram and fills the defaults', () => {
    const diagram = validateDiagram(minimal(), 'sample')
    assert.equal(diagram.columns[1].color, 'neutral')
    assert.equal(diagram.columns[0].width, 260)
    assert.equal(diagram.nodes[0].span, 1)
    assert.equal(diagram.nodes[0].row, 1)
    assert.equal(diagram.nodes[0].col, 'a')
    assert.deepEqual(diagram.edges.map(edge => edge.id), ['x->y', 'y->z'])
    assert.equal(diagram.edges[0].toSide, 'left')
    assert.equal(diagram.edges[0].fromSide, null)
    assert.equal(diagram.edges[1].dashed, true)
    assert.equal(diagram.nodes[2].planned, 'phase-2')
    assert.equal(diagram.steps.length, 2)
  })

  it('names the file when the id differs', () => {
    assert.throws(() => validateDiagram(minimal(), 'other'), /other: 'id' is 'sample', the file name says 'other'/)
  })

  it('rejects a taken cell, an unknown column, and a span that leaves the grid', () => {
    const raw = minimal()
    ;(raw.nodes as Record<string, unknown>[]).push({ id: 'w', title: 'W', kind: 'actor', at: 'a,1' })
    assert.throws(() => validateDiagram(raw, 'sample'), /cell a,1 is taken by node 'x'/)
    const unknown = minimal()
    ;(unknown.nodes as Record<string, unknown>[])[0].at = 'q,1'
    assert.throws(() => validateDiagram(unknown, 'sample'), /column 'q' is not declared/)
    const wide = minimal()
    ;(wide.nodes as Record<string, unknown>[])[1].span = 2
    assert.throws(() => validateDiagram(wide, 'sample'), /span 2 from column 'b' leaves the grid/)
  })

  it('rejects a self edge, a duplicate edge, and a step that names an unknown edge', () => {
    const self = minimal()
    ;(self.edges as Record<string, unknown>[]).push({ from: 'x', to: 'x' })
    assert.throws(() => validateDiagram(self, 'sample'), /cannot start and end on 'x'/)
    const twice = minimal()
    ;(twice.edges as Record<string, unknown>[]).push({ from: 'x', to: 'y', label: 'again' })
    assert.throws(() => validateDiagram(twice, 'sample'), /edge 'x->y' is declared twice/)
    const step = minimal()
    ;(step.steps as Record<string, unknown>[])[0].edges = ['x->z']
    assert.throws(() => validateDiagram(step, 'sample'), /steps\[1\]: edge 'x->z' is not declared/)
  })

  it('rejects a part overlap of groups and accepts a nested one', () => {
    const nested = minimal()
    nested.groups = [
      { id: 'g', title: 'G', cells: 'a,1 .. b,2' },
      { id: 'h', title: 'H', cells: 'b,1 .. b,2', color: 'blue' },
    ]
    assert.equal(validateDiagram(nested, 'sample').groups.length, 2)
    const partial = minimal()
    partial.groups = [
      { id: 'g', title: 'G', cells: 'a,1 .. b,1' },
      { id: 'h', title: 'H', cells: 'b,1 .. b,2' },
    ]
    assert.throws(() => validateDiagram(partial, 'sample'), /groups 'g' and 'h' overlap in part/)
  })

  it('enforces the length limits and the node count', () => {
    const long = minimal()
    ;(long.nodes as Record<string, unknown>[])[0].title = 'x'.repeat(41)
    assert.throws(() => validateDiagram(long, 'sample'), /'title' is 41 characters, the limit is 40/)
    const many = minimal()
    for (let index = 0; index < 11; index++) {
      ;(many.nodes as Record<string, unknown>[]).push({ id: `n${index}`, title: 'N', kind: 'actor', at: `a,${index + 2}` })
    }
    assert.throws(() => validateDiagram(many, 'sample'), /14 nodes, the limit is 12/)
  })

  it('lists every finding in one error', () => {
    const raw = minimal()
    delete raw.title
    ;(raw.nodes as Record<string, unknown>[])[0].kind = 'blob'
    try {
      validateDiagram(raw, 'sample')
      assert.fail('expected an error')
    }
    catch (error) {
      const message = (error as Error).message
      assert.match(message, /'title' is required/)
      assert.match(message, /'kind' must be one of/)
    }
  })

  it('needs a source for a line and rejects an unknown key', () => {
    const raw = minimal()
    ;(raw.nodes as Record<string, unknown>[])[0].line = 5
    raw.extra = true
    try {
      validateDiagram(raw, 'sample')
      assert.fail('expected an error')
    }
    catch (error) {
      const message = (error as Error).message
      assert.match(message, /'line' needs 'source'/)
      assert.match(message, /unknown top-level key 'extra'/)
    }
  })

  it('keeps a flowStep and gives null for a step without one', () => {
    const raw = minimal()
    ;(raw.steps as Record<string, unknown>[])[0].flowStep = 'flow-a/step-b'
    const diagram = validateDiagram(raw, 'sample')
    assert.equal(diagram.steps[0].flowStep, 'flow-a/step-b')
    assert.equal(diagram.steps[1].flowStep, null)
    assert.deepEqual(parseFlowStepRef('flow-a/step-b'), { flow: 'flow-a', step: 'step-b' })
  })

  it('rejects a flowStep that is not one flow id and one step id', () => {
    for (const value of ['nope', 'a/b/c']) {
      const raw = minimal()
      ;(raw.steps as Record<string, unknown>[])[0].flowStep = value
      assert.throws(() => validateDiagram(raw, 'sample'), new RegExp(`steps\\[1\\]: 'flowStep' must be '<flow-id>/<step-id>', got '${value}'`))
      assert.equal(parseFlowStepRef(value), null)
    }
  })

  it('rejects an unknown step key, so a typo cannot drop a binding', () => {
    const raw = minimal()
    ;(raw.steps as Record<string, unknown>[])[1].flowstep = 'flow-a/step-b'
    assert.throws(() => validateDiagram(raw, 'sample'), /steps\[2\]: unknown step key 'flowstep'/)
  })
})

describe('the air-diagram fence', () => {
  it('parses the id and the options', () => {
    const { spec, errors } = parseAirDiagramFence('id: terminal-channel\nstep: 2  # the claim\nheight: 420\ntitle: The channel\n')
    assert.deepEqual(errors, [])
    assert.deepEqual(spec, { id: 'terminal-channel', step: 2, height: 420, title: 'The channel' })
  })

  it('reports a missing id, a bad step, and an unknown key', () => {
    const { spec, errors } = parseAirDiagramFence('step: 0\nfoo: bar\n')
    assert.equal(spec, null)
    assert.deepEqual(errors, [`invalid step '0' (expected 1–9)`, `unknown key 'foo' (expected id, step, height, or title)`, `missing 'id:'`])
  })

  it('finds every fence with its line', () => {
    const source = '# Doc\n\n```air-diagram\nid: one\n```\n\ntext\n\n```air-view\nscope: modules\n```\n\n```air-diagram\nid: two\nstep: 3\n```\n'
    const fences = findAirDiagramFences(source)
    assert.deepEqual(fences.map(fence => [fence.line, parseAirDiagramFence(fence.body).spec?.id]), [[3, 'one'], [13, 'two']])
  })

  it('builds the edge key a step uses', () => {
    assert.equal(edgeId('svc', 'store'), 'svc->store')
  })
})
