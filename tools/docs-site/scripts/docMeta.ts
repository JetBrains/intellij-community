// The metadata of a bundled doc: the date and the topic of a decision, the guide group, and the note state
// (decision 2026-10-12-decision-records-are-named-by-date-and-slug-and-have-a-page).
// Shared by scripts/harvestDocs.ts (node) and the site (browser), so it imports nothing from node. The topics and the
// guide groups come from the site config (siteConfig.ts).
//
// A decision is the design of an archived change of an OpenSpec root (`decisionMeta`). The name of the archive folder,
// `YYYY-MM-DD-<slug>`, is the change id: it names the decision, and its date orders it.

import { withoutFencedBlocks } from './fence.ts'
import type { LabeledId } from './siteConfig.ts'

/** A topic id of the site config (`decisions.topics`). */
export type DecisionTopic = string

/** A guide group id of the site config (`guideGroups`). */
export type GuideGroup = string

export const NOTE_STATES = ['active', 'historical'] as const

export type NoteState = typeof NOTE_STATES[number]

export interface DecisionMeta {
  /** The date prefix of the change id. */
  date: string
  /** The topic (`x-topic` of `.openspec.yaml`), or null for a change that names none. */
  topic: DecisionTopic | null
  /** The change id: the name of the archive folder, `YYYY-MM-DD-<slug>`. */
  change: string
  /** The earlier ids of the decision (`x-adr` of `.openspec.yaml`). An old link that names one still opens the decision. */
  formerIds: string[]
}

/** True when `value` is the id of one entry of `entries`: a topic or a guide group of the site config. */
export function isLabeledId(entries: readonly LabeledId[], value: string): boolean {
  return entries.some(entry => entry.id === value)
}

export function isNoteState(value: string): value is NoteState {
  return (NOTE_STATES as readonly string[]).includes(value)
}

/** An H1 that names the kind of the file, not its decision. */
const GENERIC_HEADING = /^(?:design|proposal)$/i

/** The H1 of a markdown body outside the fenced blocks, or null when the body has none or a generic one. */
function specificHeading(body: string): string | null {
  const heading = /^#\s+(.+?)\s*$/m.exec(withoutFencedBlocks(body))?.[1]
  return heading === undefined || GENERIC_HEADING.test(heading) ? null : heading
}

/**
 * The title of the design of an archived change: the H1 of the design, else the H1 of the `proposal.md` of the same
 * folder, else the slug of the change. A generic H1, `# Design` or `# Proposal`, names no decision, so the title skips it.
 */
export function archiveDesignTitle(design: string, proposal: string | null, slug: string): string {
  return specificHeading(design) ?? (proposal === null ? null : specificHeading(proposal)) ?? slug
}

/**
 * The metadata of the design of the archived change `change`. The date is the date prefix of the folder name, and
 * the topic is the `x-topic` of the change. Throws with the path when the folder name has no date or the topic is not
 * one of `topics`, so the harvest fails on a malformed change.
 */
export function decisionMeta(path: string, change: string, topic: string | null, topics: readonly LabeledId[], formerIds: readonly string[] = []): DecisionMeta {
  const date = /^(\d{4}-\d{2}-\d{2})-/.exec(change)?.[1]
  if (date === undefined) {
    throw new Error(`harvest: ${path}: the name of an archived change starts with a date, YYYY-MM-DD-`)
  }
  if (topic !== null && !isLabeledId(topics, topic)) {
    throw new Error(`harvest: ${path}: "x-topic: ${topic}" of .openspec.yaml is unknown; use one of ${topics.map(t => t.id).join(', ')}`)
  }
  return { date, topic, change, formerIds: [...formerIds] }
}
