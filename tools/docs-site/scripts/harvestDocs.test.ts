import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { type CheckRun, createCheckRun } from './checkRun.ts'
import { harvestDocs } from './harvestDocs.ts'
import type { ModelProvider } from './modelProvider.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'

const DIAGRAM = `id: flow
title: The flow
summary: The launcher starts the IDE.
columns:
  - id: a
    title: Build
nodes:
  - id: launcher
    title: Launcher
    kind: target
    at: a,1
    target: //build:launcher
  - id: main
    title: Main
    kind: service
    at: a,2
    source: src/Main.kt
    line: 7
steps:
  - title: Start
    caption: The launcher runs main.
    nodes: [launcher, main]
`

const FILES: Record<string, string> = {
  'docs/README.md': '# Home\n\nThe site.\n',
  'docs/a-note.md': '---\nstate: active\n---\n\n# A note\n\nText.\n',
  'docs/guides/g.md': '---\ngroup: docs\n---\n\n# A guide\n\nText.\n',
  'docs/explainers/e.md': '---\ntitle: E\n---\n\n# E\n\n## Start\n\nThe launcher runs.\n\n```diagram\nid: flow\n```\n',
  'docs/decisions/0001-a-record.md': '---\ntopic: docs-site\n---\n\n# 1. A record\n\n## Status\n\nAccepted.\n',
  'docs/diagrams/flow.diagram.yaml': DIAGRAM,
  'README.md': '# Readme\n\nThe tree.\n',
}

function provider(overrides: Partial<ModelProvider> = {}): ModelProvider & { bundled: string[] } {
  const bundled: string[] = []
  return {
    bundled,
    resolveSymbol: () => null,
    resolveSource: path => path === 'src/Main.kt' ? { file: path, line: 1 } : null,
    resolveTarget: label => label === '//build:launcher' ? { file: 'build/BUILD.bazel', line: 12 } : null,
    hasModule: () => false,
    hasSpec: () => false,
    hasConcept: () => false,
    bundleSources(files) {
      bundled.push(...files)
    },
    ...overrides,
  }
}

describe('the doc harvest', () => {
  it('reads the folders of the config in order and resolves a target and a source', () => {
    const root = temporaryTree('harvest-docs-', FILES)
    const model = provider()
    const seen: string[] = []
    const harvest = harvestDocs(testSiteConfig(), model, { root, onDoc: path => seen.push(path) })
    assert.deepStrictEqual(harvest.docs.map(doc => [doc.path, doc.kind]), [
      ['docs/README.md', 'home'],
      ['docs/a-note.md', 'note'],
      ['docs/guides/g.md', 'guide'],
      ['docs/explainers/e.md', 'explainer'],
      ['docs/decisions/0001-a-record.md', 'adr'],
      ['README.md', 'readme'],
    ])
    assert.deepStrictEqual(seen, harvest.docs.map(doc => doc.path))
    assert.deepStrictEqual([...harvest.bodies.keys()], seen)
    const [diagram] = harvest.diagrams
    assert.deepStrictEqual(diagram.nodes[0].targetRef, { file: 'build/BUILD.bazel', line: 12 })
    assert.deepStrictEqual(diagram.nodes[1].sourceRef, { file: 'src/Main.kt', line: 7 })
    assert.equal('targetRef' in diagram.nodes[1], false)
    assert.deepStrictEqual(diagram.steps.map(step => step.flowRef), [null])
    assert.deepStrictEqual(model.bundled, ['build/BUILD.bazel', 'src/Main.kt'])
  })

  it('rejects a target that the site cannot resolve', () => {
    const root = temporaryTree('harvest-docs-', FILES)
    assert.throws(() => harvestDocs(testSiteConfig(), provider({ resolveTarget: undefined }), { root }),
      /^Error: harvest: docs\/diagrams\/flow\.diagram\.yaml: node 'launcher': target `\/\/build:launcher` needs a site that resolves Bazel targets$/)
    assert.throws(() => harvestDocs(testSiteConfig(), provider({ resolveTarget: () => null }), { root }),
      /node 'launcher': target `\/\/build:launcher` names no Bazel target/)
    assert.throws(() => harvestDocs(testSiteConfig(), provider({ resolveSource: () => null }), { root }),
      /node 'main': source `src\/Main\.kt` does not exist under \./)
  })

  it('rejects a flowStep in a site without flows', () => {
    const root = temporaryTree('harvest-docs-', {
      ...FILES,
      'docs/diagrams/flow.diagram.yaml': `${DIAGRAM}    flowStep: flow-a/step-b\n`,
    })
    assert.throws(() => harvestDocs(testSiteConfig(), provider(), { root }), /steps\[1\]: flowStep `flow-a\/step-b` needs a site with flows/)
  })

  it('names the guide groups of the config for a guide with an unknown group', () => {
    const root = temporaryTree('harvest-docs-', { ...FILES, 'docs/guides/g.md': '---\ngroup: misc\n---\n\n# A guide\n' })
    assert.throws(() => harvestDocs(testSiteConfig(), provider(), { root }), /docs\/guides\/g\.md: frontmatter "group:" is missing or unknown; use one of docs/)
  })

  it('takes each check from the check run of the caller, once', () => {
    const root = temporaryTree('harvest-docs-', FILES)
    const config = testSiteConfig()
    const run = createCheckRun(config, root)
    const records = run.decisions()
    const calls: string[] = []
    function spy<T>(name: string, check: () => T): () => T {
      return () => {
        calls.push(name)
        return check()
      }
    }
    const checks: CheckRun = {
      ...run,
      decisions: spy('decisions', run.decisions),
      diagramFiles: spy('diagramFiles', run.diagramFiles),
      prose: spy('prose', run.prose),
      vocabulary: spy('vocabulary', run.vocabulary),
      specRefs: spy('specRefs', run.specRefs),
    }
    harvestDocs(config, provider(), { root, checks })
    assert.deepStrictEqual(calls, ['decisions', 'diagramFiles', 'prose', 'vocabulary'])
    // the run keeps the result of the first call, so the harvest read no record again
    assert.equal(run.decisions(), records)
  })

  it('refuses a check run of another tree', () => {
    const root = temporaryTree('harvest-docs-', FILES)
    const other = temporaryTree('harvest-docs-', FILES)
    const config = testSiteConfig()
    assert.throws(() => harvestDocs(config, provider(), { root, checks: createCheckRun(config, other) }), /the check run of .* does not belong to/)
  })
})
