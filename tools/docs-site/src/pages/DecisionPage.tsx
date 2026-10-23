// One decision record (ADR 0199): the record header with its title and the chips of its number, status, date, topic
// and issues, then the body. The status paragraph reads as a callout. A superseded record names its successor above
// the body, so a reader does not act on a replaced rule. The links of the body list under the table of contents.

import { ArrowUpRightIcon, CalendarIcon, TagIcon } from 'lucide-react'
import { useCallback } from 'react'
import { ADR_STATUSES, type AdrMeta } from '../../scripts/docMeta.ts'
import { RecordLinks } from '../components/RecordLinks'
import { Alert, AlertDescription } from '../components/ui/alert'
import { StatusDot } from '../components/StatusDot'
import { NotFoundPage, RecordChip, RecordHeader } from '../furniture'
import { decisionRoute, decisionsRoute } from '../router'
import { site } from '../site.ts'
import { MarkdownDocPage } from './MarkdownDocPage'

/** The status of a record: its `## Status` section, or its `Status:` paragraph. */
function statusText(source: string): string {
  return /^## Status\n([\s\S]*?)(?=^## |(?![\s\S]))/m.exec(source)?.[1] ?? /^Status: (?:[^\n]+\n?)+/m.exec(source)?.[0] ?? ''
}

/** The YouTrack issues that the status of a record links as `[KEY](url)`, each once. */
export function statusIssues(source: string): { key: string, href: string }[] {
  const status = statusText(source)
  const issues = new Map<string, string>()
  for (const match of status.matchAll(/\[([A-Z][A-Z0-9]*-\d+)\]\((https:\/\/youtrack\.jetbrains\.com\/issue\/[^)\s]+)\)/g)) {
    issues.set(match[1], match[2])
  }
  return [...issues].map(([key, href]) => ({ key, href }))
}

/**
 * The body as the page shows it: without the `Date:` line, which the date chip shows, and with the status as a quote,
 * which reads as a callout. The status is the first paragraph of the status section, or the `Status:` paragraph.
 */
export function decisionBody(source: string): string {
  const quoted = (paragraph: string) => paragraph.replace(/^/gm, '> ')
  return source
    .replace(/^Date: .*\n+/m, '')
    .replace(/^(## Status\n+)((?:[^\n]+\n?)+)/m, (_, heading: string, paragraph: string) => `${heading}${quoted(paragraph)}`)
    .replace(/^Status: (?:[^\n]+\n?)+/m, quoted)
}

/** A `YYYY-MM-DD` date in words, such as 25 October 2026. */
function longDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`)
  return Number.isNaN(parsed.getTime()) ? date : parsed.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
}

function DecisionChips({ adr, source }: { adr: AdrMeta, source: string }) {
  const topic = site().config.decisions.topics.find(entry => entry.id === adr.topic)
  return (
    <>
      {adr.number !== null && <RecordChip className="font-mono">ADR {adr.number}</RecordChip>}
      <RecordChip className="decision-status" data-status={adr.status}>
        <StatusDot status={adr.status} />
        {ADR_STATUSES.find(entry => entry.id === adr.status)?.label ?? adr.status}
      </RecordChip>
      {adr.date !== null && (
        <RecordChip>
          <CalendarIcon data-icon="inline-start" />
          <time dateTime={adr.date}>{longDate(adr.date)}</time>
        </RecordChip>
      )}
      {topic && (
        <RecordChip href={decisionsRoute(undefined, { topic: topic.id })}>
          <TagIcon data-icon="inline-start" />
          {topic.label}
        </RecordChip>
      )}
      {statusIssues(source).map(issue => (
        <RecordChip key={issue.key} href={issue.href} external>
          {issue.key}
          <ArrowUpRightIcon data-icon="inline-end" />
        </RecordChip>
      ))}
    </>
  )
}

export function DecisionPage({ slug, anchor }: { slug: string, anchor: string | null }) {
  const { index } = site()
  const doc = index.docForDecisionSlug(slug)
  const anchorHref = useCallback((id: string) => doc ? decisionRoute(doc.path, id) : decisionsRoute(), [doc])
  const header = useCallback((source: string) => doc && (
    <RecordHeader title={doc.title} chipsClassName="decision-facts" chips={<DecisionChips adr={doc.adr} source={source} />} lead={doc.description} />
  ), [doc])
  const aside = useCallback((source: string) => doc && (
    <RecordLinks source={source} baseDir={doc.path.slice(0, doc.path.lastIndexOf('/'))} selfHref={decisionRoute(doc.path)} />
  ), [doc])
  if (!doc) {
    return <NotFoundPage kind="decision record" name={slug} backHref={decisionsRoute()} backLabel="Back to decisions" />
  }
  const successor = doc.adr.supersededBy === null ? undefined : index.docsByPath.get(doc.adr.supersededBy)
  return (
    <MarkdownDocPage path={doc.path} anchor={anchor} anchorHref={anchorHref} header={header} body={decisionBody} aside={aside}>
      {successor !== undefined && (
        <Alert variant="warning" className="decision-superseded mb-5">
          <AlertDescription>
            {doc.adr.status === 'superseded' ? 'This record is superseded by ' : 'A part of this record is superseded by '}
            <a href={decisionRoute(successor.path)}>{successor.title}</a>.
          </AlertDescription>
        </Alert>
      )}
    </MarkdownDocPage>
  )
}
