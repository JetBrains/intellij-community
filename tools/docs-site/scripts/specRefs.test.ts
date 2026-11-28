import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { checkSpecRefs, frontmatterStyle, frontmatterTargets, globToRegex, headingSlug, proseSentences, requirementSentences, specRefFindings, type SpecRefsConfig } from './specRefs.ts'
import { temporaryTree } from './tempTree.ts'
import { ARCHIVE_FILES, testSiteConfig, withOpenSpecRoot } from './testSiteConfig.ts'

const TEMPLATE = ['Summary', 'Requirements', 'References']

function config(style: 'plain-1' | null = null, roots: string[] = ['spec']): SpecRefsConfig {
  const site = testSiteConfig()
  return { contentRoot: site.contentRoot, docs: site.docs, specs: { ...site.specs, roots, style, templateSections: style === null ? null : TEMPLATE } }
}

function spec(body: string, frontmatter = 'targets:\n  - ../src/A.kt\nstyle: plain-1'): string {
  return `---\n${frontmatter}\n---\n\n# A spec\n\n${body}`
}

/** A tree with one source file, one guide, and the given files. */
function repo(files: Record<string, string>): string {
  return temporaryTree('spec-refs-', { 'src/A.kt': 'class A\n', 'src/deep/B.kt': 'class B\n', 'docs/guides/guide.md': '# Guide\n\n## The first part\n', ...files })
}

describe('a path glob', () => {
  it('matches inside one segment with a single star', () => {
    const regex = globToRegex('*.kt')
    assert.ok(regex.test('A.kt'))
    assert.ok(!regex.test('deep/B.kt'))
  })

  it('crosses segments with a double star, and a double star with a slash can match no directory', () => {
    const regex = globToRegex('**/*.kt')
    assert.ok(regex.test('A.kt'))
    assert.ok(regex.test('deep/B.kt'))
    assert.ok(globToRegex('src/**').test('src/deep/B.kt'))
  })

  it('matches one character with a question mark, and takes a dot as a dot', () => {
    assert.ok(globToRegex('A?kt').test('A.kt'))
    assert.ok(!globToRegex('A.kt').test('Axkt'))
    assert.ok(!globToRegex('a?b').test('a/b'))
  })
})

describe('a heading slug', () => {
  it('is the GitHub slug: lower case, letters, digits, hyphens and underscores, each space a hyphen', () => {
    assert.equal(headingSlug('Testing / Local Run'), 'testing--local-run')
    assert.equal(headingSlug('Data & Backend'), 'data--backend')
    assert.equal(headingSlug('`dist_plugin` macro'), 'dist_plugin-macro')
    assert.equal(headingSlug('ADR 0063: the launcher'), 'adr-0063-the-launcher')
    // a letter outside ASCII stays
    assert.equal(headingSlug('\u00dcberblick'), '\u00fcberblick')
  })
})

describe('the sentences of a spec', () => {
  it('join soft-wrapped lines and split at a sentence end, not at `e.g.` or a version', () => {
    const sentences = proseSentences(spec('The packer reads the jar,\nfor example e.g. the 0.131.0 one. It writes the file.\n'))
    assert.deepEqual(sentences.map(({ line, text }) => ({ line, text })), [
      { line: 9, text: 'The packer reads the jar, for example e.g. the 0.131.0 one.' },
      { line: 10, text: 'It writes the file.' },
    ])
    assert.equal(sentences[0].wordCount, 11)
  })

  it('skip the frontmatter, fenced code, headings and `[@test]` lines, and take each table cell as a block', () => {
    const text = spec('```\nnot prose here at all\n```\n\n[@test] ../src/A.kt\n\n| Name | What |\n|---|---|\n| The packer | Packs the jar. |\n')
    assert.deepEqual(proseSentences(text).map(({ line, text }) => `${line}:${text}`), ['15:Name', '15:What', '17:The packer', '17:Packs the jar.'])
  })

  it('take a requirement from a list item under Requirements only', () => {
    const text = spec('## Summary\n\n- Not a requirement.\n\n## Requirements\n\nThe prose explains.\n\n1. The packer packs\n   the jar.\n- The composer composes.\n')
    assert.deepEqual(requirementSentences(text).map(({ line, text }) => `${line}:${text}`), ['17:The packer packs the jar.', '19:The composer composes.'])
  })
})

describe('the frontmatter of a spec', () => {
  it('gives the targets with their lines, and the style', () => {
    const text = spec('Body.\n', 'name: A\ntargets:\n  - ../src/A.kt\n  - ../src/**/*.kt\nstyle: "plain-1"')
    assert.deepEqual(frontmatterTargets(text), [{ target: '../src/A.kt', line: 4 }, { target: '../src/**/*.kt', line: 5 }])
    assert.equal(frontmatterStyle(text), 'plain-1')
    assert.equal(frontmatterTargets('# No frontmatter\n'), null)
    assert.equal(frontmatterStyle('# No frontmatter\n'), null)
  })
})

describe('the spec reference check', () => {
  it('passes a tree whose references resolve, and reports what it read', () => {
    const root = repo({
      'spec/a.spec.md': spec('[@test] ../src/A.kt\n\nSee [the guide](../docs/guides/guide.md#the-first-part) and [the top](#a-spec).\n', 'targets:\n  - ../src/**/*.kt'),
    })
    assert.deepEqual(checkSpecRefs(config(), root), {
      specs: [{ id: 'a', path: 'spec/a.spec.md' }],
      proseDocs: ['docs/guides/guide.md'],
      linkTargets: ['docs/guides/guide.md', 'spec/a.spec.md'],
    })
  })

  it('names the file, the line and the rule of each broken reference', () => {
    const root = repo({
      'spec/a.spec.md': spec('[@test] ../src/Gone.kt\n\nSee [the guide](../docs/guides/gone.md) and [a part](../docs/guides/guide.md#no-part).\n', 'targets:\n  - ../src/A.kt\n  - ../src/**/*.java'),
      'spec/b.spec.md': '# No frontmatter\n',
      'docs/guides/other.md': '# Other\n\n[The spec](../../spec/c.spec.md)\n',
    })
    assert.deepEqual(specRefFindings(config(), root).findings, [
      `spec/a.spec.md:4: targets: '../src/**/*.java' matches no existing file`,
      `spec/a.spec.md:9: test-link: [@test] '../src/Gone.kt' matches no existing file`,
      `spec/a.spec.md:11: link: '../docs/guides/gone.md' points to a missing file`,
      `spec/a.spec.md:11: anchor: '../docs/guides/guide.md#no-part' names no heading of docs/guides/guide.md`,
      'spec/b.spec.md:1: targets: the spec has no YAML frontmatter',
      `docs/guides/other.md:3: link: '../../spec/c.spec.md' points to a missing file`,
    ])
    assert.throws(() => checkSpecRefs(config(), root), /^Error: specRefs: 6 finding\(s\)/)
  })

  it('reports each test name of a [@test] link that the file does not declare', () => {
    const root = repo({
      'src/ATest.kt': '@Test fun `a sentence name`() {}\n@Test fun bareName() {}\n',
      'spec/a.spec.md': spec('- The packer packs.\n  [@test] ../src/ATest.kt (`a sentence name`, `gone name`,\n  `bareName`)\n- The glob names no test.\n  [@test] ../src/*Test.kt (`gone too`)\n'),
    })
    assert.deepEqual(specRefFindings(config(), root).findings, [
      `spec/a.spec.md:10: test-name: [@test] '../src/ATest.kt' names 'gone name', which that file declares nowhere; rename the link, or write the test`,
    ])
  })

  it('reports a backlink of a Kotlin or Java source that names no file, and reads no source without source roots', () => {
    const root = repo({
      'spec/a.spec.md': spec('Short.\n'),
      'src/Backlinks.kt': 'package a\n\n// @spec spec/a.spec.md\n  // @spec spec/gone.spec.md\nval text = "// @spec spec/inline.spec.md"\n',
      'src/Old.java': '// @spec spec/old.spec.md\nclass Old {}\n',
      'src/notes.md': '// @spec spec/gone.spec.md\n',
      'src/node_modules/x/Dep.kt': '// @spec spec/gone.spec.md\n',
    })
    assert.deepEqual(specRefFindings(config(), root).findings, [])
    assert.deepEqual(specRefFindings({ ...config(), sourceRoots: ['src'] }, root).findings, [
      `src/Backlinks.kt:4: backlink: '// @spec spec/gone.spec.md' names no existing spec; fix the path, or delete the backlink`,
      `src/Old.java:1: backlink: '// @spec spec/old.spec.md' names no existing spec; fix the path, or delete the backlink`,
    ])
  })

  it('reports a test in a proposed section, and a pointer of another spec that drops the mark', () => {
    const root = repo({
      'spec/a.spec.md': spec('## Requirements\n\n### One way in (Proposed)\n\n- A prompt is a row.\n  [@test] ../src/A.kt\n'),
      'spec/b.spec.md': spec('- See [a](a.spec.md), "One way in".\n'),
    })
    assert.deepEqual(specRefFindings(config(), root).findings.map(finding => finding.slice(0, finding.indexOf(';'))), [
      'spec/a.spec.md:14: proposal: a [@test] link stands in the proposed section "One way in (Proposed)"',
      `spec/b.spec.md:9: proposal: names the proposed section "One way in" without ' (Proposed)'`,
    ])
  })

  it('checks the budgets, the sections and the style of a plain-1 site, and only the references of a free site', () => {
    const long = Array.from({ length: 26 }, (_, i) => `w${i}`).join(' ')
    const requirement = Array.from({ length: 21 }, (_, i) => `r${i}`).join(' ')
    const root = repo({
      'spec/a.spec.md': spec(`## Summary\n\n${long}.\n\n## Requirements\n\n- ${requirement}.\n\n## Design\n\nShort.\n`),
      'spec/b.spec.md': spec('Short.\n', 'targets:\n  - ../src/A.kt'),
      'other/c.spec.md': spec(`${long}.\n`, 'targets:\n  - ../src/A.kt'),
    })
    assert.deepEqual(specRefFindings(config(null, ['spec', 'other']), root).findings, [])
    const findings = specRefFindings(config('plain-1', ['spec', 'other']), root).findings
    assert.deepEqual(findings.map(finding => finding.replace(/budget; split it: .*$/, 'budget').replace(/ use one of .*$/, '')), [
      `other/c.spec.md:1: style: a spec of a suffix root declares 'style: plain-1'; see community/.ai/spec/SPEC_GUIDE.md 'Writing Style'`,
      'spec/a.spec.md:11: sentence: 26 words exceed the 25-word budget',
      'spec/a.spec.md:15: requirement: 21 words exceed the 20-word budget',
      `spec/a.spec.md:17: section: 'Design' is not a template section;`,
      `spec/b.spec.md:1: style: a spec of a suffix root declares 'style: plain-1'; see community/.ai/spec/SPEC_GUIDE.md 'Writing Style'`,
    ])
  })

  it('checks no requirement budget when the site turns it off, and keeps the sentence budget', () => {
    const long = Array.from({ length: 26 }, (_, i) => `w${i}`).join(' ')
    const requirement = Array.from({ length: 21 }, (_, i) => `r${i}`).join(' ')
    const root = repo({ 'spec/a.spec.md': spec(`## Requirements\n\n- ${requirement}.\n- ${long}.\n`) })
    const plain = config('plain-1')
    const findings = specRefFindings({ ...plain, specs: { ...plain.specs, requirementBudget: false } }, root).findings
    assert.deepEqual(findings.map(finding => finding.replace(/budget; split it: .*$/, 'budget')), [
      'spec/a.spec.md:12: sentence: 26 words exceed the 25-word budget',
    ])
  })

  it('counts a code span as one word, as the prose check does', () => {
    const words = (count: number): string => Array.from({ length: count }, (_, i) => `w${i}`).join(' ')
    const root = repo({ 'spec/a.spec.md': spec(`## Summary\n\n${words(24)} \`Do not show again\`.\n`) })
    assert.deepEqual(specRefFindings(config('plain-1'), root).findings, [])
    const long = repo({ 'spec/a.spec.md': spec(`## Summary\n\n${words(28)}.\n`) })
    assert.deepEqual(specRefFindings(config('plain-1'), long).findings.map(finding => finding.replace(/budget; split it: .*$/, 'budget')), [
      'spec/a.spec.md:11: sentence: 28 words exceed the 25-word budget',
    ])
  })

  it('reads the links of a design with an x-topic, and not of a design without one', () => {
    const site = withOpenSpecRoot(testSiteConfig())
    const openspec: SpecRefsConfig = { contentRoot: site.contentRoot, docs: site.docs, specs: site.specs }
    const root = repo({
      ...ARCHIVE_FILES,
      'spec/a.spec.md': spec('Short.\n', 'targets:\n  - ../src/A.kt'),
      'openspec/changes/archive/2026-11-20-one-channel/design.md': '# The host keeps one channel\n\nSee [the gone design](../2026-11-19-gone/design.md).\n',
      // a design without an x-topic wrote its links for the folder of the active change
      'openspec/changes/archive/2026-11-22-no-heading/design.md': 'See [the guide](../../docs/guides/gone.md).\n',
    })
    const { proseDocs, findings } = specRefFindings(openspec, root)
    assert.deepEqual(proseDocs, ['docs/guides/guide.md', 'openspec/changes/archive/2026-11-20-one-channel/design.md'])
    assert.deepEqual(findings, [`openspec/changes/archive/2026-11-20-one-channel/design.md:3: link: '../2026-11-19-gone/design.md' points to a missing file`])
  })

  it('reports a scan that finds no spec', () => {
    const root = repo({})
    assert.deepEqual(specRefFindings(config(), root).findings, ['spec: no spec file; the scan is broken'])
  })
})
