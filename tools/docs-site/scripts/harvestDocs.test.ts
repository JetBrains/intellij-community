import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { type CheckRun, createCheckRun } from './checkRun.ts'
import { harvestDocs } from './harvestDocs.ts'
import type { ModelProvider } from './modelProvider.ts'
import { temporaryTree } from './tempTree.ts'
import { ARCHIVE_FILES, testSiteConfig, withOpenSpecRoot } from './testSiteConfig.ts'

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

  it('reads the design of each archived change as a decision, with the date and the topic of the change', () => {
    const root = temporaryTree('harvest-docs-', { ...FILES, ...ARCHIVE_FILES })
    const harvest = harvestDocs(withOpenSpecRoot(testSiteConfig()), provider(), { root })
    const designs = harvest.docs.filter(doc => doc.adr?.change !== undefined)
    assert.deepStrictEqual(designs.map(doc => ({ path: doc.path, title: doc.title, kind: doc.kind, adr: doc.adr })), [
      {
        path: 'openspec/changes/archive/2026-11-20-one-channel/design.md',
        title: 'The host keeps one channel',
        kind: 'adr',
        adr: { number: null, status: 'accepted', statusText: '', date: '2026-11-20', topic: 'docs-site', supersededBy: null, change: '2026-11-20-one-channel' },
      },
      // a generic H1 takes the H1 of the proposal, and a design with no H1 and no proposal takes the slug
      {
        path: 'openspec/changes/archive/2026-11-21-a-view-frame/design.md',
        title: 'A view keeps its frame',
        kind: 'adr',
        adr: { number: null, status: 'accepted', statusText: '', date: '2026-11-21', topic: null, supersededBy: null, change: '2026-11-21-a-view-frame' },
      },
      {
        path: 'openspec/changes/archive/2026-11-22-no-heading/design.md',
        title: 'no-heading',
        kind: 'adr',
        adr: { number: null, status: 'accepted', statusText: '', date: '2026-11-22', topic: null, supersededBy: null, change: '2026-11-22-no-heading' },
      },
    ])
    // the designs follow the decision records and come before the extra docs
    assert.deepStrictEqual(harvest.docs.map(doc => doc.path).slice(-5), [
      'docs/decisions/0001-a-record.md',
      ...designs.map(doc => doc.path),
      'README.md',
    ])
  })

  it('reads a design of an OpenSpec root outside the content root, with a doc path that climbs out of it', () => {
    const files = Object.fromEntries(Object.entries({ ...FILES, ...ARCHIVE_FILES }).map(([path, text]) => [path.startsWith('openspec/') ? `tools/vm/${path}` : `site/${path}`, text]))
    const root = temporaryTree('harvest-docs-', files)
    const config = testSiteConfig({ contentRoot: 'site' })
    const openspec = { roots: ['tools/vm/openspec'], templateSections: ['Purpose'] }
    const site = { ...config, decisions: { ...config.decisions, folders: ['site/docs/decisions'] }, specs: { ...config.specs, openspec } }
    const harvest = harvestDocs(site, provider(), { root })
    const design = harvest.docs.find(doc => doc.adr?.change === '2026-11-20-one-channel')
    assert.equal(design?.path, '../tools/vm/openspec/changes/archive/2026-11-20-one-channel/design.md')
    assert.equal(design?.title, 'The host keeps one channel')
    assert.ok(harvest.bodies.get(design.path)?.includes('The host keeps one channel.'))
  })

  it('names the topics of the config for a design with an unknown x-topic', () => {
    const root = temporaryTree('harvest-docs-', { ...FILES, ...ARCHIVE_FILES, 'openspec/changes/archive/2026-11-22-no-heading/.openspec.yaml': 'x-topic: misc\n' })
    assert.throws(() => harvestDocs(withOpenSpecRoot(testSiteConfig()), provider(), { root }),
      /2026-11-22-no-heading\/design\.md: "x-topic: misc" of \.openspec\.yaml is unknown; use one of docs-site/)
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

  it('resolves the code spans on the first call, and keeps the answer', () => {
    const guide = '---\ngroup: docs\n---\n\n# A guide\n\nSee `src/Gone.kt`.\n'
    const root = temporaryTree('harvest-docs-', { ...FILES, 'docs/guides/g.md': guide })
    const harvest = harvestDocs(testSiteConfig(), provider(), { root })
    const paths = harvest.codeSpanPaths()
    assert.deepStrictEqual(paths, new Map([['docs/guides', new Map([['src/Gone.kt', null]])]]))
    assert.equal(harvest.codeSpanPaths(), paths)
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
