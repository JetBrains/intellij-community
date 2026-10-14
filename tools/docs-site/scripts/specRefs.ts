// The reference check and the style check of the specs of a site. The spec roots, the spec style, and the template
// sections come from `specs` of the site config. The prose docs are the guides, the explainers, the decision records,
// and the extra docs of `docs`.
//
// The reference rules apply to every site:
//   - targets: the `targets:` frontmatter list of a spec is not empty, and each entry names an existing file. An entry
//     is relative to the folder of the spec. An entry with `*` or `?` is a glob.
//   - test-link: each `[@test]` link of a spec names an existing file, relative to the folder of the spec.
//   - link, anchor: each relative `.md` link of a spec or a prose doc names an existing file, and its `#anchor` names
//     a heading of that file by its GitHub slug.
// The style rules apply when `specs.style` is `plain-1`, to each spec that declares `style: plain-1`:
//   - sentence: a sentence has at most 25 words. The words of ASD-STE100 for a descriptive sentence.
//   - requirement: a list item under `## Requirements` has at most 20 words in each sentence.
//   - section: each `##` heading is one of `specs.templateSections`, when the site gives them.
//   - style: each spec under the first spec root declares `style: plain-1`.
//
// The check is one owner for two callers: the `check` script of a site, and the gate of a site, which runs on bun
// without node_modules. So it imports nothing from npm. It throws with every finding.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { contentDir, isMainModule, posixRelative, repoRoot, runCheck, specFiles } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'

/** The part of the site config that the check reads. */
export type SpecRefsConfig = Pick<SiteConfig, 'contentRoot' | 'docs' | 'specs'>

/** The style version of a spec that the style rules check. See `community/.ai/spec/SPEC_GUIDE.md`. */
export const PLAIN_STYLE = 'plain-1'

/** The most words in a descriptive sentence of a `plain-1` spec. The limit of ASD-STE100. */
export const MAX_SENTENCE_WORDS = 25

/** The most words in a requirement sentence of a `plain-1` spec. */
export const MAX_REQUIREMENT_WORDS = 20

/** One spec that the check read. */
export interface CheckedSpec {
  /** The path relative to its spec root, without the spec suffix, for example `composition`. */
  id: string
  /** The repository-relative POSIX path. */
  path: string
}

/** What the check read. */
export interface SpecRefs {
  specs: CheckedSpec[]
  /** The repository-relative POSIX paths of the prose docs, sorted. */
  proseDocs: string[]
  /** The repository-relative POSIX paths of the existing files that a markdown link names, sorted. */
  linkTargets: string[]
}

/** One sentence of a spec, with the line that its block starts on. */
export interface SpecSentence {
  line: number
  text: string
  wordCount: number
}

const TEST_LINK = /\[@test]\s*\(?([^\s)]+)\)?/g

/** A relative markdown link to a `.md` file, or a same-document anchor: group 1 is the path, group 2 the anchor. */
const MARKDOWN_LINK = /]\(([^)#\s]*\.md)?(?:#([^)\s]*))?\)/g

/** A table header separator such as `|---|---|`. */
const TABLE_RULE = /^\|?[\s:|-]+\|?$/

/**
 * A sentence boundary: terminal punctuation, whitespace, then the start of a new sentence. The opening character keeps
 * `e.g.` and `0.131.0` from splitting a sentence into false short ones.
 */
const SENTENCE_BREAK = /(?<=[.!?])\s+(?=[A-Z[(`])/

const LIST_MARKER = /^(?:[-*+]|\d+\.)\s+/

function lines(text: string): string[] {
  return text.split(/\r\n|\n|\r/)
}

/** The 1-based line of the character at `offset`. */
function lineAt(text: string, offset: number): number {
  let line = 1
  for (let i = 0; i < offset; i++) {
    if (text.charCodeAt(i) === 10) {
      line++
    }
  }
  return line
}

/**
 * Converts a path glob to a regex: a single star matches inside one path segment, a double star also crosses segments,
 * and a double star with a slash can match no directory.
 */
export function globToRegex(glob: string): RegExp {
  let pattern = '^'
  let i = 0
  while (i < glob.length) {
    if (glob.startsWith('**/', i)) {
      pattern += '(?:.*/)?'
      i += 3
    }
    else if (glob.startsWith('**', i)) {
      pattern += '.*'
      i += 2
    }
    else if (glob[i] === '*') {
      pattern += '[^/]*'
      i += 1
    }
    else if (glob[i] === '?') {
      pattern += '[^/]'
      i += 1
    }
    else {
      pattern += glob[i].replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      i += 1
    }
  }
  return new RegExp(`${pattern}$`)
}

/** The GitHub slug of a heading: lower case, letters, digits, `-` and `_` kept, each space a hyphen. */
export function headingSlug(heading: string): string {
  let slug = ''
  for (const c of heading.toLowerCase()) {
    if (/[\p{L}\p{Nd}_-]/u.test(c)) {
      slug += c
    }
    else if (c === ' ') {
      slug += '-'
    }
  }
  return slug
}

/** The index of the closing `---` line of the frontmatter, or -1 when the text has no frontmatter block. */
function frontmatterEnd(all: readonly string[]): number {
  if (all[0]?.trim() !== '---') {
    return -1
  }
  const end = all.slice(1).findIndex(line => line.trim() === '---')
  return end < 0 ? -1 : end + 1
}

/** The `targets` list of the frontmatter, each entry with its 1-based line, or null when the file has no frontmatter. */
export function frontmatterTargets(text: string): { target: string, line: number }[] | null {
  const all = lines(text)
  const end = frontmatterEnd(all)
  if (end < 0) {
    return null
  }
  const targets: { target: string, line: number }[] = []
  let inTargets = false
  for (let index = 1; index < end; index++) {
    const trimmed = all[index].trim()
    if (trimmed === 'targets:') {
      inTargets = true
    }
    else if (inTargets && trimmed.startsWith('- ')) {
      targets.push({ target: trimmed.slice(2).trim(), line: index + 1 })
    }
    else if (inTargets && trimmed.length > 0) {
      inTargets = false
    }
  }
  return targets
}

/** The `style` field of the frontmatter, or null when the file declares none. */
export function frontmatterStyle(text: string): string | null {
  const all = lines(text)
  const end = frontmatterEnd(all)
  if (end < 0) {
    return null
  }
  for (const line of all.slice(1, end)) {
    if (line.startsWith(' ') || line.startsWith('\t')) {
      continue
    }
    const colon = line.indexOf(':')
    if (colon >= 0 && line.slice(0, colon).trim() === 'style') {
      const value = line.slice(colon + 1).trim().replace(/^["']+|["']+$/g, '')
      return value.length > 0 ? value : null
    }
  }
  return null
}

function sentence(line: number, text: string): SpecSentence {
  return { line, text, wordCount: text.split(/[ \t]/).filter(word => word.trim().length > 0).length }
}

/**
 * The sentences of a spec that `accept` takes, with the line that the containing block starts on.
 *
 * Skips what a word budget cannot judge: the frontmatter, fenced code, headings, and `[@test]` inventory lines. Each
 * cell of a table row is its own block. Soft-wrapped lines join back into one block first, because a markdown sentence
 * wraps across lines, and a count per line measures the wrapping, not the prose.
 */
export function sentencesOf(text: string, accept: (section: string, isListItem: boolean) => boolean): SpecSentence[] {
  const all = lines(text)
  const end = frontmatterEnd(all)
  let index = end < 0 ? 0 : end + 1

  const sentences: SpecSentence[] = []
  let block = ''
  let blockLine = 0
  let blockIsListItem = false
  let section = ''
  let inFence = false

  const flush = (): void => {
    const joined = block.trim()
    block = ''
    if (joined.length === 0 || !accept(section, blockIsListItem)) {
      return
    }
    for (const part of joined.split(SENTENCE_BREAK)) {
      const trimmed = part.trim()
      if (trimmed.length > 0) {
        sentences.push(sentence(blockLine, trimmed))
      }
    }
  }

  const append = (lineNumber: number, content: string, isListItem: boolean): void => {
    if (block.length === 0) {
      blockLine = lineNumber
      blockIsListItem = isListItem
    }
    else {
      block += ' '
    }
    block += content
  }

  for (; index < all.length; index++) {
    const trimmed = all[index].trim()
    if (trimmed.startsWith('```')) {
      flush()
      inFence = !inFence
    }
    else if (inFence) {
      // fenced code is not prose
    }
    else if (trimmed.startsWith('## ')) {
      flush()
      section = trimmed.slice('## '.length).trim()
    }
    else if (trimmed.length === 0 || trimmed.startsWith('#') || trimmed.startsWith('[@test]')) {
      flush()
    }
    else if (trimmed.startsWith('|')) {
      flush()
      if (!TABLE_RULE.test(trimmed)) {
        for (const cell of trimmed.replace(/^\|+|\|+$/g, '').split('|')) {
          append(index + 1, cell.trim(), false)
          flush()
        }
      }
    }
    else if (LIST_MARKER.test(trimmed)) {
      flush()
      append(index + 1, trimmed.replace(LIST_MARKER, ''), true)
    }
    else {
      append(index + 1, trimmed, false)
    }
  }
  flush()
  return sentences
}

/** Every prose sentence of a spec. */
export function proseSentences(text: string): SpecSentence[] {
  return sentencesOf(text, () => true)
}

/**
 * The sentences of the list items under `## Requirements`. Only a list item is a requirement. The prose and the tables
 * of the section explain the requirements and take the sentence budget.
 */
export function requirementSentences(text: string): SpecSentence[] {
  return sentencesOf(text, (section, isListItem) => section === 'Requirements' && isListItem)
}

/** True when a file under `dir` matches `regex` by its POSIX path below `base`. */
function anyFileMatches(dir: string, base: string, regex: RegExp): boolean {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    // a symbolic link to a directory is not entered, as a Java file walk does not follow it
    if (entry.isDirectory()) {
      if (anyFileMatches(path, base, regex)) {
        return true
      }
    }
    else if (entry.isFile() || (entry.isSymbolicLink() && existsSync(path) && statSync(path).isFile())) {
      if (regex.test(relative(base, path).split(sep).join('/'))) {
        return true
      }
    }
  }
  return false
}

/**
 * True when `reference`, relative to the folder `specDir`, names at least one existing file. A reference with `*` or
 * `?` is a glob: the segments before the first wildcard name the directory to walk, and the rest matches the paths
 * below it.
 */
export function resolvesReference(specDir: string, reference: string): boolean {
  if (!reference.includes('*') && !reference.includes('?')) {
    return existsSync(resolve(specDir, reference))
  }
  const segments = reference.split('/')
  const firstWildcard = segments.findIndex(segment => segment.includes('*') || segment.includes('?'))
  const dir = resolve(specDir, ...segments.slice(0, firstWildcard))
  if (!existsSync(dir) || !statSync(dir).isDirectory()) {
    return false
  }
  return anyFileMatches(dir, dir, globToRegex(segments.slice(firstWildcard).join('/')))
}

/** The heading slugs of a markdown file. A line in a fenced block is not a heading. */
function headingSlugs(text: string): Set<string> {
  const slugs = new Set<string>()
  let inFence = false
  for (const line of lines(text)) {
    if (line.trimStart().startsWith('```')) {
      inFence = !inFence
    }
    if (inFence || !line.startsWith('#')) {
      continue
    }
    slugs.add(headingSlug(line.replace(/^#+/, '').trim()))
  }
  return slugs
}

function entries(dir: string, suffix: string): string[] {
  return existsSync(dir) ? readdirSync(dir).filter(entry => entry.endsWith(suffix)).sort() : []
}

/** The absolute paths of the prose docs: the guides, the explainers, the decision records, and the extra docs. */
export function proseDocFiles(config: SpecRefsConfig, root: string = repoRoot): string[] {
  const content = contentDir(config, root)
  const { guides, explainers, decisions, extraReadmes } = config.docs
  const files = new Set<string>()
  for (const folder of [guides, explainers, ...decisions]) {
    for (const entry of entries(join(content, folder), '.md')) {
      files.add(join(content, folder, entry))
    }
  }
  for (const path of extraReadmes) {
    const file = join(content, path)
    if (existsSync(file) && statSync(file).isFile()) {
      files.add(file)
    }
  }
  return [...files].sort()
}

/** Reads the specs and the prose docs and returns what they read and every finding, as `<path>:<line>: <rule>: <text>`. */
export function specRefFindings(config: SpecRefsConfig, root: string = repoRoot): SpecRefs & { findings: string[] } {
  const texts = new Map<string, string>()
  const text = (file: string): string => {
    let result = texts.get(file)
    if (result === undefined) {
      result = readFileSync(file, 'utf8')
      texts.set(file, result)
    }
    return result
  }
  const slugs = new Map<string, Set<string>>()
  const slugsOf = (file: string): Set<string> => {
    let result = slugs.get(file)
    if (result === undefined) {
      result = headingSlugs(text(file))
      slugs.set(file, result)
    }
    return result
  }
  const path = (file: string): string => posixRelative(root, file)

  const files = specFiles(config, root)
  const proseDocs = proseDocFiles(config, root).filter(file => !files.some(spec => spec.file === file))
  const findings: string[] = []
  if (files.length === 0) {
    findings.push(`${config.specs.roots.join(', ')}: no ${config.specs.suffix} file; the scan is broken`)
  }
  if (proseDocs.length === 0) {
    findings.push(`${config.docs.guides}: no guide, explainer, decision record or extra doc; the scan is broken`)
  }

  const linkTargets = new Set<string>()
  const markdownLinks = (file: string): void => {
    const source = text(file)
    for (const match of source.matchAll(MARKDOWN_LINK)) {
      const link = match[1] ?? ''
      const anchor = match[2] ?? ''
      if (link.includes('://')) {
        continue
      }
      const at = `${path(file)}:${lineAt(source, match.index)}`
      const target = link.length === 0 ? file : resolve(dirname(file), link)
      if (!existsSync(target)) {
        findings.push(`${at}: link: '${link}' points to a missing file`)
        continue
      }
      linkTargets.add(path(target))
      if (anchor.length > 0 && target.endsWith('.md') && !slugsOf(target).has(anchor)) {
        findings.push(`${at}: anchor: '${link}#${anchor}' names no heading of ${path(target)}`)
      }
    }
  }

  const plain = config.specs.style === PLAIN_STYLE
  const sections = config.specs.templateSections === null ? null : new Set(config.specs.templateSections)
  const firstRoot = join(root, config.specs.roots[0] ?? '')
  for (const { file } of files) {
    const source = text(file)
    const specDir = dirname(file)
    const targets = frontmatterTargets(source)
    if (targets === null) {
      findings.push(`${path(file)}:1: targets: the spec has no YAML frontmatter`)
    }
    else if (targets.length === 0) {
      findings.push(`${path(file)}:1: targets: the frontmatter declares no targets`)
    }
    else {
      for (const { target, line } of targets) {
        if (!resolvesReference(specDir, target)) {
          findings.push(`${path(file)}:${line}: targets: '${target}' matches no existing file`)
        }
      }
    }

    const seen = new Set<string>()
    for (const match of source.matchAll(TEST_LINK)) {
      const link = match[1].replace(/[),.]+$/, '')
      if (!seen.has(link)) {
        seen.add(link)
        if (!resolvesReference(specDir, link)) {
          findings.push(`${path(file)}:${lineAt(source, match.index)}: test-link: [@test] '${link}' matches no existing file`)
        }
      }
    }

    markdownLinks(file)

    if (!plain) {
      continue
    }
    const style = frontmatterStyle(source)
    if (style !== PLAIN_STYLE) {
      if (file.startsWith(firstRoot + sep)) {
        findings.push(`${path(file)}:1: style: a spec under ${config.specs.roots[0]} declares 'style: ${PLAIN_STYLE}'; see community/.ai/spec/SPEC_GUIDE.md 'Writing Style'`)
      }
      continue
    }
    for (const { line, text: sentenceText, wordCount } of proseSentences(source)) {
      if (wordCount > MAX_SENTENCE_WORDS) {
        findings.push(`${path(file)}:${line}: sentence: ${wordCount} words exceed the ${MAX_SENTENCE_WORDS}-word budget; split it: "${sentenceText.slice(0, 90)}…"`)
      }
    }
    for (const { line, text: sentenceText, wordCount } of requirementSentences(source)) {
      if (wordCount > MAX_REQUIREMENT_WORDS) {
        findings.push(`${path(file)}:${line}: requirement: ${wordCount} words exceed the ${MAX_REQUIREMENT_WORDS}-word budget; split it: "${sentenceText.slice(0, 90)}…"`)
      }
    }
    if (sections !== null) {
      let inFence = false
      for (const [index, line] of lines(source).entries()) {
        if (line.trimStart().startsWith('```')) {
          inFence = !inFence
        }
        if (inFence || !line.startsWith('## ')) {
          continue
        }
        const heading = line.slice('## '.length).trim()
        if (!sections.has(heading)) {
          findings.push(`${path(file)}:${index + 1}: section: '${heading}' is not a template section; use one of ${[...sections].join(', ')}, or fold the content into the section that owns it`)
        }
      }
    }
  }

  for (const file of proseDocs) {
    markdownLinks(file)
  }

  const specs = files.map(({ file, specRoot }) => ({
    id: relative(specRoot, file).split(sep).join('/').slice(0, -config.specs.suffix.length),
    path: path(file),
  }))
  return { specs, proseDocs: proseDocs.map(path), linkTargets: [...linkTargets].sort(), findings }
}

/** Checks the specs and the prose docs of the site. Throws with every finding. Returns what the check read. */
export function checkSpecRefs(config: SpecRefsConfig, root: string = repoRoot): SpecRefs {
  const { findings, ...refs } = specRefFindings(config, root)
  if (findings.length > 0) {
    throw new Error(`specRefs: ${findings.length} finding(s); update the owning doc in the same change\n${findings.join('\n')}`)
  }
  return refs
}

// --- CLI: `node specRefs.ts <site.config.ts> [<root>]` ---------------------------------------------
// The root is the repository root by default. A test gives the root of a temporary tree.

if (isMainModule(import.meta.url)) {
  const root = process.argv[3] === undefined ? repoRoot : resolve(process.argv[3])
  await runCheck('specRefs', (config) => {
    const { specs, proseDocs } = checkSpecRefs(config, root)
    return `${specs.length} specs, ${proseDocs.length} prose docs`
  })
}
