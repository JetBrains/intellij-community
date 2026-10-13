// The decision records of a site, and the references to them. The folders, the last frozen number, and the
// reference roots come from the site config (`decisions` of siteConfig.ts).
//
// A record is identified by its file name. A record written before 2026-10-12 has a four-digit
// number, `NNNN-slug.md`, and the number is frozen: no new record takes one, because a sequential
// number is a counter that parallel sessions and checkouts allocate with no coordinator. A new record
// is `YYYY-MM-DD-slug.md`: the date orders it, and the slug identifies it with no coordination.
//
// The check is one owner for three callers: the `check` script of a site, the gate of a site (the Air JUnit gate
// runs on bun), and the harvest. It fails on a file name of another shape, on a number or a slug that two records
// use, on an H1 or a `Date:` line that disagrees with the file name, on a link to a record that does not exist, and
// on an `ADR NNNN` mention that names no numbered record. A number of `allowDuplicateNumbers` can name two records;
// a bare mention of such a number is ambiguous, and a link to the record is not.

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { withoutFencedBlocks } from './fence.ts'
import { isMainModule, repoRoot, runCheck, walkFiles } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'

/** The part of the site config that the check reads. */
export type DecisionConfig = Pick<SiteConfig, 'decisions'>

/** The directories that a walk of a reference root does not enter: tool outputs, dependencies, test data, and images. */
const SKIP_DIRS: ReadonlySet<string> = new Set(['node_modules', '.git', 'generated', 'target', 'testData', 'images', 'test-results', 'out', 'dist'])

export interface DecisionRecord {
  /** The file name without `.md`: the identity of the record. */
  id: string
  /** The repository-relative POSIX path. */
  path: string
  /** The four-digit number of a frozen record, or null for a dated record. */
  number: string | null
  /** The `YYYY-MM-DD` of a dated record, or null for a numbered record. */
  date: string | null
  slug: string
  /** The H1 without its number prefix. */
  title: string
}

export interface DecisionFileName {
  number: string | null
  date: string | null
  slug: string
}

const NUMBERED = /^(\d{4})-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$/
const DATED = /^(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$/

function isCalendarDate(date: string): boolean {
  const parsed = new Date(`${date}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
}

/** The parts of a record file name, or the reason the name is not one. A number above `lastFrozenNumber` is new. */
export function parseDecisionFileName(fileName: string, lastFrozenNumber: number): DecisionFileName | { error: string } {
  const dated = DATED.exec(fileName)
  if (dated !== null) {
    if (!isCalendarDate(dated[1])) {
      return { error: `the prefix ${dated[1]} is not a calendar date` }
    }
    return { number: null, date: dated[1], slug: dated[2] }
  }
  const numbered = NUMBERED.exec(fileName)
  if (numbered !== null) {
    if (Number(numbered[1]) > lastFrozenNumber) {
      return { error: `a new record takes the date, not a number: name it YYYY-MM-DD-${numbered[2]}.md` }
    }
    return { number: numbered[1], date: null, slug: numbered[2] }
  }
  return { error: 'a record is NNNN-slug.md (frozen) or YYYY-MM-DD-slug.md, with a lower-case slug' }
}

function posix(path: string): string {
  return path.split(sep).join('/')
}

/** Reads one record. Returns the record, or the findings about it. */
export function parseDecisionRecord(path: string, fileName: string, source: string, lastFrozenNumber: number): { record: DecisionRecord } | { errors: string[] } {
  const name = parseDecisionFileName(fileName, lastFrozenNumber)
  if ('error' in name) {
    return { errors: [`${path}: ${name.error}`] }
  }
  const errors: string[] = []
  const heading = /^#\s+(.+?)\s*$/m.exec(source)?.[1]
  if (heading === undefined) {
    errors.push(`${path}: no H1`)
  }
  let title = heading ?? ''
  if (name.number !== null) {
    const prefix = `${Number(name.number)}. `
    if (heading !== undefined && !heading.startsWith(prefix)) {
      errors.push(`${path}: the H1 starts with "${prefix}", the number of the file name; it is "# ${heading}"`)
    }
    title = heading?.startsWith(prefix) ? heading.slice(prefix.length) : title
  }
  else {
    if (heading !== undefined && /^(?:ADR\s+)?\d/.test(heading)) {
      errors.push(`${path}: a dated record has no number in its H1; it is "# ${heading}"`)
    }
    const date = /^Date:\s*(\S+)\s*$/m.exec(source)?.[1]
    if (date === undefined) {
      errors.push(`${path}: a dated record has a "Date: ${name.date}" line`)
    }
    else if (date !== name.date) {
      errors.push(`${path}: the Date line says ${date}, the file name says ${name.date}`)
    }
  }
  if (errors.length > 0) {
    return { errors }
  }
  return { record: { id: fileName.slice(0, -'.md'.length), path, number: name.number, date: name.date, slug: name.slug, title } }
}

/**
 * Every record of every folder. Throws with every finding: a malformed name, a missing folder, a duplicate number or
 * slug. A number of `allowDuplicateNumbers` can name two records.
 */
export function readDecisionRecords(config: DecisionConfig, root: string = repoRoot): DecisionRecord[] {
  const { folders, lastFrozenNumber, allowDuplicateNumbers } = config.decisions
  const records: DecisionRecord[] = []
  const errors: string[] = []
  for (const folder of folders) {
    const dir = join(root, folder)
    if (!existsSync(dir)) {
      errors.push(`${folder}: the folder does not exist`)
      continue
    }
    for (const entry of readdirSync(dir).sort()) {
      if (!entry.endsWith('.md') || entry === 'README.md') {
        continue
      }
      const path = `${folder}/${entry}`
      const parsed = parseDecisionRecord(path, entry, readFileSync(join(dir, entry), 'utf8'), lastFrozenNumber)
      if ('errors' in parsed) {
        errors.push(...parsed.errors)
      }
      else {
        records.push(parsed.record)
      }
    }
  }
  const byNumber = new Map<string, string[]>()
  const bySlug = new Map<string, string>()
  for (const record of records) {
    if (record.number !== null) {
      const others = byNumber.get(record.number) ?? []
      const limit = allowDuplicateNumbers.includes(record.number) ? 2 : 1
      if (others.length >= limit) {
        errors.push(`${record.path}: number ${record.number} is also the number of ${others.join(' and ')}`)
      }
      byNumber.set(record.number, [...others, record.path])
    }
    const other = bySlug.get(record.slug)
    if (other !== undefined) {
      errors.push(`${record.path}: slug "${record.slug}" is also the slug of ${other}; one decision has one record`)
    }
    bySlug.set(record.slug, record.path)
  }
  if (errors.length > 0) {
    throw new Error(`decisions: ${errors.length} invalid record(s)\n${errors.join('\n')}`)
  }
  return records
}

const LINK = /]\(<?([^)\s>]+?\.md)(?:#[^)\s>]*)?>?\)/g
/** A markdown link with its text, for example `[ADR 0005](0005-a-record.md)`. */
const TEXT_LINK = /\[([^\]]*)]\(<?([^)\s>]+?\.md)(?:#[^)\s>]*)?>?\)/g
const MENTION = /\bADR\s+(\d{3,4})\b/g

/** The source files whose comments cite a record. */
const SOURCE_SUFFIXES: readonly string[] = ['.kt', '.java', '.ts', '.tsx', '.mjs', '.rs', '.bazel', '.bzl']

export interface DecisionReferenceFinding {
  /** The repository-relative POSIX path of the citing file. */
  path: string
  line: number
  message: string
}

/**
 * The findings about the references under `decisions.referenceRoots`: a markdown link into a decision folder whose
 * target does not exist, and an `ADR NNNN` mention, in markdown or in a source comment, that names no numbered
 * record or names two. A mention that is the text of a link to a record with its number names that record. A fenced
 * block is not read, so a guide can show the shape of a name.
 */
export function checkDecisionReferences(records: readonly DecisionRecord[], config: DecisionConfig, root: string = repoRoot): DecisionReferenceFinding[] {
  const paths = new Set(records.map(record => record.path))
  const recordsByNumber = new Map<string, number>()
  for (const record of records) {
    if (record.number !== null) {
      recordsByNumber.set(record.number, (recordsByNumber.get(record.number) ?? 0) + 1)
    }
  }
  const numbersByPath = new Map(records.filter(record => record.number !== null).map(record => [record.path, record.number]))
  const folders = config.decisions.folders.map(folder => resolve(root, folder))
  const skipDirs = new Set([...SKIP_DIRS, ...config.decisions.skipDirs])
  const findings: DecisionReferenceFinding[] = []
  for (const referenceRoot of config.decisions.referenceRoots) {
    walkFiles(join(root, referenceRoot), entry => skipDirs.has(entry), (file) => {
      const markdown = file.endsWith('.md')
      if (!markdown && !SOURCE_SUFFIXES.some(suffix => file.endsWith(suffix))) {
        return
      }
      const path = posix(relative(root, file))
      const source = readFileSync(file, 'utf8')
      const lines = (markdown ? withoutFencedBlocks(source) : source).split('\n')
      lines.forEach((text, index) => {
        // The text of a link to a numbered record, as [start, end, number of the target].
        const linkedTexts = markdown
          ? [...text.matchAll(TEXT_LINK)].flatMap((link) => {
              const target = numbersByPath.get(posix(relative(root, resolve(dirname(file), link[2]))))
              return target === undefined ? [] : [{ start: link.index + 1, end: link.index + 1 + link[1].length, number: target }]
            })
          : []
        for (const mention of text.matchAll(MENTION)) {
          const number = mention[1].padStart(4, '0')
          const count = recordsByNumber.get(number) ?? 0
          if (count === 0) {
            findings.push({ path, line: index + 1, message: `"${mention[0]}" names no decision record` })
          }
          else if (count > 1 && !linkedTexts.some(linked => linked.number === number && linked.start <= mention.index && mention.index < linked.end)) {
            findings.push({ path, line: index + 1, message: `"${mention[0]}" is ambiguous: ${count} records take the number; link the record` })
          }
        }
        if (!markdown) {
          return
        }
        for (const link of text.matchAll(LINK)) {
          const target = resolve(dirname(file), link[1])
          const folder = folders.find(candidate => target.startsWith(candidate + sep))
          if (folder !== undefined && !paths.has(posix(relative(root, target))) && !existsSync(target)) {
            findings.push({ path, line: index + 1, message: `the link "${link[1]}" names no decision record` })
          }
        }
      })
    })
  }
  return findings
}

/** Reads the records and checks the references. Throws with every finding. */
export function checkDecisionRecords(config: DecisionConfig, root: string = repoRoot): DecisionRecord[] {
  const records = readDecisionRecords(config, root)
  const findings = checkDecisionReferences(records, config, root)
  if (findings.length > 0) {
    throw new Error(`decisions: ${findings.length} stale reference(s)\n${findings.map(finding => `${finding.path}:${finding.line}: ${finding.message}`).join('\n')}`)
  }
  return records
}

// --- CLI: `node adrFiles.ts <site.config.ts>` ------------------------------------------------------

if (isMainModule(import.meta.url)) {
  await runCheck('decisions', config => `${checkDecisionRecords(config).length} records`)
}
