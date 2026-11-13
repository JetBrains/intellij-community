import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { openspecDeltaFiles, specFiles } from './paths.ts'
import { openspecRequirementSentences, specRefFindings, type SpecRefsConfig } from './specRefs.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'

const SECTIONS = ['Purpose', 'Goals', 'Non-goals', 'Concepts', 'Requirements', 'References']

/** A site with one OpenSpec root, `os/openspec`, and the suffix roots `roots`. */
function config(roots: string[] = []): SpecRefsConfig {
  const site = testSiteConfig()
  return { contentRoot: site.contentRoot, docs: site.docs, specs: { ...site.specs, roots, openspec: { roots: ['os/openspec'], templateSections: SECTIONS } } }
}

function spec(body: string, frontmatter = 'name: Cache\ndescription: The caches.\nstyle: openspec-1\ntargets:\n  - src/A.kt'): string {
  return `---\n${frontmatter}\n---\n\n# Cache\n\nStatus: Active\nDate: 2026-11-10\n\n${body}`
}

const VALID = `## Purpose

The spec states which caches keep the output of an action.

## Requirements

### Requirement: The caches: A local build reads only

A local build MUST read the remote cache and write nothing to it.

#### Scenario: A local build
- **WHEN** a local build runs
- **THEN** it writes nothing
  [@test] src/A.kt (\`reads only\`)

### Requirement: The caches: A CI build writes

A CI build MUST write each result.

\`\`\`text
# a fenced line is no heading
\`\`\`

#### Scenario: The policy table
- **WHEN** a CI build runs an action
- **THEN** the action follows its row
  | Action | Remote |
  |---|---|
  | \`Pack\` | no |

## References

- [The guide](../../../../docs/guides/guide.md) and [the caches](#the-caches).
`

/** A tree with one source file, one guide, and the given files. */
function repo(files: Record<string, string>): string {
  return temporaryTree('openspec-refs-', { 'src/A.kt': 'class A\n', 'docs/guides/guide.md': '# Guide\n\n## The first part\n', 'os/openspec/config.yaml': 'schema: spec-driven\n', ...files })
}

describe('the spec files of an OpenSpec root', () => {
  it('take the capability as the id, nested or flat, and read the paths from the repository root', () => {
    const root = repo({
      'os/openspec/specs/cache/spec.md': spec(VALID),
      'os/openspec/specs/area/nested/spec.md': spec(VALID),
      'os/openspec/specs/cache/notes.md': '# Notes\n',
      'spec/b.spec.md': '---\ntargets:\n  - ../src/A.kt\n---\n\n# B\n',
    })
    assert.deepEqual(specFiles(config(['spec']), root).map(({ id, rootPaths }) => ({ id, rootPaths })), [
      { id: 'area/nested', rootPaths: true },
      { id: 'cache', rootPaths: true },
      { id: 'b', rootPaths: false },
    ])
  })

  it('refuse one id in two roots, so a moved spec cannot keep its old file', () => {
    const root = repo({ 'os/openspec/specs/cache/spec.md': spec(VALID), 'spec/cache.spec.md': '# Old\n' })
    assert.throws(() => specFiles(config(['spec']), root), /spec id\(s\) name two files; delete the old file of a moved spec\n'cache': os\/openspec\/specs\/cache\/spec.md and spec\/cache.spec.md/)
  })

  it('give the deltas of the active changes with the folder of their main spec, and skip the archive', () => {
    const root = repo({
      'os/openspec/changes/one/specs/cache/spec.md': '## ADDED Requirements\n',
      'os/openspec/changes/archive/2026-01-01-old/specs/cache/spec.md': '## ADDED Requirements\n',
    })
    assert.deepEqual(openspecDeltaFiles(config(), root).map(({ file, capabilityDir }) => [file.slice(root.length + 1), capabilityDir.slice(root.length + 1)]), [
      ['os/openspec/changes/one/specs/cache/spec.md', 'os/openspec/specs/cache'],
    ])
  })
})

describe('the requirement sentences of an OpenSpec spec', () => {
  it('are the prose and the list items between a requirement heading and its first scenario', () => {
    const sentences = openspecRequirementSentences(spec(`## Purpose\n\nNot a rule.\n\n## Requirements\n\n### Requirement: G: R\n\nThe rule MUST hold.\n\n- A list item of the rule.\n\n#### Scenario: S\n- **WHEN** it runs\n`))
    assert.deepEqual(sentences.map(({ text }) => text), ['The rule MUST hold.', 'A list item of the rule.'])
  })
})

describe('the OpenSpec rules of the spec check', () => {
  it('pass a valid root, and a link to a group names its first requirement', () => {
    const root = repo({
      'os/openspec/specs/cache/spec.md': spec(VALID),
      'docs/guides/other.md': '# Other\n\n[The caches](../../os/openspec/specs/cache/spec.md#the-caches)\n',
    })
    assert.deepEqual(specRefFindings(config(), root).findings, [])
  })

  it('name the file, the line and the rule of each broken rule', () => {
    const requirement = Array.from({ length: 21 }, (_, i) => `r${i}`).join(' ')
    const root = repo({
      'os/openspec/specs/cache/spec.md': spec(`## Purpose

The spec states the caches.

## Requirements

### Requirement: No group here

A rule MUST hold.

### Requirement: First: One

A rule MUST hold. ${requirement}.
  [@test] src/A.kt

### Requirement: Second: Two

A rule MUST hold.

| A | B |
|---|---|

### Requirement: First: One

A rule MUST hold.

### Requirement: Purpose: A group named as a section

A rule MUST hold.

## Design

Text.
`, 'name: Cache\ndescription: d\nstyle: plain-1\ntargets:\n  - src/Gone.kt'),
    })
    assert.deepEqual(specRefFindings(config(), root).findings.map(finding => finding.replace(/budget; split it: .*$/, 'budget').replace(/;.*$/, '')), [
      `os/openspec/specs/cache/spec.md:6: targets: 'src/Gone.kt' matches no existing file`,
      `os/openspec/specs/cache/spec.md:1: style: a spec of an OpenSpec root declares 'style: openspec-1'`,
      // `r0` starts no new sentence, so the rule and the words after it are one sentence
      'os/openspec/specs/cache/spec.md:26: requirement: 25 words exceed the 20-word budget',
      `os/openspec/specs/cache/spec.md:20: heading: 'Requirement: No group here' is not 'Requirement: <Group>: <behaviour>'`,
      'os/openspec/specs/cache/spec.md:27: requirement: a [@test] line stands in the text of a requirement',
      'os/openspec/specs/cache/spec.md:33: requirement: a table stands in the text of a requirement',
      `os/openspec/specs/cache/spec.md:36: heading: 'Requirement: First: One' repeats the requirement of line 24`,
      `os/openspec/specs/cache/spec.md:36: heading: the requirements of the group 'First' stand apart`,
      `os/openspec/specs/cache/spec.md:40: group: the group 'Purpose' has the slug of the section on line 14, so '#purpose' is ambiguous`,
      `os/openspec/specs/cache/spec.md:44: section: 'Design' is not a template section`,
    ])
  })

  it('check a delta from the folder of its main spec, without a frontmatter, and skip the archive', () => {
    const root = repo({
      'os/openspec/specs/cache/spec.md': spec(VALID),
      'os/openspec/changes/one/specs/cache/spec.md': `## ADDED Requirements

### Requirement: The caches: A delta adds a rule

A delta MUST add a rule. See [the guide](../../../../docs/guides/guide.md) and [a gone page](gone.md).

#### Scenario: A delta
- **WHEN** the archive runs
- **THEN** the rule lands
  [@test] src/Gone.kt
`,
      'os/openspec/changes/archive/2026-01-01-old/specs/cache/spec.md': '## ADDED Requirements\n\n[a gone page](gone.md)\n',
    })
    assert.deepEqual(specRefFindings(config(), root).findings, [
      `os/openspec/changes/one/specs/cache/spec.md:10: test-link: [@test] 'src/Gone.kt' matches no existing file`,
      `os/openspec/changes/one/specs/cache/spec.md:5: link: 'gone.md' points to a missing file`,
    ])
  })
})

describe('the scan of an OpenSpec root', () => {
  it('passes a root with its config.yaml and no spec yet, when the site has no suffix root', () => {
    assert.deepEqual(specRefFindings(config(), repo({})).findings, [])
  })

  it('reports a root without config.yaml, because its path names no OpenSpec root', () => {
    const root = temporaryTree('openspec-refs-', { 'docs/guides/guide.md': '# Guide\n' })
    assert.deepEqual(specRefFindings(config(), root).findings, ['os/openspec: no config.yaml; the path names no OpenSpec root, so the scan is broken'])
  })

  it('reports a spec.md under specs/ that the scan does not give, because the scan is broken then', () => {
    // a spec.md right under specs/ names no capability, so the scan does not give it
    const root = repo({ 'os/openspec/specs/spec.md': spec(VALID) })
    assert.deepEqual(specRefFindings(config(), root).findings, ['os/openspec: specs/ holds 1 spec.md file(s), and the scan found no spec; the scan is broken'])
  })

  it('reports a site with a suffix root and no spec, also when its OpenSpec root has config.yaml', () => {
    assert.deepEqual(specRefFindings(config(['spec']), repo({})).findings, ['os/openspec, spec: no spec file; the scan is broken'])
  })
})
