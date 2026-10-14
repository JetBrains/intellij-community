import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { VocabularyRule } from './siteConfig.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'
import {
  checkVocabulary,
  diagramVocabularyFindings,
  formatVocabularyFinding,
  markdownVocabularyFindings,
  readVocabularyTexts,
  vocabularyPattern,
} from './vocabulary.ts'

const COMPOSER: VocabularyRule = { term: 'composer', replacement: 'the placer', reason: 'a test rule', allowed: { docs: ['docs/decisions/*'] } }

function lines(rules: readonly VocabularyRule[], source: string, path = 'docs/guides/g.md'): (number | null)[] {
  return markdownVocabularyFindings(rules, path, source).map(finding => finding.line)
}

describe('the term pattern', () => {
  it('matches the term and its plural as whole words, in any case', () => {
    const pattern = vocabularyPattern('fragment')
    assert.deepEqual('A fragment, two Fragments, fragmentation, prefragment, fragment_id.'.match(pattern), ['fragment', 'Fragments'])
  })

  it('matches a term of two words across a line break, and not across a hyphen', () => {
    const pattern = vocabularyPattern('dev launcher')
    assert.deepEqual('the dev\nlauncher and the dev-launcher'.match(pattern), ['dev\nlauncher'])
  })

  it('uses Unicode word boundaries', () => {
    assert.equal('\u00e9fragment'.match(vocabularyPattern('fragment')), null)
  })
})

describe('the vocabulary of a markdown file', () => {
  it('reports each line of a term once, with its line and section', () => {
    const findings = markdownVocabularyFindings([COMPOSER], 'docs/guides/g.md', '# G\n\n## Place\n\nThe composer and the composers.\n')
    assert.equal(findings.length, 1)
    assert.equal(findings[0].line, 5)
    assert.equal(findings[0].section, 'docs/guides/g.md#place')
    assert.equal(formatVocabularyFinding(findings[0]), 'docs/guides/g.md:5: "composer" → use "the placer" (a test rule): The composer and the composers.')
  })

  it('skips the frontmatter, a fenced block, an HTML comment, a code span, a link target and a URL', () => {
    const source = [
      '---',
      'description: the composer',
      '---',
      '```',
      'composer',
      '```',
      '<!-- composer -->',
      'The `composer` tool, [the tool](composer.md), and https://example.com/composer.',
    ].join('\n')
    assert.deepEqual(lines([COMPOSER], source), [])
  })

  it('reports the label of a link, and a line after the frontmatter keeps its file line', () => {
    assert.deepEqual(lines([COMPOSER], '---\ntitle: X\n---\n\nSee [the composer](../spec/x.md).\n'), [5])
  })

  it('allows a term in a doc, in a section with its subsections, and in a link label to a path', () => {
    const rule: VocabularyRule = {
      term: 'composer',
      replacement: null,
      reason: 'a test rule',
      allowed: { docs: ['docs/decisions/*'], sections: ['docs/guides/g.md#legacy'], linkPaths: ['docs/notes.md'] },
    }
    const source = '## Legacy\n\nThe composer.\n\n### Older\n\nThe composer.\n\n## Today\n\nThe [old composer](../notes.md#x), the composer.\n'
    assert.deepEqual(lines([rule], source), [11])
    assert.deepEqual(lines([rule], source, 'docs/decisions/0001-x.md'), [])
    assert.match(formatVocabularyFinding(markdownVocabularyFindings([rule], 'docs/guides/g.md', source)[0]), /→ say it in other words \(a test rule\)/)
  })
})

describe('the vocabulary of a diagram', () => {
  it('reads the title, text, detail and caption fields, and not the summary', () => {
    const source = [
      'id: d',
      'title: The composer',
      'summary: The composer places files.',
      'nodes:',
      '  - id: a',
      '    title: A',
      '    text: The `composer` tool',
      '    detail: |',
      '      One line.',
      '      The composer places.',
      'steps:',
      '  - title: One',
      '    caption: The composer runs.',
    ].join('\n')
    const findings = diagramVocabularyFindings([COMPOSER], 'docs/diagrams/d.diagram.yaml', source)
    assert.deepEqual(findings.map(finding => finding.field), ['title', 'node a, detail', 'step One, caption'])
    assert.deepEqual(findings.map(finding => finding.line), [2, 10, 13])
    assert.deepEqual(diagramVocabularyFindings([COMPOSER], 'docs/decisions/d.diagram.yaml', source), [])
  })
})

describe('the vocabulary check of a site', () => {
  // a guide is a violation, a decision record is allowed
  it('reports a term outside its scope, and its scope allows it', () => {
    const root = temporaryTree('vocabulary-', {
      'docs/guides/g.md': '---\ngroup: docs\n---\n\n# G\n\nThe composer places files.\n',
      'docs/decisions/0001-x.md': '# 1. X\n\nThe composer was the placer.\n',
      'docs/diagrams/d.diagram.yaml': 'id: d\ntitle: The composer\n',
      'spec/x.spec.md': '# X\n\nThe composers run.\n',
      'README.md': 'The composer.\n',
    })
    const config = testSiteConfig({ vocabulary: [COMPOSER] })
    assert.deepEqual(readVocabularyTexts(config, root).map(text => text.path), [
      'docs/guides/g.md',
      'docs/decisions/0001-x.md',
      'README.md',
      'spec/x.spec.md',
      'docs/diagrams/d.diagram.yaml',
    ])
    assert.throws(() => checkVocabulary(config, undefined, root), (error: Error) => {
      assert.match(error.message, /^vocabulary: 4 finding\(s\)/)
      assert.match(error.message, /docs\/guides\/g\.md:7: "composer"/)
      assert.match(error.message, /spec\/x\.spec\.md:3: "composer"/)
      assert.match(error.message, /docs\/diagrams\/d\.diagram\.yaml(:2)? \(title\): "composer"/)
      assert.doesNotMatch(error.message, /docs\/decisions\//)
      return true
    })
    assert.equal(checkVocabulary(testSiteConfig({ vocabulary: [{ ...COMPOSER, term: 'packer' }] }), undefined, root), 5)
    assert.equal(checkVocabulary(testSiteConfig(), undefined, root), 0)
  })

  it('reports an allowed section that names no heading', () => {
    const root = temporaryTree('vocabulary-', { 'docs/guides/g.md': '# G\n\n## Place\n\nText.\n' })
    const rule = { ...COMPOSER, allowed: { sections: ['docs/guides/g.md#place', 'docs/guides/g.md#gone'] } }
    assert.throws(() => checkVocabulary(testSiteConfig({ vocabulary: [rule] }), undefined, root), /^Error: vocabulary: 1 finding\(s\)\nthe rule "composer" allows the section docs\/guides\/g\.md#gone, which names no heading$/)
  })
})
