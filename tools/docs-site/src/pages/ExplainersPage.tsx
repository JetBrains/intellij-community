// The explainers index (ADR 0203): one card per explainer, with its chapters as the way in.

import { DocPage, EntryCardLink, PageHeader } from '../furniture'
import { useAnchorScroll } from '../markdown'
import { explainerRoute } from '../router'
import { site } from '../site.ts'
import { guideSlug } from '../siteData.ts'

export function ExplainersPage({ anchor }: { anchor: string | null }) {
  const explainerDocs = site().index.explainerDocs
  useAnchorScroll(anchor, explainerDocs.length)
  return (
    <DocPage className="explainers-page">
      <PageHeader>
        <h1>Explainers</h1>
        <p>The architecture, one picture at a time. Each explainer is a few short chapters with one hand-laid diagram each: step through it, click a part for its code.</p>
      </PageHeader>
      <ul className="m-0 grid list-none gap-3 p-0 md:grid-cols-2">
        {explainerDocs.map(doc => (
          <li key={doc.path} className="explainer-card">
            <EntryCardLink
              className="h-full items-start"
              href={explainerRoute(guideSlug(doc.path))}
              title={doc.title}
              description={doc.description || undefined}
              meta={`${doc.chapters.length} chapters`}
            />
            <ol className="mt-1.5 mb-0 grid list-decimal gap-0.5 pl-6 text-xs">
              {doc.chapters.map(chapter => (
                <li key={chapter.id}><a href={explainerRoute(guideSlug(doc.path), chapter.id)}>{chapter.title}</a></li>
              ))}
            </ol>
          </li>
        ))}
      </ul>
    </DocPage>
  )
}
