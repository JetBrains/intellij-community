import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { checkDecisionReferences, checkDecisions, type DecisionConfig, readDecisionDesigns } from './decisionFiles.ts'
import { temporaryTree } from './tempTree.ts'

const OPENSPEC_ROOT = 'plugins/air/openspec'
const ARCHIVE = `${OPENSPEC_ROOT}/changes/archive`
const DESIGN = '# The host keeps one channel\n\n## Context\n\nThe host keeps one channel.\n'

function config(): DecisionConfig {
  return {
    decisions: {
      referenceRoots: ['plugins/air'],
      skipDirs: [],
      topics: [{ id: 'testing', label: 'Tests' }],
      hiddenTopic: null,
    },
    specs: { roots: [], suffix: '.spec.md', style: null, templateSections: null, openspec: { roots: [OPENSPEC_ROOT], templateSections: [] } },
  }
}

function repo(files: Record<string, string>): string {
  return temporaryTree('decision-files-', files)
}

function findingLines(root: string): string[] {
  return checkDecisionReferences(config(), root).map(finding => `${finding.path}:${finding.line}: ${finding.message}`)
}

describe('the designs of the archived changes', () => {
  it('are the design files of the archive folders, with the topic and the old ids of .openspec.yaml', () => {
    const root = repo({
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'schema: spec-driven\ncreated: 2026-11-20\nx-topic: testing\nx-adr: ["0012", "2026-10-12-a-lease-prefers-the-warm-slot"]\n',
      [`${ARCHIVE}/2026-11-21-no-design/proposal.md`]: '## Why\n',
      [`${ARCHIVE}/2026-11-22-no-topic/design.md`]: DESIGN,
    })
    assert.deepStrictEqual(readDecisionDesigns(config(), root).map(({ id, date, slug, topic, adr }) => ({ id, date, slug, topic, adr })), [
      { id: '2026-11-20-one-channel', date: '2026-11-20', slug: 'one-channel', topic: 'testing', adr: ['0012', '2026-10-12-a-lease-prefers-the-warm-slot'] },
      { id: '2026-11-22-no-topic', date: '2026-11-22', slug: 'no-topic', topic: null, adr: [] },
    ])
  })

  it('reject a folder name without a calendar date and a topic that the site does not name', () => {
    const root = repo({
      [`${ARCHIVE}/One-Channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-13-40-a-date/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'x-topic: misc\n',
    })
    assert.throws(() => readDecisionDesigns(config(), root), (error: Error) => {
      assert.match(error.message, /3 invalid archived change\(s\)/)
      assert.match(error.message, /archive\/One-Channel\/design\.md: the folder of an archived change is YYYY-MM-DD-slug/)
      assert.match(error.message, /archive\/2026-13-40-a-date\/design\.md: the folder of an archived change is YYYY-MM-DD-slug/)
      assert.match(error.message, /archive\/2026-11-20-one-channel\/design\.md: "x-topic: misc" of \.openspec\.yaml is unknown; use one of testing/)
      return true
    })
  })
})

describe('the references', () => {
  it('answer a decision mention and a link to a design, outside a fenced block', () => {
    const root = repo({
      [`${ARCHIVE}/2026-11-20-one-channel/design.md`]: DESIGN,
      [`${ARCHIVE}/2026-11-20-one-channel/.openspec.yaml`]: 'x-topic: testing\n',
      // a design with a topic takes the link rule, and a design without one wrote its links for the active change
      [`${ARCHIVE}/2026-11-21-topic-links/design.md`]: '## Context\n\nSee [the channel](../2026-11-19-gone/design.md).\n',
      [`${ARCHIVE}/2026-11-21-topic-links/.openspec.yaml`]: 'x-topic: testing\n',
      [`${ARCHIVE}/2026-11-22-old-links/design.md`]: '## Context\n\nSee [the channel](../archive/2026-11-20-one-channel/design.md). ADR 0012 was its number.\n',
      'plugins/air/native/src/host.rs': [
        '// the decision 2026-11-20-one-channel keeps one channel',
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
    assert.deepStrictEqual(findingLines(root), [
      'plugins/air/native/src/host.rs:2: "Decision 2026-11-19-gone" names no archived change with a design',
      `${ARCHIVE}/2026-11-21-topic-links/design.md:3: the link "../2026-11-19-gone/design.md" names no design of an archived change`,
      'plugins/air/spec/a.spec.md:2: "decision 2026-11-18-gone-too" names no archived change with a design',
      'plugins/air/spec/a.spec.md:2: the link "../openspec/changes/archive/2026-11-19-gone/design.md" names no design of an archived change',
    ])
    assert.throws(() => checkDecisions(config(), root), /4 stale reference\(s\)/)
  })

  it('find a decision number and a link to a decision record file, and keep a link to a file that exists', () => {
    const root = repo({
      'plugins/air/spec/a.spec.md': [
        'See [ADR 0012](../docs/decisions/0012-the-lane-is-a-module.md) and ADRs 0013 and 0014.',
        'A record of another set [stays](../../other/docs/decisions/0001-kept.md), and ADR `0099` is a number too.',
        '```',
        'ADR 0098 inside a fence is an example',
        '```',
        'A [guide](../docs/guides/missing.md) is no decision link.',
      ].join('\n'),
      'plugins/other/docs/decisions/0001-kept.md': '# Kept\n',
      'plugins/air/docs/src/a.ts': '// ADR 0015 in TypeScript\n',
    })
    assert.deepStrictEqual(findingLines(root), [
      'plugins/air/docs/src/a.ts:1: "ADR 0015" names a decision by a number; name the design as decision <change-id>, or link it',
      'plugins/air/spec/a.spec.md:1: "ADR 0012" names a decision by a number; name the design as decision <change-id>, or link it',
      'plugins/air/spec/a.spec.md:1: "ADRs 0013" names a decision by a number; name the design as decision <change-id>, or link it',
      'plugins/air/spec/a.spec.md:1: the link "../docs/decisions/0012-the-lane-is-a-module.md" names a decision record file; link the design of the archived change',
      'plugins/air/spec/a.spec.md:2: "ADR `0099" names a decision by a number; name the design as decision <change-id>, or link it',
    ])
  })
})
