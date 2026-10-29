// Decisions index (ADR 0199 of Air): one row per decision record, newest first. The status and the
// topic filters live in the query, so a filtered list is a link. With no topic in the query, the page
// hides the records of the hidden topic of the site config: the docs-site records describe the site, not its subject.

import { useMemo, useState } from 'react'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '../components/ui/select'
import { ADR_STATUSES, adrNumberedTitle } from '../../scripts/docMeta.ts'
import type { LabeledId } from '../../scripts/siteConfig.ts'
import { CatalogEmpty, DecisionStatusBadge, DocPage, FilterBar, FilterChips, FilterCount, PageHeader } from '../furniture'
import { useAnchorScroll } from '../markdown'
import { decisionRoute, decisionsRoute, guidesRoute, navigate, type DecisionStatusFilter } from '../router'
import { site } from '../site.ts'
import type { DecisionDoc } from '../siteData.ts'

const DEFAULT_TOPIC = ' default'
const ALL_TOPICS = 'all'

function topics(): LabeledId[] {
  return site().config.decisions.topics
}

function topicItems(): { value: string, label: string }[] {
  const hiddenTopic = site().config.decisions.hiddenTopic
  const hiddenTopicLabel = topics().find(topic => topic.id === hiddenTopic)?.label ?? hiddenTopic ?? ''
  return [
    { value: DEFAULT_TOPIC, label: `All topics except ${hiddenTopicLabel}` },
    { value: ALL_TOPICS, label: 'Every topic' },
    ...topics().map(topic => ({ value: topic.id, label: topic.label })),
  ]
}

function topicLabel(id: string): string {
  return topics().find(topic => topic.id === id)?.label ?? id
}

function matchesTopic(doc: DecisionDoc, topic: string | null): boolean {
  if (topic === null) {
    return doc.adr.topic !== site().config.decisions.hiddenTopic
  }
  return topic === ALL_TOPICS || doc.adr.topic === topic
}

function matchesQuery(doc: DecisionDoc, needle: string): boolean {
  if (needle === '') {
    return true
  }
  return (doc.adr.number !== null && (needle === doc.adr.number || needle === String(Number(doc.adr.number))))
    || `${doc.title} ${doc.adr.statusText} ${doc.adr.date ?? ''}`.toLowerCase().includes(needle)
}

function DecisionRow({ doc }: { doc: DecisionDoc }) {
  const successor = doc.adr.supersededBy === null ? undefined : site().index.docsByPath.get(doc.adr.supersededBy)
  return (
    <li className="decision-row grid grid-cols-[3.25rem_minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 border-b border-border py-2.5 max-sm:grid-cols-[2.75rem_minmax(0,1fr)]">
      <span className="decision-number font-mono text-xs text-muted-foreground tabular-nums">{doc.adr.number ?? doc.adr.date}</span>
      <div className="grid min-w-0 gap-0.5">
        <a className="decision-title font-medium text-foreground" href={decisionRoute(doc.path)}>
          {doc.title}
        </a>
        {successor !== undefined && successor.adr !== undefined && (
          <span className="text-xs text-muted-foreground">
            {doc.adr.status === 'superseded' ? 'Superseded by ' : 'In part superseded by '}
            <a href={decisionRoute(successor.path)}>{adrNumberedTitle(successor.adr, successor.title)}</a>
          </span>
        )}
      </div>
      <div className="decision-facts flex flex-wrap items-center justify-end gap-1.5 max-sm:col-start-2 max-sm:justify-start">
        <DecisionStatusBadge status={doc.adr.status} />
        <span className="text-xs text-muted-foreground">{topicLabel(doc.adr.topic)}</span>
        {doc.adr.date !== null && <time className="font-mono text-xs text-muted-foreground tabular-nums" dateTime={doc.adr.date}>{doc.adr.date}</time>}
      </div>
    </li>
  )
}

export function DecisionsPage({ anchor, status, topic }: {
  anchor: string | null
  status: DecisionStatusFilter | null
  topic: string | null
}) {
  const [query, setQuery] = useState('')
  const { config, index } = site()
  const decisionDocs = index.decisionDocs
  const topicChoices = useMemo(topicItems, [])
  const needle = query.trim().toLowerCase().replace(/^adr\s*/, '')
  const inTopic = useMemo(() => decisionDocs.filter(doc => matchesTopic(doc, topic)), [decisionDocs, topic])
  const shown = inTopic.filter(doc => (status === null || doc.adr.status === status) && matchesQuery(doc, needle))
  const hiddenCount = topic === null ? decisionDocs.length - inTopic.length : 0
  useAnchorScroll(anchor, decisionDocs.length)
  const go = (next: { status?: DecisionStatusFilter | null, topic?: string | null }) =>
    navigate(decisionsRoute(undefined, { status: next.status === undefined ? status : next.status, topic: next.topic === undefined ? topic : next.topic }), { replace: true })
  return (
    <DocPage>
      <PageHeader>
        <h1>Decisions</h1>
        <p>
          Each decision record says what {config.brandMark} chose, why, and what the choice costs. The newest record comes first. The
          {' '}<a href={guidesRoute()}>guides</a> explain the result.
        </p>
      </PageHeader>
      <FilterBar>
        <Input
          type="search"
          placeholder="Filter by number, date, title, or status…"
          value={query}
          onChange={event => setQuery(event.target.value)}
          aria-label="Filter decision records"
        />
        <Select items={topicChoices} value={topic ?? DEFAULT_TOPIC} onValueChange={next => go({ topic: next === DEFAULT_TOPIC ? null : String(next) })}>
          <SelectTrigger size="sm" aria-label="Filter by topic" className="filter-select decision-topic">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {topicChoices.slice(0, 2).map(item => <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}
            </SelectGroup>
            <SelectSeparator />
            <SelectGroup>
              {topicChoices.slice(2).map(item => <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}
            </SelectGroup>
          </SelectContent>
        </Select>
        <FilterCount>{shown.length} of {decisionDocs.length}</FilterCount>
        <FilterChips
          aria-label="Filter by status"
          allLabel="Any status"
          options={ADR_STATUSES.map(entry => ({
            value: entry.id,
            label: `${entry.label} ${inTopic.filter(doc => doc.adr.status === entry.id).length}`,
          }))}
          value={status}
          onChange={next => go({ status: next as DecisionStatusFilter | null })}
        />
      </FilterBar>
      {hiddenCount > 0 && (
        <p className="decision-hidden-note -mt-2 mb-3 text-xs text-muted-foreground">
          {hiddenCount} records about this docs site are hidden.{' '}
          <Button variant="link" size="sm" className="h-auto p-0 align-baseline text-foreground underline" onClick={() => go({ topic: ALL_TOPICS })}>
            Show them
          </Button>
        </p>
      )}
      {shown.length === 0
        ? <CatalogEmpty>No decision record matches the filter.</CatalogEmpty>
        : (
            <ol className="decision-list m-0 list-none p-0">
              {shown.map(doc => <DecisionRow key={doc.path} doc={doc} />)}
            </ol>
          )}
    </DocPage>
  )
}
