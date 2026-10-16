// A small site config for the kernel tests. The content root is the root of the temporary tree of a test, so a
// test writes `docs/explainers/x.md` and the check reads it there.

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
