import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { checkDecisionReferences, checkDecisionRecords, type DecisionConfig, parseDecisionFileName, parseDecisionRecord, readArchiveDesigns, readDecisionRecords } from './adrFiles.ts'
import { temporaryTree } from './tempTree.ts'

const NUMBERED = '---\ntopic: testing\n---\n\n# 12. The lane is a module\n\nDate: 2026-09-01\n\n## Status\n\nAccepted.\n'
const DATED = '---\ntopic: testing\n---\n\n# A lease prefers the warm slot\n\nDate: 2026-10-12\n\n## Status\n\nAccepted.\n'

const DECISION_FOLDERS = ['plugins/air/docs/decisions', 'community/tools/vm/docs/decisions']
const OPENSPEC_ROOT = 'plugins/air/openspec'
const ARCHIVE = `${OPENSPEC_ROOT}/changes/archive`
const DESIGN = '# The host keeps one channel\n\n## Context\n\nThe host keeps one channel.\n'

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
    specs: { roots: [], suffix: '.spec.md', style: null, templateSections: null, openspec: { roots: [OPENSPEC_ROOT], templateSections: [] } },
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

describe('the designs of the archived changes', () => {
  it('are the design files of the archive folders, with the topic and the old ids of .openspec.yaml', () => {
    const root = repo({
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'schema: spec-driven\ncreated: 2026-11-20\nx-topic: testing\nx-adr: ["0012", "2026-10-12-a-lease-prefers-the-warm-slot"]\n',
      [`${ARCHIVE}/2026-11-21-no-design/proposal.md`]: '## Why\n',
      [`${ARCHIVE}/2026-11-22-no-topic/design.md`]: DESIGN,
    })
    assert.deepStrictEqual(readArchiveDesigns(config(), root).map(({ id, date, slug, topic, adr }) => ({ id, date, slug, topic, adr })), [
      { id: '2026-11-20-one-channel', date: '2026-11-20', slug: 'one-channel', topic: 'testing', adr: ['0012', '2026-10-12-a-lease-prefers-the-warm-slot'] },
      { id: '2026-11-22-no-topic', date: '2026-11-22', slug: 'no-topic', topic: null, adr: [] },
    ])
  })

  it('reject a folder name without a calendar date and a topic that the site does not name', () => {
    const root = repo({
      [`${ARCHIVE}/One-Channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'x-topic: misc\n',
    })
    assert.throws(() => readArchiveDesigns(config(), root), (error: Error) => {
      assert.match(error.message, /2 invalid archived change\(s\)/)
      assert.match(error.message, /archive\/One-Channel\/design\.md: the folder of an archived change is YYYY-MM-DD-slug/)
      assert.match(error.message, /archive\/2026-11-20-one-channel\/design\.md: "x-topic: misc" of \.openspec\.yaml is unknown; use one of testing/)
      return true
    })
  })

  it('answer a decision mention and a link to a design, and a dated record answers a mention too', () => {
    const root = repo({
      [`${DECISION_FOLDERS[1]}/2026-10-12-a-lease-prefers-the-warm-slot.md`]: DATED,
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'x-topic: testing\n',
      // a design with a topic takes the link rule, and a design without one wrote its links for the active change
      [`${ARCHIVE}/2026-11-21-topic-links/design.md`]: '## Context\n\nSee [the channel](../2026-11-19-gone/design.md).\n',
      [`${ARCHIVE}/2026-11-21-topic-links/.openspec.yaml`]: 'x-topic: testing\n',
      [`${ARCHIVE}/2026-11-22-old-links/design.md`]: '## Context\n\nSee [the channel](../archive/2026-11-20-one-channel/design.md).\n',
      'plugins/air/native/src/host.rs': [
        '// the decision 2026-11-20-one-channel keeps one channel',
        '// the decision 2026-10-12-a-lease-prefers-the-warm-slot still answers',
        '// Decision 2026-11-19-gone names no change',
      ].join('\n'),
      'plugins/air/spec/a.spec.md': [
        'See [the channel](../openspec/changes/archive/2026-11-20-one-channel/design.md).',
        'See [a gone design](../openspec/changes/archive/2026-11-19-gone/design.md) and the decision 2026-11-18-gone-too.',
        '```',
        'decision 2026-01-01-an-example inside a fence',
        '```',
      ].join('\n'),
    })
    const designs = readArchiveDesigns(config(), root)
    const findings = checkDecisionReferences(readDecisionRecords(config(), root), config(), root, designs).map(finding => `${finding.path}:${finding.line}: ${finding.message}`)
    assert.deepStrictEqual(findings, [
      'plugins/air/native/src/host.rs:3: "Decision 2026-11-19-gone" names no archived change with a design and no dated decision record',
      `${ARCHIVE}/2026-11-21-topic-links/design.md:3: the link "../2026-11-19-gone/design.md" names no design of an archived change`,
      'plugins/air/spec/a.spec.md:2: "decision 2026-11-18-gone-too" names no archived change with a design and no dated decision record',
      'plugins/air/spec/a.spec.md:2: the link "../openspec/changes/archive/2026-11-19-gone/design.md" names no design of an archived change',
    ])
    assert.throws(() => checkDecisionRecords(config(), root), /4 stale reference\(s\)/)
  })
})
