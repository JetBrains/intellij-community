import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, it } from 'node:test'
import { checkDiagramProse, checkExplainerProse, checkProse, countWords, formatProseFinding, type ProseRule, readProseFindings } from './proseLint.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'

/** The limits of the Air site: an issue id and a "Since IJAI" with no number are history marks too. */
const CONFIG = testSiteConfig({
  prose: { maxSentenceWords: 25, maxCardWords: 150, historyPattern: /\bIJAI-\d+\b|\b[Rr]ound \d+\b|\bSince IJAI\b(?!-\d)|\b[0-9a-f]{13}\b/g, guide: null },
})
const LIMITS = CONFIG.prose

function air(files: Record<string, string>): string {
  return temporaryTree('prose-lint-', files)
}

/** A sentence of `count` words. */
function words(count: number): string {
  return `${Array.from({ length: count }, (_, index) => `word${index}`).join(' ')}.`
}

function rules(findings: { rule: ProseRule }[]): ProseRule[] {
  return findings.map(finding => finding.rule)
}

const FRONTMATTER = '---\ntitle: X\ndescription: A description of the page that is longer than twenty-five words is not prose of the page, so the gate does not count the words of it at all.\n---\n'

describe('the prose of an explainer', () => {
  it('passes a short sentence', () => {
    assert.deepStrictEqual(checkExplainerProse('x.md', `${FRONTMATTER}\n# X\n\n${words(25)} ${words(3)}\n`, LIMITS), [])
  })

  it('reports a sentence over 25 words once, with its line', () => {
    const findings = checkExplainerProse('x.md', `${FRONTMATTER}\n# X\n\nShort one.\n${words(26)}\n`, LIMITS)
    assert.deepStrictEqual(rules(findings), ['sentence-length'])
    assert.equal(findings[0].line, 9)
    assert.match(formatProseFinding(findings[0]), /^x\.md:9: sentence-length: 26 words: "word0 word1/)
  })

  it('counts a sentence across the lines of a paragraph', () => {
    const findings = checkExplainerProse('x.md', `${words(20).slice(0, -1)}\n${words(6)}\n`, LIMITS)
    assert.deepStrictEqual(rules(findings), ['sentence-length'])
    assert.equal(findings[0].line, 1)
  })

  it('ends a sentence at the end of a list item', () => {
    const item = words(20).slice(0, -1)
    assert.deepStrictEqual(checkExplainerProse('x.md', `- ${item}\n- ${item}\n`, LIMITS), [])
  })

  it('skips a fenced block, a table row, and a heading', () => {
    const long = words(40)
    const source = `${FRONTMATTER}\n## ${long}\n\n\`\`\`air-diagram\nid: ${long} IJAI-1\n\`\`\`\n\n| ${long} | 18 actions |\n| --- | --- |\n`
    assert.deepStrictEqual(checkExplainerProse('x.md', source, LIMITS), [])
  })

  it('counts a code span as one word', () => {
    const span = '`a b c d e f g h i j`'
    assert.equal(countWords('two words'), 2)
    assert.deepStrictEqual(checkExplainerProse('x.md', `${words(24).slice(0, -1)} ${span}.\n`, LIMITS), [])
    assert.deepStrictEqual(rules(checkExplainerProse('x.md', `${words(25).slice(0, -1)} ${span}.\n`, LIMITS)), ['sentence-length'])
  })

  it('does not split a sentence at a dot in a code span or a decimal', () => {
    const source = `${words(12).slice(0, -1)} \`a. b\` 0.02 ${words(12)}\n`
    assert.deepStrictEqual(rules(checkExplainerProse('x.md', source, LIMITS)), ['sentence-length'])
  })

  it('reports an issue id, a round, and a commit hash once per sentence', () => {
    const findings = checkExplainerProse('x.md', 'Since IJAI-3663, the store writes the file.\nRound 4 moved it.\nCommit 47993e84da7a0 did it.\n', LIMITS)
    assert.deepStrictEqual(rules(findings), ['history', 'history', 'history'])
    assert.match(findings[0].excerpt, /^"IJAI-3663" in/)
  })

  it('passes a link to a decision record', () => {
    assert.deepStrictEqual(checkExplainerProse('x.md', 'The [record](../decisions/0197-a-session-is-state.md) holds the reason.\n', LIMITS), [])
  })

  it('reports a count that rots, and not a schema number', () => {
    assert.deepStrictEqual(rules(checkExplainerProse('x.md', 'The reducer takes 18 actions.\n', LIMITS)), ['rotting-count'])
    assert.deepStrictEqual(checkExplainerProse('x.md', 'Schema 1 files reached users.\n', LIMITS), [])
  })
})

const DIAGRAM = (detail: string, caption = 'The click opens the picker.'): string => `id: x
title: X
summary: One click starts one session.
nodes:
  - id: api
    title: Api
    text: one RPC call
    kind: rpc
    at: a,1
    detail: |
      ${detail}
steps:
  - title: Click
    caption: ${caption}
    nodes: [api]
`

describe('the prose of a diagram', () => {
  it('passes a short card', () => {
    assert.deepStrictEqual(checkDiagramProse('x.diagram.yaml', DIAGRAM('The one RPC that carries the request.'), LIMITS), [])
  })

  it('reports a long sentence of a card with the line of the field and the node', () => {
    const findings = checkDiagramProse('x.diagram.yaml', DIAGRAM(words(26)), LIMITS)
    assert.deepStrictEqual(rules(findings), ['sentence-length'])
    assert.equal(formatProseFinding(findings[0]).split(': ')[0], 'x.diagram.yaml:11 (node api, detail)')
  })

  it('reports a card over 150 words once', () => {
    const detail = Array.from({ length: 8 }, () => words(20)).join(' ')
    assert.deepStrictEqual(rules(checkDiagramProse('x.diagram.yaml', DIAGRAM(detail), LIMITS)), ['card-length'])
  })

  it('reports history and a rotting count in a caption', () => {
    const findings = checkDiagramProse('x.diagram.yaml', DIAGRAM('A card.', 'The reducer takes 18 actions since round 3.'), LIMITS)
    assert.deepStrictEqual(rules(findings), ['history', 'rotting-count'])
    assert.equal(findings[0].field, 'step Click, caption')
  })
})

describe('the prose gate', () => {
  it('reads the explainers and the diagrams under the root and throws with every finding', () => {
    const root = air({
      'docs/explainers/x.md': `${FRONTMATTER}\nThe host owns the state.\n`,
      'docs/diagrams/x.diagram.yaml': DIAGRAM('The one RPC that carries the request.'),
    })
    assert.equal(checkProse(CONFIG, root), 2)
    writeFileSync(join(root, 'docs/explainers/y.md'), 'IJAI-1 moved it.\n')
    assert.deepStrictEqual(readProseFindings(CONFIG, root).findings.map(formatProseFinding), ['docs/explainers/y.md:1: history: "IJAI-1" in "IJAI-1 moved it."'])
    assert.throws(() => checkProse(CONFIG, root), /prose: 1 finding\(s\)[\s\S]*docs\/explainers\/y\.md:1: history/)
  })
})
