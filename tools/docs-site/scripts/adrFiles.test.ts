import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { checkDecisionReferences, checkDecisionRecords, type DecisionConfig, parseDecisionFileName, parseDecisionRecord, readDecisionRecords } from './adrFiles.ts'
import { temporaryTree } from './tempTree.ts'

const NUMBERED = '---\ntopic: testing\n---\n\n# 12. The lane is a module\n\nDate: 2026-09-01\n\n## Status\n\nAccepted.\n'
const DATED = '---\ntopic: testing\n---\n\n# A lease prefers the warm slot\n\nDate: 2026-10-12\n\n## Status\n\nAccepted.\n'

const DECISION_FOLDERS = ['plugins/air/docs/decisions', 'community/tools/vm/docs/decisions']

function config(allowDuplicateNumbers: string[] = []): DecisionConfig {
  return {
    decisions: {
      folders: DECISION_FOLDERS,
      lastFrozenNumber: 235,
      referenceRoots: ['plugins/air'],
      skipDirs: [],
      topics: [{ id: 'testing', label: 'Tests' }],
      hiddenTopic: null,
      allowDuplicateNumbers,
    },
  }
}

function repo(files: Record<string, string>): string {
  return temporaryTree('adr-files-', files, DECISION_FOLDERS)
}

describe('a decision file name', () => {
  it('is a frozen number or a date, with a slug', () => {
    assert.deepStrictEqual(parseDecisionFileName('0012-the-lane-is-a-module.md', 235), { number: '0012', date: null, slug: 'the-lane-is-a-module' })
    assert.deepStrictEqual(parseDecisionFileName('2026-10-12-a-lease-prefers-the-warm-slot.md', 235), { number: null, date: '2026-10-12', slug: 'a-lease-prefers-the-warm-slot' })
  })

  it('rejects a new number, a date that is no date, and another shape', () => {
    assert.match((parseDecisionFileName('0236-a-new-record.md', 235) as { error: string }).error, /YYYY-MM-DD-a-new-record\.md/)
    assert.match((parseDecisionFileName('2026-13-40-a-record.md', 235) as { error: string }).error, /calendar date/)
    assert.match((parseDecisionFileName('A-Record.md', 235) as { error: string }).error, /lower-case slug/)
    assert.match((parseDecisionFileName('the-record.md', 235) as { error: string }).error, /NNNN-slug\.md/)
  })
})

describe('a decision record', () => {
  it('keeps the H1 number of a frozen record equal to the file number', () => {
    const parsed = parseDecisionRecord('d/0012-the-lane-is-a-module.md', '0012-the-lane-is-a-module.md', NUMBERED, 235)
    assert.ok('record' in parsed)
    assert.equal(parsed.record.title, 'The lane is a module')
    assert.equal(parsed.record.id, '0012-the-lane-is-a-module')
    const wrong = parseDecisionRecord('d/0013-x.md', '0013-x.md', NUMBERED, 235)
    assert.ok('errors' in wrong)
    assert.match(wrong.errors[0], /starts with "13\. "/)
  })

  it('wants no number in the H1 of a dated record, and a Date line equal to the prefix', () => {
    const parsed = parseDecisionRecord('d/2026-10-12-a.md', '2026-10-12-a.md', DATED, 235)
    assert.ok('record' in parsed)
    assert.equal(parsed.record.date, '2026-10-12')
    assert.equal(parsed.record.number, null)
    const numberedHeading = parseDecisionRecord('d/2026-10-12-a.md', '2026-10-12-a.md', DATED.replace('# A lease', '# 236. A lease'), 235)
    assert.ok('errors' in numberedHeading)
    assert.match(numberedHeading.errors[0], /no number in its H1/)
    const otherDate = parseDecisionRecord('d/2026-10-11-a.md', '2026-10-11-a.md', DATED, 235)
    assert.ok('errors' in otherDate)
    assert.match(otherDate.errors[0], /Date line says 2026-10-12, the file name says 2026-10-11/)
    const noDate = parseDecisionRecord('d/2026-10-12-a.md', '2026-10-12-a.md', DATED.replace('Date: 2026-10-12\n', ''), 235)
    assert.ok('errors' in noDate)
    assert.match(noDate.errors[0], /has a "Date: 2026-10-12" line/)
  })
})

describe('the decision folders', () => {
  it('read both folders and skip the README', () => {
    const root = repo({
      [`${DECISION_FOLDERS[0]}/0012-the-lane-is-a-module.md`]: NUMBERED,
      [`${DECISION_FOLDERS[1]}/2026-10-12-a-lease-prefers-the-warm-slot.md`]: DATED,
      [`${DECISION_FOLDERS[1]}/README.md`]: '# Records\n',
    })
    assert.deepStrictEqual(readDecisionRecords(config(), root).map(record => record.id), ['0012-the-lane-is-a-module', '2026-10-12-a-lease-prefers-the-warm-slot'])
  })

  it('reject a number or a slug that two records use, across the folders', () => {
    const root = repo({
      [`${DECISION_FOLDERS[0]}/0012-the-lane-is-a-module.md`]: NUMBERED,
      [`${DECISION_FOLDERS[1]}/0012-the-lane-is-a-module.md`]: NUMBERED,
      [`${DECISION_FOLDERS[0]}/2026-10-12-a-lease-prefers-the-warm-slot.md`]: DATED,
      [`${DECISION_FOLDERS[1]}/2026-10-11-a-lease-prefers-the-warm-slot.md`]: DATED.replace('2026-10-12', '2026-10-11'),
    })
    assert.throws(() => readDecisionRecords(config(), root), (error: Error) => {
      assert.match(error.message, /community\/tools\/vm\/docs\/decisions\/0012-the-lane-is-a-module\.md: number 0012 is also the number of plugins\/air/)
      assert.match(error.message, /slug "a-lease-prefers-the-warm-slot" is also the slug of/)
      return true
    })
  })
})

describe('the references', () => {
  it('find a link to a missing record and a mention of a missing number, outside a fenced block', () => {
    const root = repo({
      [`${DECISION_FOLDERS[0]}/0012-the-lane-is-a-module.md`]: NUMBERED,
      'plugins/air/spec/a.spec.md': [
        'See [ADR 0012](../docs/decisions/0012-the-lane-is-a-module.md) and [ADR 0013](../docs/decisions/0013-gone.md).',
        'ADR 0014 is gone too, and ADR 12 is the lane.',
        '```',
        'ADR 0099 inside a fence is an example',
        '```',
        'A [guide](../docs/guides/missing.md) is no decision link.',
      ].join('\n'),
      'plugins/air/docs/src/a.ts': '// ADR 0015 in TypeScript\n',
    })
    const findings = checkDecisionReferences(readDecisionRecords(config(), root), config(), root).map(finding => `${finding.path}:${finding.line}: ${finding.message}`)
    assert.deepStrictEqual(findings, [
      'plugins/air/docs/src/a.ts:1: "ADR 0015" names no decision record',
      'plugins/air/spec/a.spec.md:1: "ADR 0013" names no decision record',
      'plugins/air/spec/a.spec.md:1: the link "../docs/decisions/0013-gone.md" names no decision record',
      'plugins/air/spec/a.spec.md:2: "ADR 0014" names no decision record',
    ])
    assert.throws(() => checkDecisionRecords(config(), root), /4 stale reference\(s\)/)
  })

  it('let a number of allowDuplicateNumbers name two records, and find a mention of it ambiguous unless it links the record', () => {
    const files = {
      [`${DECISION_FOLDERS[0]}/0012-the-lane-is-a-module.md`]: NUMBERED,
      [`${DECISION_FOLDERS[1]}/0012-the-lane-is-a-crate.md`]: NUMBERED.replace('a module', 'a crate'),
      'plugins/air/spec/a.spec.md': [
        'See [the lane](../docs/decisions/0012-the-lane-is-a-module.md).',
        'ADR 0012 is the lane.',
        'See [ADR 0012](../docs/decisions/0012-the-lane-is-a-module.md) and [ADR 0012](../../../community/tools/vm/docs/decisions/0012-the-lane-is-a-crate.md).',
        'See [ADR 0012 and a guide](../docs/guides/a.md).',
      ].join('\n'),
    }
    assert.throws(() => readDecisionRecords(config(), repo(files)), /number 0012 is also the number of/)
    const root = repo(files)
    const records = readDecisionRecords(config(['0012']), root)
    assert.equal(records.length, 2)
    assert.deepStrictEqual(checkDecisionReferences(records, config(['0012']), root).map(finding => `${finding.path}:${finding.line}: ${finding.message}`), [
      'plugins/air/spec/a.spec.md:2: "ADR 0012" is ambiguous: 2 records take the number; link the record',
      'plugins/air/spec/a.spec.md:4: "ADR 0012" is ambiguous: 2 records take the number; link the record',
    ])
  })
})
