// The metadata of a bundled doc: the ADR status, date and topic, the guide group, and the note state (ADR 0199).
// Shared by scripts/harvestDocs.ts (node) and the site (browser), so it imports nothing from node. The topics and the
// guide groups come from the site config (siteConfig.ts).

import type { LabeledId } from './siteConfig.ts'

/** A topic id of the site config (`decisions.topics`). */
export type AdrTopic = string

/** A guide group id of the site config (`guideGroups`). */
export type GuideGroup = string

export const NOTE_STATES = ['active', 'historical'] as const

export type NoteState = typeof NOTE_STATES[number]

export const ADR_STATUSES = [
  { id: 'accepted', label: 'Accepted' },
  { id: 'proposed', label: 'Proposed' },
  { id: 'partly-superseded', label: 'Partly superseded' },
  { id: 'superseded', label: 'Superseded' },
] as const

export type AdrStatus = typeof ADR_STATUSES[number]['id']

export interface AdrMeta {
  /**
   * The four-digit number from the file name of a record before 2026-10-12, for example `0197`, or null for a
   * record that the date and the slug name (`2026-10-12-<slug>.md`, adrFiles.ts).
   */
  number: string | null
  status: AdrStatus
  /** The first sentence of the status text, for the row tooltip. */
  statusText: string
  /** The `Date:` line, which a dated record also carries in its file name, or null when a numbered record has none. */
  date: string | null
  topic: AdrTopic
  /** The content-root-relative path of the record that supersedes this one, or null. */
  supersededBy: string | null
}

/**
 * The order key of a record, ascending: a numbered record by its number, then a dated record by its date. The site sorts
 * the key descending, so the newest record comes first.
 */
export function adrOrderKey(meta: AdrMeta): string {
  return meta.number === null ? `1${meta.date ?? ''}` : `0${meta.number}`
}

/** The short name of a record in a list or a crumb: `ADR 0197` for a numbered record, the date for a dated one. */
export function adrLabel(meta: AdrMeta): string {
  return meta.number === null ? meta.date ?? '' : `ADR ${meta.number}`
}

/** The title of a record with its number prefix, `197. Title`, or the title alone for a dated record. */
export function adrNumberedTitle(meta: AdrMeta, title: string): string {
  return meta.number === null ? title : `${Number(meta.number)}. ${title}`
}

/** True when `value` is the id of one entry of `entries`: a topic or a guide group of the site config. */
export function isLabeledId(entries: readonly LabeledId[], value: string): boolean {
  return entries.some(entry => entry.id === value)
}

export function isNoteState(value: string): value is NoteState {
  return (NOTE_STATES as readonly string[]).includes(value)
}

/** The H1 of a decision record without its number prefix: `# 197. A session is state` gives `A session is state`. */
export function adrDisplayTitle(title: string): string {
  return title.replace(/^(?:ADR\s+)?0*\d+\s*(?:\.|—|–|-|:)\s*/, '')
}

/** The status text of a record: the first paragraph under `## Status`, or the rest of a `Status:` line. */
export function adrStatusText(body: string): string | null {
  const heading = /^##\s+Status\s*\n+((?:.+\n?)+)/m.exec(body)
  if (heading) {
    return heading[1].replace(/\s*\n\s*/g, ' ').trim()
  }
  const line = /^\*{0,2}Status:?\*{0,2}:?\s*(.+)$/m.exec(body)
  return line ? line[1].trim() : null
}

/** The status of a record from its status text, or null when the text names no known status. */
export function adrStatus(statusText: string): AdrStatus | null {
  const text = statusText.replace(/^\*+/, '')
  // "Superseded in part by", "Superseded on <date> by [ADR n](…) for the language", "Superseded for cloud launch"
  if (/Superseded (?:in part|for )|Superseded on \S+ by \[[^\]]*\]\([^)]*\)\*{0,2}:? for /.test(text)) {
    return 'partly-superseded'
  }
  if (/^Superseded\b/.test(text)) {
    return 'superseded'
  }
  if (/^Proposed\b/.test(text)) {
    return 'proposed'
  }
  if (/^Accepted\b/.test(text)) {
    return 'accepted'
  }
  return null
}

/** The file name of the first decision record that the text after "Superseded" links, or null. */
export function adrSupersededBy(statusText: string): string | null {
  const after = statusText.slice(statusText.search(/Superseded/))
  const link = /]\((?:\.\/)?(\d{4}(?:-\d{2}-\d{2})?-[^)\s#]+\.md)/.exec(after)
  return link ? link[1] : null
}

/** The first sentence of a status text, without markdown links and emphasis. */
export function adrStatusSummary(statusText: string): string {
  const plain = statusText.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/\*\*|`/g, '')
  const end = plain.search(/\.(?:\s|$)/)
  return end < 0 ? plain : plain.slice(0, end + 1)
}

/**
 * The metadata of the decision record at `path` (`docs/decisions/<file>.md`). Throws with the path when the
 * file name has neither a number nor a date, or the record has no known status or no topic of `topics`, so the
 * harvest fails on a malformed record. `topics` is `decisions.topics` of the site config.
 */
export function parseAdrMeta(path: string, body: string, topic: string | undefined, topics: readonly LabeledId[]): AdrMeta {
  const folder = path.slice(0, path.lastIndexOf('/') + 1)
  const fileName = path.slice(folder.length)
  const prefix = /^(\d{4}-\d{2}-\d{2}|\d{4})-/.exec(fileName)?.[1]
  if (prefix === undefined) {
    throw new Error(`harvest: ${path}: a decision record file name starts with a date, YYYY-MM-DD-, or with the four-digit number of a record before 2026-10-12`)
  }
  const number = prefix.length === 4 ? prefix : null
  const statusText = adrStatusText(body)
  if (statusText === null) {
    throw new Error(`harvest: ${path}: no "## Status" section and no "Status:" line`)
  }
  const status = adrStatus(statusText)
  if (status === null) {
    throw new Error(`harvest: ${path}: the status "${statusText.slice(0, 60)}" names no known status`)
  }
  if (topic === undefined || !isLabeledId(topics, topic)) {
    throw new Error(`harvest: ${path}: frontmatter "topic:" is missing or unknown; use one of ${topics.map(t => t.id).join(', ')}`)
  }
  const superseding = status === 'superseded' || status === 'partly-superseded' ? adrSupersededBy(statusText) : null
  return {
    number,
    status,
    statusText: adrStatusSummary(statusText),
    date: /^Date:\s*(\d{4}-\d{2}-\d{2})/m.exec(body)?.[1] ?? (number === null ? prefix : null),
    topic,
    supersededBy: superseding === null ? null : `${folder}${superseding}`,
  }
}
