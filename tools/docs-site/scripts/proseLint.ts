// The prose gate of the explainers and of the diagram cards of a site (the readable-docs guide of Air).
//
// An explainer and a card describe the shape of today for a reader at the whiteboard. The gate checks the four
// rules of that text that a machine can decide: a long sentence, a history mark (an issue id, a round, a commit
// hash), a card over the hard cap of ADR 0204, and a count that rots. The other lenses of the guide need a reviewer.
// The limits and the history marks come from `prose` of the site config.
//
// The gate reads the prose of the explainers (`docs.explainers`) and the `summary`, `text`, `detail`, and `caption`
// fields of the diagrams (`docs.diagrams`). It does not read the frontmatter, a fenced block, a table row, or a
// heading. A code span counts as one word.
//
// The check is one owner for three callers: the `check` script of a site, the gate of a site (the Air JUnit gate
// runs on bun), and the harvest. It throws with every finding.

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { withoutFencedBlocks } from './fence.ts'
import { contentDir, isMainModule, repoRoot, runCheck } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'
import { parseYaml } from './yamlCompat.ts'

/** The part of the site config that the check reads. */
export type ProseConfig = Pick<SiteConfig, 'contentRoot' | 'docs' | 'prose'>

/** The limits of the check: `prose` of the site config. */
export type ProseLimits = SiteConfig['prose']

export type ProseRule = 'sentence-length' | 'history' | 'card-length' | 'rotting-count'

export interface ProseFinding {
  /** The file, relative to the content root. */
  path: string
  /** The 1-based line, or null when the YAML parser of the runtime gives no positions. */
  line: number | null
  /** The field of a diagram, such as `node api, detail`, or null for an explainer. */
  field: string | null
  rule: ProseRule
  excerpt: string
}

/** The `<path>:<line> (<field>): <rule>: <excerpt>` line of one finding. */
export function formatProseFinding(finding: ProseFinding): string {
  const line = finding.line === null ? '' : `:${finding.line}`
  const field = finding.field === null ? '' : ` (${finding.field})`
  return `${finding.path}${line}${field}: ${finding.rule}: ${finding.excerpt}`
}

/** The history marks of a site that names none: a round and a bare commit hash. */
const DEFAULT_HISTORY = /\b[Rr]ound \d+\b|\b[0-9a-f]{13}\b/g

/** The history pattern of `limits` with the global flag, which `matchAll` needs. */
function historyPattern(limits: ProseLimits): RegExp {
  const pattern = limits.historyPattern ?? DEFAULT_HISTORY
  return pattern.global ? pattern : new RegExp(pattern.source, `${pattern.flags}g`)
}
/** A count of parts that rots. A number that names a schema, a version, a phase, or an ADR is no count. */
const ROTTING_COUNT = /(?<!\b(?:[Ss]chema|[Vv]ersion|[Pp]hase|ADR) )\b\d+ (?:actions|event types|values|reactions|lines|files)\b/
const CODE_SPAN = /(`+)([\s\S]*?[^`])\1(?!`)/g
const LINK_TARGET = /]\([^)]*\)/g
const WORD = /[\p{L}\p{N}]/u
const ABBREVIATIONS: ReadonlySet<string> = new Set(['e.g.', 'i.e.', 'vs.'])
const EXCERPT_WORDS = 12

/** A run of text with the 1-based source line of each character. */
interface LinedText {
  text: string
  lines: number[]
}

/**
 * Rewrites the matches of `pattern` in `source`, and keeps the line of each character. A replacement takes the line
 * of the first character of its match.
 */
function replaceLined(source: LinedText, pattern: RegExp, replace: (match: RegExpExecArray) => string): LinedText {
  let text = ''
  const lines: number[] = []
  let from = 0
  for (const match of source.text.matchAll(pattern)) {
    const index = match.index
    text += source.text.slice(from, index)
    lines.push(...source.lines.slice(from, index))
    const replacement = replace(match as RegExpExecArray)
    text += replacement
    lines.push(...Array.from(replacement, () => source.lines[index]))
    from = index + match[0].length
  }
  text += source.text.slice(from)
  lines.push(...source.lines.slice(from))
  return { text, lines }
}

/** The text with each code span as one word and each link target removed, so a word count and a match read only prose. */
function normalize(source: LinedText): LinedText {
  const compact = replaceLined(source, CODE_SPAN, (match) => {
    const body = match[2].replace(/\s+/g, '')
    return `\`${WORD.test(body) ? body : 'code'}\``
  })
  return replaceLined(compact, LINK_TARGET, () => ']')
}

/** The words of a text: the whitespace-separated tokens with a letter or a digit. */
export function countWords(text: string): number {
  return text.split(/\s+/).filter(token => WORD.test(token)).length
}

/** The sentences of a block, each with the line of its first character. A block end ends a sentence. */
function splitSentences(block: LinedText): { text: string, line: number }[] {
  const sentences: { text: string, line: number }[] = []
  let start = 0
  const push = (end: number): void => {
    const raw = block.text.slice(start, end)
    const offset = raw.length - raw.trimStart().length
    const text = raw.trim()
    if (text.length > 0) {
      sentences.push({ text, line: block.lines[start + offset] })
    }
    start = end
  }
  const end = /[.!?][)"'\]”’]*(?=\s|$)/g
  for (const match of block.text.matchAll(end)) {
    const stop = match.index + match[0].length
    const word = /[\p{L}.]*$/u.exec(block.text.slice(start, stop))?.[0] ?? ''
    if (ABBREVIATIONS.has(word.toLowerCase())) {
      continue
    }
    push(stop)
  }
  push(block.text.length)
  return sentences
}

function excerptOf(sentence: string): string {
  const words = sentence.split(/\s+/)
  return words.length > EXCERPT_WORDS ? `${words.slice(0, EXCERPT_WORDS).join(' ')} ...` : sentence
}

/** The findings of one block of prose: a paragraph, a list item, or a YAML field. */
function checkBlock(block: LinedText, limits: ProseLimits, report: (line: number, rule: ProseRule, excerpt: string) => void): void {
  const prose = normalize(block)
  const history = historyPattern(limits)
  for (const sentence of splitSentences(prose)) {
    const words = countWords(sentence.text)
    if (words > limits.maxSentenceWords) {
      report(sentence.line, 'sentence-length', `${words} words: "${excerptOf(sentence.text)}"`)
    }
    const marks = [...new Set(Array.from(sentence.text.matchAll(history), match => `"${match[0]}"`))]
    if (marks.length > 0) {
      report(sentence.line, 'history', `${marks.join(', ')} in "${excerptOf(sentence.text)}"`)
    }
    const count = ROTTING_COUNT.exec(sentence.text)
    if (count !== null) {
      report(sentence.line, 'rotting-count', `"${count[0]}" in "${excerptOf(sentence.text)}"`)
    }
  }
}

const LIST_ITEM = /^\s*(?:[-*+]|\d+[.)])\s+/
const HEADING = /^\s{0,3}#{1,6}(?:\s|$)/
const TABLE_ROW = /^\s*\|/

/** The prose blocks of an explainer: each paragraph and each list item, with the frontmatter, fences, headings, and tables out. */
export function explainerBlocks(source: string): LinedText[] {
  const lines = withoutFencedBlocks(source).split('\n')
  let first = 0
  if (lines[0]?.trim() === '---') {
    const close = lines.findIndex((line, index) => index > 0 && line.trim() === '---')
    first = close < 0 ? lines.length : close + 1
  }
  const blocks: LinedText[] = []
  let current: LinedText | null = null
  const close = (): void => {
    if (current !== null) {
      blocks.push(current)
      current = null
    }
  }
  for (let index = first; index < lines.length; index++) {
    const line = lines[index]
    if (line.trim() === '' || HEADING.test(line) || TABLE_ROW.test(line) || /^\s*<!--/.test(line)) {
      close()
      continue
    }
    const item = LIST_ITEM.exec(line)
    if (item !== null) {
      close()
    }
    const text = (item === null ? line : line.slice(item[0].length)).replace(/^\s*>\s?/, '').trim()
    if (current === null) {
      current = { text, lines: Array.from(text, () => index + 1) }
    }
    else {
      const joined: LinedText = current
      joined.text += ` ${text}`
      joined.lines.push(index + 1, ...Array.from(text, () => index + 1))
    }
  }
  close()
  return blocks
}

/** The findings of one explainer. */
export function checkExplainerProse(path: string, source: string, limits: ProseLimits): ProseFinding[] {
  const findings: ProseFinding[] = []
  for (const block of explainerBlocks(source)) {
    checkBlock(block, limits, (line, rule, excerpt) => findings.push({ path, line, field: null, rule, excerpt }))
  }
  return findings
}

let yamlModule: typeof import('yaml') | null
try {
  yamlModule = await import('yaml')
}
catch {
  yamlModule = null
}

export interface DiagramField {
  /** The YAML path of the field, such as `['nodes', 3, 'detail']`. */
  path: (string | number)[]
  /** The label of the field in a finding, such as `node api, detail`. */
  label: string
  /** The 1-based line of the first line of the value. */
  line: number | null
  /** A literal block (`|`): each line of the value is one line of the source. */
  literal: boolean
  value: string
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

/** The name of a text field of a diagram. */
export type DiagramFieldName = 'title' | 'summary' | 'text' | 'detail' | 'caption'

/** The fields that the prose check reads. A title is no prose: the diagram grammar caps its length. */
const PROSE_FIELDS: ReadonlySet<DiagramFieldName> = new Set(['summary', 'text', 'detail', 'caption'])

/**
 * The text fields of a diagram whose names are in `names`: the title and the summary of the diagram, the title of a
 * column and of a group, the title, the text and the detail of a node, and the title and the caption of a step. The
 * line comes from the npm `yaml` parser. Under the bun of the JUnit gate, with no node_modules, the field label alone
 * locates a finding.
 */
export function diagramFields(source: string, names: ReadonlySet<DiagramFieldName> = PROSE_FIELDS): DiagramField[] {
  const data = record(parseYaml(source))
  const fields: DiagramField[] = []
  const add = (path: (string | number)[], label: string, value: unknown): void => {
    if (typeof value === 'string' && names.has(path.at(-1) as DiagramFieldName)) {
      fields.push({ path, label, line: null, literal: false, value })
    }
  }
  add(['title'], 'title', data.title)
  add(['summary'], 'summary', data.summary)
  for (const part of ['columns', 'groups'] as const) {
    list(data[part]).forEach((entry, index) => {
      const fieldsOf = record(entry)
      const id = typeof fieldsOf.id === 'string' ? fieldsOf.id : String(index)
      add([part, index, 'title'], `${part === 'columns' ? 'column' : 'group'} ${id}, title`, fieldsOf.title)
    })
  }
  list(data.nodes).forEach((node, index) => {
    const fieldsOf = record(node)
    const id = typeof fieldsOf.id === 'string' ? fieldsOf.id : String(index)
    add(['nodes', index, 'title'], `node ${id}, title`, fieldsOf.title)
    add(['nodes', index, 'text'], `node ${id}, text`, fieldsOf.text)
    add(['nodes', index, 'detail'], `node ${id}, detail`, fieldsOf.detail)
  })
  list(data.steps).forEach((step, index) => {
    const fieldsOf = record(step)
    const title = typeof fieldsOf.title === 'string' ? fieldsOf.title : String(index + 1)
    add(['steps', index, 'title'], `step ${title}, title`, fieldsOf.title)
    add(['steps', index, 'caption'], `step ${title}, caption`, fieldsOf.caption)
  })
  if (yamlModule !== null) {
    const counter = new yamlModule.LineCounter()
    const document = yamlModule.parseDocument(source, { lineCounter: counter })
    for (const field of fields) {
      const node = document.getIn(field.path, true) as { range?: [number, number, number], type?: string } | undefined
      if (node?.range !== undefined) {
        const start = counter.linePos(node.range[0]).line
        // a block scalar starts on the line of its indicator, and its text on the next line
        const block = node.type === 'BLOCK_LITERAL' || node.type === 'BLOCK_FOLDED'
        field.line = block ? start + 1 : start
        field.literal = node.type === 'BLOCK_LITERAL'
      }
    }
  }
  return fields
}

/** The findings of one diagram file. */
export function checkDiagramProse(path: string, source: string, limits: ProseLimits): ProseFinding[] {
  const findings: ProseFinding[] = []
  for (const field of diagramFields(source)) {
    const line = field.line
    // a line of a literal block maps to one source line; any other scalar reports the line of its first line
    const literal = line !== null && field.literal
    const text = field.value.replace(/\n+$/, '')
    const lines: number[] = []
    let current = 0
    for (const char of text) {
      lines.push(line === null ? 0 : line + (literal ? current : 0))
      if (char === '\n') {
        current++
      }
    }
    checkBlock({ text, lines }, limits, (sentenceLine, rule, excerpt) => {
      findings.push({ path, line: line === null ? null : sentenceLine, field: field.label, rule, excerpt })
    })
    if (field.path.at(-1) === 'detail') {
      const words = countWords(normalize({ text, lines }).text)
      if (words > limits.maxCardWords) {
        findings.push({ path, line, field: field.label, rule: 'card-length', excerpt: `${words} words, the cap is ${limits.maxCardWords}` })
      }
    }
  }
  return findings
}

function markdownFiles(dir: string, suffix: string): string[] {
  return existsSync(dir) ? readdirSync(dir).filter(entry => entry.endsWith(suffix)).sort() : []
}

/** Every finding of the explainers and the diagram cards of the site. */
export function readProseFindings(config: ProseConfig, root: string = repoRoot): { files: number, findings: ProseFinding[] } {
  const content = contentDir(config, root)
  const { explainers: explainersDir, diagrams: diagramsDir } = config.docs
  const findings: ProseFinding[] = []
  const explainers = markdownFiles(join(content, explainersDir), '.md')
  for (const entry of explainers) {
    const path = `${explainersDir}/${entry}`
    findings.push(...checkExplainerProse(path, readFileSync(join(content, path), 'utf8'), config.prose))
  }
  const diagrams = markdownFiles(join(content, diagramsDir), '.diagram.yaml')
  for (const entry of diagrams) {
    const path = `${diagramsDir}/${entry}`
    findings.push(...checkDiagramProse(path, readFileSync(join(content, path), 'utf8'), config.prose))
  }
  return { files: explainers.length + diagrams.length, findings }
}

/** Reads the explainers and the diagram cards. Throws with every finding. Returns the number of files read. */
export function checkProse(config: ProseConfig, root: string = repoRoot): number {
  const { files, findings } = readProseFindings(config, root)
  if (findings.length > 0) {
    const guide = config.prose.guide === null ? '' : `; see ${config.prose.guide}`
    throw new Error(`prose: ${findings.length} finding(s)${guide}\n${findings.map(formatProseFinding).join('\n')}`)
  }
  return files
}

// --- CLI: `node proseLint.ts <site.config.ts>` -----------------------------------------------------

if (isMainModule(import.meta.url)) {
  await runCheck('prose', config => `${checkProse(config)} files`)
}
