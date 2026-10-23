// Guides index (ADR 0199 of Air): the guides in their topic groups (frontmatter `group:`), then the working
// notes. A historical note folds away, and the decision records have their own page.

import { routeForDoc } from '../docRoute'
import { EntryCardLink, HeadingAnchor, LinkList, LinkListItem, PageHeader } from '../furniture'
import { useAnchorScroll } from '../markdown'
import { catalogAnchor, decisionsRoute, guidesRoute } from '../router'
import { site } from '../site.ts'
import type { HarvestedDoc } from '../siteData.ts'
import { TocDocPage } from './TocAside'

function byOrderThenTitle(a: HarvestedDoc, b: HarvestedDoc): number {
  return a.order - b.order || a.title.localeCompare(b.title)
}

// section anchors use the catalog `group-` prefix: guide slugs never start with it, so the
// /guides/<x> route stays unambiguous
const NOTES_ANCHOR = catalogAnchor('working-notes')

function GuideCards({ docs }: { docs: HarvestedDoc[] }) {
  return (
    <ul className="doc-list m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,300px),1fr))] gap-2 p-0">
      {docs.map(doc => (
        <li key={doc.path} className="grid">
          <EntryCardLink className="h-full items-start" href={routeForDoc(doc)} title={doc.title} description={doc.description || undefined} />
        </li>
      ))}
    </ul>
  )
}

function NoteList({ docs }: { docs: HarvestedDoc[] }) {
  return (
    <div className="doc-list">
      <LinkList>
        {docs.map(doc => (
          <LinkListItem key={doc.path} detail={doc.description || undefined}>
            <a href={routeForDoc(doc)}>{doc.title}</a>
          </LinkListItem>
        ))}
      </LinkList>
    </div>
  )
}

export function GuidesPage({ anchor }: { anchor: string | null }) {
  const { config, index } = site()
  const docs = index.data.docs
  const guides = docs.filter(doc => doc.kind === 'guide').sort(byOrderThenTitle)
  const notes = docs.filter(doc => doc.kind === 'note').sort(byOrderThenTitle)
  const activeNotes = notes.filter(doc => doc.state !== 'historical')
  const historicalNotes = notes.filter(doc => doc.state === 'historical')
  const groups = config.guideGroups
    .map(group => ({ id: catalogAnchor(group.id), title: group.label, docs: guides.filter(doc => doc.group === group.id) }))
    .filter(group => group.docs.length > 0)
  const toc = [...groups, ...notes.length > 0 ? [{ id: NOTES_ANCHOR, title: 'Working notes' }] : []]
  useAnchorScroll(anchor, docs.length)
  return (
    <TocDocPage items={toc.map(section => ({ href: guidesRoute(section.id), title: section.title }))}>
      <PageHeader>
        <h1>Guides</h1>
        <p>
          Hand-written guides, by topic. A guide explains how a part of {config.brandMark} works. The <a href={decisionsRoute()}>decision
          records</a> say why it works that way.
        </p>
      </PageHeader>
      {groups.map(group => (
        <section key={group.id} className="mb-7">
          <h2 id={group.id}>
            {group.title}
            <HeadingAnchor href={guidesRoute(group.id)} />
          </h2>
          <GuideCards docs={group.docs} />
        </section>
      ))}
      {notes.length > 0 && (
        <section className="working-notes">
          <h2 id={NOTES_ANCHOR}>
            Working notes
            <HeadingAnchor href={guidesRoute(NOTES_ANCHOR)} />
          </h2>
          <p className="text-muted-foreground">
            Plans, investigations, and to-do lists. A note is not a contract: when a note and a spec disagree, the spec wins.
          </p>
          <NoteList docs={activeNotes} />
          {historicalNotes.length > 0 && (
            <details className="historical-notes mt-3">
              <summary className="cursor-pointer text-detail text-muted-foreground">
                {historicalNotes.length} historical {historicalNotes.length === 1 ? 'note' : 'notes'}: finished or set aside
              </summary>
              <NoteList docs={historicalNotes} />
            </details>
          )}
        </section>
      )}
    </TocDocPage>
  )
}
