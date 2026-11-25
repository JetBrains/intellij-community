// A small site config for the kernel tests. The content root is the root of the temporary tree of a test, so a
// test writes `docs/explainers/x.md` and the check reads it there. `withOpenSpecRoot` adds the OpenSpec root
// `openspec`, and `ARCHIVE_FILES` holds the archived changes of a test.

import type { SiteConfig } from './siteConfig.ts'

/** A site config with the content root `.`, three node kinds, two planned values, and the default history marks. */
export function testSiteConfig(overrides: Partial<SiteConfig> = {}): SiteConfig {
  return {
    id: 'test',
    title: 'Test site',
    brandMark: 'T',
    base: '/test/',
    siteRoot: 'docs',
    contentRoot: '.',
    outDir: 'out/test-site',
    docs: {
      root: 'docs',
      home: 'docs/README.md',
      notes: 'docs',
      guides: 'docs/guides',
      explainers: 'docs/explainers',
      diagrams: 'docs/diagrams',
      glossary: null,
      decisions: ['docs/decisions'],
      extraReadmes: ['README.md'],
      skipDirs: [],
    },
    specs: { roots: ['spec'], suffix: '.spec.md', style: null, templateSections: null },
    sourceRoots: ['.'],
    github: { repoBase: 'https://github.com/example/repo/blob/master/', pathPrefix: '' },
    themeStorageKey: 'test-docs-theme',
    decisions: {
      folders: ['docs/decisions'],
      lastFrozenNumber: 9,
      referenceRoots: ['docs'],
      skipDirs: [],
      topics: [{ id: 'docs-site', label: 'Docs site' }],
      hiddenTopic: null,
      allowDuplicateNumbers: [],
    },
    guideGroups: [{ id: 'docs', label: 'Docs' }],
    diagram: {
      kinds: [
        { id: 'service', label: 'Service', glyph: null, className: 'border-border' },
        { id: 'module', label: 'Module', glyph: null, className: 'border-border', mono: true },
        { id: 'target', label: 'Target', glyph: null, className: 'border-dashed' },
      ],
      planned: [
        { id: 'phase-2', badge: 'Planned · phase 2', cardText: 'Planned for phase 2.' },
        { id: 'open', badge: 'Open decision', cardText: 'Open.' },
      ],
    },
    prose: { maxSentenceWords: 25, maxCardWords: 150, guide: null },
    vocabulary: null,
    launchPicker: null,
    ...overrides,
  }
}

/** The OpenSpec root of `withOpenSpecRoot`. */
export const TEST_OPENSPEC_ROOT = 'openspec'

/** `config` with the OpenSpec root `openspec`, whose archive holds the designs of a test. */
export function withOpenSpecRoot(config: SiteConfig): SiteConfig {
  return { ...config, specs: { ...config.specs, openspec: { roots: [TEST_OPENSPEC_ROOT], templateSections: ['Purpose', 'Requirements'] } } }
}

/**
 * The archived changes of a test under `openspec`: a design with an `x-topic`, a design with the generic H1 `# Design`
 * and a proposal, a design with no H1, and a change with no design.
 */
export const ARCHIVE_FILES: Readonly<Record<string, string>> = {
  'openspec/config.yaml': 'schema: spec-driven\n',
  'openspec/changes/archive/2026-11-20-one-channel/.openspec.yaml': 'schema: spec-driven\ncreated: 2026-11-20\nx-topic: docs-site\nx-adr: ["0001"]\n',
  'openspec/changes/archive/2026-11-20-one-channel/design.md':
    '# The host keeps one channel\n\n## Context\n\nThe host keeps one channel.\n\n## Decisions\n\nThe view keeps [its frame](../2026-11-21-a-view-frame/design.md).\n',
  'openspec/changes/archive/2026-11-21-a-view-frame/.openspec.yaml': 'schema: spec-driven\ncreated: 2026-11-21\n',
  'openspec/changes/archive/2026-11-21-a-view-frame/design.md': '# Design\n\n## Context\n\nThe view keeps its frame.\n',
  'openspec/changes/archive/2026-11-21-a-view-frame/proposal.md': '# A view keeps its frame\n\n## Why\n\nThe frame stays.\n',
  'openspec/changes/archive/2026-11-22-no-heading/design.md': '## Context\n\nThe text.\n',
  'openspec/changes/archive/2026-11-23-no-design/proposal.md': '## Why\n\nThe text.\n',
}
