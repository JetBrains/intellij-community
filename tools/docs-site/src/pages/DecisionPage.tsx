// One decision record (ADR 0199): the record body, under a strip with its status, date and topic. A
// superseded record names its successor above the body, so a reader does not act on a replaced rule.

import { useCallback } from 'react'
import { Alert, AlertDescription } from '../components/ui/alert'
import { DecisionStatusBadge, NotFoundPage } from '../furniture'
import { decisionRoute, decisionsRoute } from '../router'
import { site } from '../site.ts'
import { MarkdownDocPage } from './MarkdownDocPage'

export function DecisionPage({ slug, anchor }: { slug: string, anchor: string | null }) {
  const { config, index } = site()
  const doc = index.docForDecisionSlug(slug)
  const anchorHref = useCallback((id: string) => doc ? decisionRoute(doc.path, id) : decisionsRoute(), [doc])
  if (!doc) {
    return <NotFoundPage kind="decision record" name={slug} backHref={decisionsRoute()} backLabel="Back to decisions" />
  }
  const successor = doc.adr.supersededBy === null ? undefined : index.docsByPath.get(doc.adr.supersededBy)
  const topic = config.decisions.topics.find(entry => entry.id === doc.adr.topic)
  return (
    <MarkdownDocPage path={doc.path} anchor={anchor} anchorHref={anchorHref}>
      <div className="decision-facts mb-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        {doc.adr.number !== null && <span className="font-mono">ADR {doc.adr.number}</span>}
        <DecisionStatusBadge status={doc.adr.status} />
        {doc.adr.date !== null && <time className="font-mono" dateTime={doc.adr.date}>{doc.adr.date}</time>}
        {topic && <a href={decisionsRoute(undefined, { topic: topic.id })}>{topic.label}</a>}
      </div>
      {successor !== undefined && (
        <Alert className="decision-superseded mb-5">
          <AlertDescription>
            {doc.adr.status === 'superseded' ? 'This record is superseded by ' : 'A part of this record is superseded by '}
            <a href={decisionRoute(successor.path)}>{successor.title}</a>.
          </AlertDescription>
        </Alert>
      )}
    </MarkdownDocPage>
  )
}
