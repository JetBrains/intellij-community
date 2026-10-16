import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { harvestSpecs } from './harvestSpecs.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'

const SPEC = `---
name: Composition
description: How the composer joins components.
style: plain-1
targets:
  - ../src/*.rs
---

# Composition

Status: Active
Date: 2026-10-05

## Summary

The composer joins components.

[@test] ../tests/ComposeTest.kt
[@test] ../tests/Missing.kt

## Requirements
`

const FILES: Record<string, string> = {
  'build/spec/composition.spec.md': SPEC,
  'build/spec/actions/new.spec.md': '---\nname: New\n---\n\n# New\n',
  'build/legacy.spec.md': '# Legacy\n\nNo frontmatter.\n',
  'build/node_modules/pkg/x.spec.md': '# Skipped\n',
  'build/tests/ComposeTest.kt': 'class ComposeTest\n',
}

const config = testSiteConfig({ contentRoot: '', specs: { roots: ['build/spec', 'build'], suffix: '.spec.md', style: 'plain-1', templateSections: null } })

describe('harvestSpecs', () => {
  it('gives each spec to the first root that holds it, and skips a dependency folder', () => {
    const root = temporaryTree('harvest-specs-', FILES)
    const { specs } = harvestSpecs(config, root)
    assert.deepEqual(specs.map(spec => [spec.id, spec.path, spec.group]), [
      ['legacy', 'build/legacy.spec.md', 'spec'],
      ['actions/new', 'build/spec/actions/new.spec.md', 'actions'],
      ['composition', 'build/spec/composition.spec.md', 'spec'],
    ])
  })

  it('reads the frontmatter, the status and date lines, the headings, and the existing test targets', () => {
    const root = temporaryTree('harvest-specs-', FILES)
    const { specs, bodies } = harvestSpecs(config, root)
    const spec = specs.find(candidate => candidate.id === 'composition')!
    assert.deepEqual(spec, {
      id: 'composition',
      path: 'build/spec/composition.spec.md',
      group: 'spec',
      name: 'Composition',
      description: 'How the composer joins components.',
      status: 'Active',
      date: '2026-10-05',
      style: 'plain-1',
      targets: ['../src/*.rs'],
      testFiles: ['build/tests/ComposeTest.kt'],
      headings: ['Summary', 'Requirements'],
    })
    // the body is the source after the frontmatter
    assert.match(bodies.get(spec.path)!, /^\n# Composition\n/)
    const legacy = specs.find(candidate => candidate.id === 'legacy')!
    assert.equal(legacy.name, 'legacy')
    assert.equal(legacy.style, null)
  })
})
