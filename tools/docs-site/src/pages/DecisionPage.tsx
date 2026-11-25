// One decision (decision 2026-10-12-decision-records-are-named-by-date-and-slug-and-have-a-page): the header with the
// title and the chips of the date and the topic, then the design of the archived change. The links of the body list
// under the table of contents. An old link that names a former id of the decision opens the URL of its change id.

import { CalendarIcon, TagIcon } from 'lucide-react'
import { useCallback, useEffect } from 'react'
import type { DecisionMeta } from '../../scripts/docMeta.ts'
import { RecordLinks } from '../components/RecordLinks'
import { NotFoundPage, RecordChip, RecordHeader } from '../furniture'
import { decisionRoute, decisionsRoute, navigate } from '../router'
import { site } from '../site.ts'
import { MarkdownDocPage } from './MarkdownDocPage'

/** A `YYYY-MM-DD` date in words, such as 25 October 2026. */
function longDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`)
  return Number.isNaN(parsed.getTime()) ? date : parsed.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
}

function DecisionChips({ decision }: { decision: DecisionMeta }) {
  const topic = site().config.decisions.topics.find(entry => entry.id === decision.topic)
  return (
    <>
      <RecordChip>
        <CalendarIcon data-icon="inline-start" />
        <time dateTime={decision.date}>{longDate(decision.date)}</time>
      </RecordChip>
      {topic && (
        <RecordChip href={decisionsRoute(undefined, { topic: topic.id })}>
          <TagIcon data-icon="inline-start" />
          {topic.label}
        </RecordChip>
      )}
    </>
  )
}

export function DecisionPage({ slug, anchor }: { slug: string, anchor: string | null }) {
  const { index } = site()
  const doc = index.docForDecisionSlug(slug)
  const anchorHref = useCallback((id: string) => doc ? decisionRoute(doc.path, id) : decisionsRoute(), [doc])
  const header = useCallback(() => doc && (
    <RecordHeader title={doc.title} chipsClassName="decision-facts" chips={<DecisionChips decision={doc.decision} />} lead={doc.description} />
  ), [doc])
  const aside = useCallback((source: string) => doc && (
    <RecordLinks source={source} baseDir={doc.path.slice(0, doc.path.lastIndexOf('/'))} selfHref={decisionRoute(doc.path)} />
  ), [doc])
  const formerId = doc !== undefined && slug !== doc.decision.change
  useEffect(() => {
    if (doc !== undefined && formerId) {
      navigate(decisionRoute(doc.path, anchor ?? undefined), { replace: true })
    }
  }, [doc, formerId, anchor])
  if (!doc) {
    return <NotFoundPage kind="decision" name={slug} backHref={decisionsRoute()} backLabel="Back to decisions" />
  }
  return <MarkdownDocPage path={doc.path} anchor={anchor} anchorHref={anchorHref} header={header} aside={aside} />
}
