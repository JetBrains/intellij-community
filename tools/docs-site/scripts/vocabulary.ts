// The vocabulary check of a site: a term that the docs of the site must not use, with the term to use instead. The
// rules are `vocabulary` of the site config. The check reads the docs that the harvest reads and the specs, by line
// and by section, and the title, text, detail and caption fields of the diagrams.
//
// A match is the term, or the term with an `s`, as a whole word, case-insensitive. The check skips the frontmatter,
// the fenced blocks, the HTML comments, the code spans, the link targets and the URLs. A rule can allow its term in a
// doc, in a section, or in the label of a link to a path.
//
// The check is one owner for two callers: the `check` script of a site, and the gate of a site, which runs on bun
// without node_modules. It throws with every finding.

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, posix } from 'node:path'
import { headingText, slugify } from './explainerDoc.ts'
import { withoutFencedBlocks } from './fence.ts'
import { contentDir, isMainModule, posixRelative, repoRoot, runCheck, specFiles } from './paths.ts'
import { type DiagramFieldName, diagramFields } from './proseLint.ts'
import type { SiteConfig, VocabularyRule } from './siteConfig.ts'

/** The part of the site config that the check reads. */
export type VocabularyConfig = Pick<SiteConfig, 'contentRoot' | 'docs' | 'specs' | 'vocabulary'>

/** One file that the check reads, by content-relative path. */
export interface VocabularyText {
  path: string
  kind: 'markdown' | 'diagram'
  source: string
}

export interface VocabularyFinding {
  /** The file, relative to the content root. */
  path: string
  /** The 1-based line, or null when the YAML parser of the runtime gives no positions. */
  line: number | null
  /** The field of a diagram, such as `node api, detail`, or null for a markdown file. */
  field: string | null
  /** The innermost section of the match, as `<path>#<slug>`, or null before the first heading or in a diagram. */
  section: string | null
  rule: VocabularyRule
  excerpt: string
}

/** The diagram fields that the check reads. */
const DIAGRAM_FIELDS: ReadonlySet<DiagramFieldName> = new Set(['title', 'text', 'detail', 'caption'])
const EXCERPT_LENGTH = 80

/** The `<path>:<line> (<field>): "<term>" → use "<replacement>" (<reason>): <excerpt>` line of one finding. */
export function formatVocabularyFinding(finding: VocabularyFinding): string {
  const line = finding.line === null ? '' : `:${finding.line}`
  const field = finding.field === null ? '' : ` (${finding.field})`
  const { term, replacement, reason } = finding.rule
  const advice = replacement === null ? 'say it in other words' : `use "${replacement}"`
  return `${finding.path}${line}${field}: "${term}" → ${advice} (${reason}): ${finding.excerpt}`
}

/**
 * The pattern of a term: the term or the term with an `s`, between Unicode word boundaries, case-insensitive. A space
 * of the term matches any run of whitespace, so a term can break across lines.
 */
export function vocabularyPattern(term: string): RegExp {
  const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/ /g, '\\s+')
  return new RegExp(`(?<![\\p{L}\\p{Nd}_])${escaped}s?(?![\\p{L}\\p{Nd}_])`, 'giu')
}

/** True when the content-relative `path` matches `pattern`. A `*` matches any characters inside one path segment. */
function pathMatches(pattern: string, path: string): boolean {
  if (!pattern.includes('*')) {
    return pattern === path
  }
  const source = pattern.split('*').map(part => part.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*')
  return new RegExp(`^${source}$`).test(path)
}

/** The text with each match of `pattern` replaced by spaces, so the offsets and the lines of the rest stay true. */
function blank(text: string, pattern: RegExp): string {
  return text.replace(pattern, match => match.replace(/[^\n]/g, ' '))
}

/** The source with the frontmatter blanked line by line. */
function withoutFrontmatter(source: string): string {
  if (!source.startsWith('---\n')) {
    return source
  }
  const end = source.indexOf('\n---', 4)
  if (end < 0) {
    return source
  }
  const close = source.indexOf('\n', end + 1)
  const stop = close < 0 ? source.length : close
  return source.slice(0, stop).replace(/[^\n]/g, ' ') + source.slice(stop)
}

const CODE_SPAN = /(`+)[\s\S]*?[^`]\1(?!`)|``/g
const HTML_COMMENT = /<!--[\s\S]*?-->/g
const URL = /\bhttps?:\/\/[^\s)>\]]+/g
const LINK = /\[([^\]\n]*)]\(([^)\s]*)[^)]*\)/g
const HEADING = /^(#{1,6})[ \t]+(.+?)[ \t#]*$/

/** The content-relative path of a link target of the doc at `docPath`, without its anchor, or null for a URL or an anchor. */
function linkPath(docPath: string, target: string): string | null {
  const path = target.split('#')[0]
  if (path === '' || /^[a-z][a-z0-9+.-]*:/i.test(path)) {
    return null
  }
  return posix.normalize(posix.join(posix.dirname(docPath), decodeURIComponent(path)))
}

/** The findings of one markdown file. */
export function markdownVocabularyFindings(rules: readonly VocabularyRule[], path: string, source: string): VocabularyFinding[] {
  let text = blank(withoutFencedBlocks(withoutFrontmatter(source)), HTML_COMMENT)
  text = blank(text, CODE_SPAN)
  // the label of a link stays prose; its target does not
  const links: { start: number, end: number, path: string | null }[] = []
  text = text.replace(LINK, (match, label: string, target: string, offset: number) => {
    links.push({ start: offset + 1, end: offset + 1 + label.length, path: linkPath(path, target) })
    return `[${label}]${match.slice(label.length + 2).replace(/[^\n]/g, ' ')}`
  })
  text = blank(text, URL)

  // the sections of each line: the slug of every enclosing heading, the innermost last
  const lines = text.split('\n')
  const lineStarts: number[] = []
  const sections: string[][] = []
  const stack: { level: number, slug: string }[] = []
  let offset = 0
  for (const line of lines) {
    const heading = HEADING.exec(line)
    if (heading !== null) {
      const level = heading[1].length
      while (stack.length > 0 && stack.at(-1)!.level >= level) {
        stack.pop()
      }
      stack.push({ level, slug: slugify(headingText(heading[2])) })
    }
    lineStarts.push(offset)
    sections.push(stack.map(entry => `${path}#${entry.slug}`))
    offset += line.length + 1
  }
  const lineOf = (index: number): number => {
    let low = 0
    let high = lineStarts.length - 1
    while (low < high) {
      const middle = (low + high + 1) >> 1
      if (lineStarts[middle] <= index) {
        low = middle
      }
      else {
        high = middle - 1
      }
    }
    return low
  }

  const findings: VocabularyFinding[] = []
  const sourceLines = source.split('\n')
  for (const rule of rules) {
    const allowed = rule.allowed ?? {}
    if ((allowed.docs ?? []).some(pattern => pathMatches(pattern, path))) {
      continue
    }
    const seen = new Set<number>()
    for (const match of text.matchAll(vocabularyPattern(rule.term))) {
      const lineIndex = lineOf(match.index)
      const chain = sections[lineIndex]
      if (seen.has(lineIndex) || chain.some(section => (allowed.sections ?? []).includes(section))) {
        continue
      }
      const link = links.find(candidate => candidate.start <= match.index && match.index < candidate.end)
      if (link?.path != null && (allowed.linkPaths ?? []).includes(link.path)) {
        continue
      }
      seen.add(lineIndex)
      findings.push({
        path,
        line: lineIndex + 1,
        field: null,
        section: chain.at(-1) ?? null,
        rule,
        excerpt: sourceLines[lineIndex].trim().slice(0, EXCERPT_LENGTH),
      })
    }
  }
  return findings
}

/** The findings of one diagram file: its title, text, detail and caption fields. */
export function diagramVocabularyFindings(rules: readonly VocabularyRule[], path: string, source: string): VocabularyFinding[] {
  const findings: VocabularyFinding[] = []
  const applicable = rules.filter(rule => !(rule.allowed?.docs ?? []).some(pattern => pathMatches(pattern, path)))
  if (applicable.length === 0) {
    return findings
  }
  for (const field of diagramFields(source, DIAGRAM_FIELDS)) {
    const text = blank(field.value, CODE_SPAN)
    for (const rule of applicable) {
      const match = vocabularyPattern(rule.term).exec(text)
      if (match === null) {
        continue
      }
      // a line of a literal block maps to one source line; any other scalar reports the line of its first line
      const offset = field.literal ? text.slice(0, match.index).split('\n').length - 1 : 0
      findings.push({
        path,
        line: field.line === null ? null : field.line + offset,
        field: field.label,
        section: null,
        rule,
        excerpt: field.value.replace(/\s+/g, ' ').trim().slice(0, EXCERPT_LENGTH),
      })
    }
  }
  return findings
}

/** The sections of a markdown file, as `<path>#<slug>`. */
function sectionsOf(path: string, source: string): string[] {
  return withoutFencedBlocks(source).split('\n').flatMap((line) => {
    const heading = HEADING.exec(line)
    return heading === null ? [] : [`${path}#${slugify(headingText(heading[2]))}`]
  })
}

/**
 * The findings of the files `texts` under the rules `rules`, and an error for each allowed section that names no
 * heading of `texts`, so a renamed heading does not leave a stale entry.
 */
export function vocabularyFindings(rules: readonly VocabularyRule[], texts: readonly VocabularyText[]): { findings: VocabularyFinding[], errors: string[] } {
  const findings = texts.flatMap(text => text.kind === 'markdown'
    ? markdownVocabularyFindings(rules, text.path, text.source)
    : diagramVocabularyFindings(rules, text.path, text.source))
  const sections = new Set(texts.flatMap(text => text.kind === 'markdown' ? sectionsOf(text.path, text.source) : []))
  const errors = rules.flatMap(rule => (rule.allowed?.sections ?? [])
    .filter(section => !sections.has(section))
    .map(section => `the rule "${rule.term}" allows the section ${section}, which names no heading`))
  return { findings, errors }
}

function entries(dir: string, suffix: string): string[] {
  return existsSync(dir) ? readdirSync(dir).filter(entry => entry.endsWith(suffix)).sort() : []
}

/**
 * The files of the site that the check reads: the docs that the doc harvest reads, the specs, and the diagrams. Each
 * file is read once, in this order.
 */
export function readVocabularyTexts(config: Omit<VocabularyConfig, 'vocabulary'>, root: string = repoRoot): VocabularyText[] {
  const content = contentDir(config, root)
  const docs: string[] = []
  const { home, notes, guides, explainers, glossary, decisions, extraReadmes, diagrams } = config.docs
  if (home !== null) {
    docs.push(home)
  }
  if (notes !== null) {
    // AGENTS.md instructs an agent, not a reader
    docs.push(...entries(join(content, notes), '.md').filter(entry => entry !== 'AGENTS.md').map(entry => `${notes}/${entry}`))
  }
  for (const folder of [guides, explainers, ...decisions]) {
    docs.push(...entries(join(content, folder), '.md').map(entry => `${folder}/${entry}`))
  }
  if (glossary !== null) {
    docs.push(glossary)
  }
  docs.push(...extraReadmes)
  docs.push(...specFiles(config, root).map(({ file }) => posixRelative(content, file)))

  const texts: VocabularyText[] = []
  const seen = new Set<string>()
  for (const path of docs) {
    const file = join(content, path)
    if (!seen.has(path) && existsSync(file)) {
      seen.add(path)
      texts.push({ path, kind: 'markdown', source: readFileSync(file, 'utf8') })
    }
  }
  for (const entry of entries(join(content, diagrams), '.diagram.yaml')) {
    const path = `${diagrams}/${entry}`
    texts.push({ path, kind: 'diagram', source: readFileSync(join(content, path), 'utf8') })
  }
  return texts
}

/**
 * Checks the docs, the specs and the diagrams of the site against its vocabulary. Throws with every finding. Returns
 * the number of files read, or 0 when the site has no vocabulary.
 */
export function checkVocabulary(config: VocabularyConfig, texts?: readonly VocabularyText[], root: string = repoRoot): number {
  if (config.vocabulary === null) {
    return 0
  }
  const files = texts ?? readVocabularyTexts(config, root)
  const { findings, errors } = vocabularyFindings(config.vocabulary, files)
  if (findings.length > 0 || errors.length > 0) {
    throw new Error(`vocabulary: ${findings.length + errors.length} finding(s)\n${[...errors, ...findings.map(formatVocabularyFinding)].join('\n')}`)
  }
  return files.length
}

// --- CLI: `node vocabulary.ts <site.config.ts>` ----------------------------------------------------

if (isMainModule(import.meta.url)) {
  await runCheck('vocabulary', config => `${config.vocabulary?.length ?? 0} rules, ${checkVocabulary(config)} files`)
}
