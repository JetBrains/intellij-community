import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { archiveDesignTitle, decisionMeta } from './docMeta.ts'

const TOPICS = [{ id: 'docs-site', label: 'Docs site' }]

describe('the design of an archived change', () => {
  it('takes the H1 of the design, else the H1 of the proposal, else the slug, and skips a generic H1', () => {
    assert.equal(archiveDesignTitle('# The host keeps one channel\n\n## Context\n', '# A proposal\n', 'one-channel'), 'The host keeps one channel')
    assert.equal(archiveDesignTitle('# Design\n\n## Context\n', '# A view keeps its frame\n', 'a-view-frame'), 'A view keeps its frame')
    assert.equal(archiveDesignTitle('# Design\n', '# Proposal\n\n## Why\n', 'gates-stay-out'), 'gates-stay-out')
    assert.equal(archiveDesignTitle('## Context\n\n```sh\n# a comment\n```\n', null, 'no-heading'), 'no-heading')
  })

  it('is a decision with the date of its folder and its former ids, and fails on a folder without a date or an unknown topic', () => {
    assert.deepStrictEqual(decisionMeta('openspec/changes/archive/2026-11-20-x/design.md', '2026-11-20-x', 'docs-site', TOPICS, ['0197-x']), {
      date: '2026-11-20',
      topic: 'docs-site',
      change: '2026-11-20-x',
      formerIds: ['0197-x'],
    })
    assert.deepStrictEqual(decisionMeta('d/design.md', '2026-11-20-x', null, TOPICS), { date: '2026-11-20', topic: null, change: '2026-11-20-x', formerIds: [] })
    assert.throws(() => decisionMeta('d/design.md', 'x', null, TOPICS), /starts with a date/)
    assert.throws(() => decisionMeta('d/design.md', '2026-11-20-x', 'misc', TOPICS), /"x-topic: misc" of \.openspec\.yaml is unknown/)
  })
})
