// The section tree of a reading page: the left island of the reading layout, as the Project view of the IDE. A tree
// lists the pages of one section in groups: the guides by group, the decision records by topic. A site adds the trees
// of its own sections through the `sectionTrees` part of `createSite`.

import type { AdrStatus } from '../scripts/docMeta.ts'
import { routeForDoc } from './docRoute'
import { decisionRoute, decisionsRoute, guidesRoute } from './router'
import { site } from './site.ts'
import type { HarvestedDoc } from './siteData.ts'

/** One page of a section tree. */
export interface SectionTreeLink {
  href: string
  label: string
  /** A short dim prefix, for example the number of a decision record. */
  detail?: string
  /** The status of a decision record, which the tree shows as a dot. */
  status?: AdrStatus
}

/** One group of a section tree, for example the guides of one guide group. */
export interface SectionTreeGroup {
  id: string
  label: string
  links: readonly SectionTreeLink[]
}

/** The tree of one section. */
export interface SectionTree {
  /** The id of the section. A change of it mounts a new tree; within one section the tree keeps its state. */
  id: string
  /** The section name, which links the index page of the section. */
  label: string
  href: string
  groups: readonly SectionTreeGroup[]
}

/** Gives the tree of a section. The shell calls it once and keeps the result. */
export type SectionTreeSource = () => SectionTree

const trees = new Map<SectionTreeSource, SectionTree>()

/** The tree of `source`, computed once. */
export function sectionTreeOf(source: SectionTreeSource): SectionTree {
  let tree = trees.get(source)
  if (tree === undefined) {
    tree = source()
    trees.set(source, tree)
  }
  return tree
}

function pathOf(href: string): string {
  return new URL(href, 'http://docs-site.local').pathname
}

/**
 * The link of the current page: the link with the longest path that the current path equals or extends with an
 * anchor segment. Null on a page that the tree does not list, for example the index page.
 */
export function activeTreeLink(tree: SectionTree, currentHref: string): SectionTreeLink | null {
  const current = pathOf(currentHref)
  let best: SectionTreeLink | null = null
  let bestLength = -1
  for (const group of tree.groups) {
    for (const link of group.links) {
      const path = pathOf(link.href)
      if ((current === path || current.startsWith(`${path}/`)) && path.length > bestLength) {
        best = link
        bestLength = path.length
      }
    }
  }
  return best
}

function byOrderThenTitle(a: HarvestedDoc, b: HarvestedDoc): number {
  return a.order - b.order || a.title.localeCompare(b.title)
}

function docLink(doc: HarvestedDoc): SectionTreeLink {
  return { href: routeForDoc(doc), label: doc.title }
}

/** The guides in their guide groups, then the working notes that are not historical, as on the Guides page. */
export function guidesTree(): SectionTree {
  const { config, index } = site()
  const docs = index.data.docs
  const guides = docs.filter(doc => doc.kind === 'guide').sort(byOrderThenTitle)
  const notes = docs.filter(doc => doc.kind === 'note' && doc.state !== 'historical').sort(byOrderThenTitle)
  const groups: SectionTreeGroup[] = config.guideGroups.map(group => ({
    id: group.id,
    label: group.label,
    links: guides.filter(doc => doc.group === group.id).map(docLink),
  }))
  groups.push({ id: 'working-notes', label: 'Working notes', links: notes.map(docLink) })
  return { id: 'guides', label: 'Guides', href: guidesRoute(), groups: groups.filter(group => group.links.length > 0) }
}

/** The decision records by topic, in the topic order of the site config; each topic newest first. */
export function decisionsTree(): SectionTree {
  const { config, index } = site()
  const groups = config.decisions.topics.map(topic => ({
    id: topic.id,
    label: topic.label,
    links: index.decisionDocs
      .filter(doc => doc.adr.topic === topic.id)
      .map(doc => ({ href: decisionRoute(doc.path), label: doc.title, detail: doc.adr.number ?? undefined, status: doc.adr.status })),
  }))
  return { id: 'decisions', label: 'Decisions', href: decisionsRoute(), groups: groups.filter(group => group.links.length > 0) }
}

/** The trees of the kernel sections, by route page. A `doc` page has no tree: no section lists it. */
export const KERNEL_SECTION_TREES: Readonly<Record<string, SectionTreeSource>> = {
  guides: guidesTree,
  guide: guidesTree,
  decisions: decisionsTree,
  decision: decisionsTree,
}
