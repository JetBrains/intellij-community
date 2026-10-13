import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { adrDisplayTitle, adrLabel, type AdrMeta, adrNumberedTitle, adrOrderKey, adrStatus, adrStatusText, adrSupersededBy, parseAdrMeta } from './docMeta.ts'

const TOPICS = [{ id: 'docs-site', label: 'Docs site' }]

describe('decision record metadata', () => {
  it('drops the number prefix of every title style', () => {
    assert.equal(adrDisplayTitle('197. A session is state'), 'A session is state')
    assert.equal(adrDisplayTitle('0111. Cloud runs launch'), 'Cloud runs launch')
    assert.equal(adrDisplayTitle('ADR 0001 — Use LikeC4'), 'Use LikeC4')
    assert.equal(adrDisplayTitle('A title without a number'), 'A title without a number')
  })

  it('reads the status paragraph under the heading or a status line', () => {
    assert.equal(adrStatusText('# 1. T\n\n## Status\n\nAccepted. Amends\nADR 0013.\n\n## Context\n'), 'Accepted. Amends ADR 0013.')
    assert.equal(adrStatusText('# 15. T\n\nStatus: Accepted\n'), 'Accepted')
    assert.equal(adrStatusText('# 2. T\n\nNo status here.\n'), null)
  })

  it('tells a full supersession from a partial one', () => {
    assert.equal(adrStatus('Accepted. Supersedes ADR 0144.'), 'accepted')
    assert.equal(adrStatus('Proposed. Tracked in IJAI-3053.'), 'proposed')
    assert.equal(adrStatus('Superseded by [ADR 0002](0002-a.md).'), 'superseded')
    assert.equal(adrStatus('**Superseded on 2026-09-26 by [ADR 0059](0059-r.md)**, which moves the controller'), 'superseded')
    assert.equal(adrStatus('**Superseded on 2026-09-26 by [ADR 0059](0059-r.md)** for the language: the verbs stay'), 'partly-superseded')
    assert.equal(adrStatus('Accepted. Superseded in part by [ADR 0138](0138-a.md).'), 'partly-superseded')
    assert.equal(adrStatus('Accepted. Superseded for cloud launch creation by [ADR 0038](0038-c.md).'), 'partly-superseded')
    assert.equal(adrStatus('Draft.'), null)
  })

  it('finds the superseding record after the word, not an earlier link', () => {
    assert.equal(adrSupersededBy('Accepted. Amends [ADR 0009](0009-a.md). Superseded in part by [ADR 0127](./0127-b.md#x).'), '0127-b.md')
    assert.equal(adrSupersededBy('Superseded, see the spec.'), null)
  })

  it('fails on a record with no known status or topic', () => {
    const body = '# 9. T\n\nDate: 2026-07-01\n\n## Status\n\nAccepted.\n'
    assert.deepStrictEqual(parseAdrMeta('docs/decisions/0009-t.md', body, 'docs-site', TOPICS), {
      number: '0009',
      status: 'accepted',
      statusText: 'Accepted.',
      date: '2026-07-01',
      topic: 'docs-site',
      supersededBy: null,
    })
    assert.throws(() => parseAdrMeta('docs/decisions/0009-t.md', body, undefined, TOPICS), /topic/)
    assert.throws(() => parseAdrMeta('docs/decisions/0009-t.md', body, 'misc', TOPICS), /topic/)
    assert.throws(() => parseAdrMeta('docs/decisions/0009-t.md', '# 9. T\n', 'docs-site', TOPICS), /Status/)
    assert.throws(() => parseAdrMeta('docs/decisions/t.md', body, 'docs-site', TOPICS), /YYYY-MM-DD/)
  })

  it('reads a dated record: no number, and the date from the file name', () => {
    const body = '# T\n\n## Status\n\nSuperseded by [the next](2026-11-01-next.md).\n'
    assert.deepStrictEqual(parseAdrMeta('docs/decisions/2026-10-12-t.md', body, 'docs-site', TOPICS), {
      number: null,
      status: 'superseded',
      statusText: 'Superseded by the next.',
      date: '2026-10-12',
      topic: 'docs-site',
      supersededBy: 'docs/decisions/2026-11-01-next.md',
    })
    assert.equal(adrLabel({ number: null, date: '2026-10-12' } as AdrMeta), '2026-10-12')
    assert.equal(adrLabel({ number: '0197', date: null } as AdrMeta), 'ADR 0197')
    assert.equal(adrNumberedTitle({ number: '0197' } as AdrMeta, 'T'), '197. T')
    assert.equal(adrNumberedTitle({ number: null } as AdrMeta, 'T'), 'T')
    assert.ok(adrOrderKey({ number: null, date: '2026-10-12' } as AdrMeta) > adrOrderKey({ number: '0235' } as AdrMeta))
  })
})
