// The search of the launch picker (pages/LaunchPage.tsx): the entries that a search text matches, best match first.
// The module is pure, so a node unit test loads it.

import type { LaunchEntry } from './siteData.ts'

/** The rank of an entry for a lowercase needle: lower is better, null when it does not match. */
function rankOf(entry: LaunchEntry, needle: string): number | null {
  const name = entry.name.toLowerCase()
  if (name === needle || entry.target === needle) {
    return 0
  }
  if (name.startsWith(needle)) {
    return 1
  }
  if (name.includes(needle)) {
    return 2
  }
  return `${entry.target} ${entry.command}`.toLowerCase().includes(needle) ? 3 : null
}

/**
 * The entries whose name, target, or command holds `query`, case-insensitive. An exact name or target comes first,
 * then a name that starts with the query, then a name that holds it, then a target or a command that holds it. Equal
 * ranks keep the order of `entries`. An empty query gives every entry.
 */
export function rankLaunchEntries(entries: readonly LaunchEntry[], query: string): LaunchEntry[] {
  const needle = query.trim().toLowerCase()
  if (needle === '') {
    return [...entries]
  }
  return entries
    .map((entry, index) => ({ entry, index, rank: rankOf(entry, needle) }))
    .filter((candidate): candidate is { entry: LaunchEntry, index: number, rank: number } => candidate.rank !== null)
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .map(candidate => candidate.entry)
}

/**
 * The entry that the picker selects first: the best match of the query, or the entry named `defaultName` when the
 * query is empty, or the first entry.
 */
export function initialLaunchEntry(entries: readonly LaunchEntry[], query: string, defaultName: string): LaunchEntry | null {
  if (query.trim() !== '') {
    return rankLaunchEntries(entries, query)[0] ?? null
  }
  return entries.find(entry => entry.name === defaultName) ?? entries[0] ?? null
}
