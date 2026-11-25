// Decisions index (decision 2026-10-12-decision-records-are-named-by-date-and-slug-and-have-a-page): one row per
// decision, newest first. A decision is the design of an archived OpenSpec change. The topic filter lives in the query,
// so a filtered list is a link. With no topic in the query, the page hides the decisions of the hidden topic of the site
// config: the docs-site decisions describe the site, not its subject. A design without a topic shows only with no topic
// filter.

import { useMemo, useState } from 'react'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from '../components/ui/select'
import type { LabeledId } from '../../scripts/siteConfig.ts'
import { CatalogEmpty, DocPage, FilterBar, FilterCount, PageHeader } from '../furniture'
import { useAnchorScroll } from '../markdown'
import { decisionRoute, decisionsRoute, guidesRoute, navigate } from '../router'
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

function topicLabel(id: string | null): string {
  return id === null ? '' : topics().find(topic => topic.id === id)?.label ?? id
}

function matchesTopic(doc: DecisionDoc, topic: string | null): boolean {
  if (topic === null) {
    return doc.decision.topic !== site().config.decisions.hiddenTopic
  }
  return topic === ALL_TOPICS || doc.decision.topic === topic
}

function matchesQuery(doc: DecisionDoc, needle: string): boolean {
  return needle === '' || `${doc.title} ${doc.decision.change}`.toLowerCase().includes(needle)
}

function DecisionRow({ doc }: { doc: DecisionDoc }) {
  return (
    <li className="decision-row grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 border-b border-border py-2.5 max-sm:grid-cols-[minmax(0,1fr)]">
      <a className="decision-title min-w-0 font-medium text-foreground" href={decisionRoute(doc.path)}>
        {doc.title}
      </a>
      <div className="decision-facts flex flex-wrap items-center justify-end gap-1.5 max-sm:justify-start">
        <span className="text-xs text-muted-foreground">{topicLabel(doc.decision.topic)}</span>
        <time className="font-mono text-xs text-muted-foreground tabular-nums" dateTime={doc.decision.date}>{doc.decision.date}</time>
      </div>
    </li>
  )
}

export function DecisionsPage({ anchor, topic }: {
  anchor: string | null
  topic: string | null
}) {
  const [query, setQuery] = useState('')
  const { config, index } = site()
  const decisionDocs = index.decisionDocs
  const topicChoices = useMemo(topicItems, [])
  const needle = query.trim().toLowerCase()
  const inTopic = useMemo(() => decisionDocs.filter(doc => matchesTopic(doc, topic)), [decisionDocs, topic])
  const shown = inTopic.filter(doc => matchesQuery(doc, needle))
  const hiddenCount = topic === null ? decisionDocs.length - inTopic.length : 0
  useAnchorScroll(anchor, decisionDocs.length)
  const go = (next: { topic: string | null }) => navigate(decisionsRoute(undefined, { topic: next.topic }), { replace: true })
  return (
    <DocPage>
      <PageHeader>
        <h1>Decisions</h1>
        <p>
          Each decision says what {config.brandMark} chose, why, and what the choice costs. A decision is the design of an
          archived change. The newest decision comes first. The <a href={guidesRoute()}>guides</a> explain the result.
        </p>
      </PageHeader>
      <FilterBar>
        <Input
          type="search"
          placeholder="Filter by date or title…"
          value={query}
          onChange={event => setQuery(event.target.value)}
          aria-label="Filter decisions"
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
      </FilterBar>
      {hiddenCount > 0 && (
        <p className="decision-hidden-note -mt-2 mb-3 text-xs text-muted-foreground">
          {hiddenCount} decisions about this docs site are hidden.{' '}
          <Button variant="link" size="sm" className="h-auto p-0 align-baseline text-foreground underline" onClick={() => go({ topic: ALL_TOPICS })}>
            Show them
          </Button>
        </p>
      )}
      {shown.length === 0
        ? <CatalogEmpty>No decision matches the filter.</CatalogEmpty>
        : (
            <ol className="decision-list m-0 list-none p-0">
              {shown.map(doc => <DecisionRow key={doc.path} doc={doc} />)}
            </ol>
          )}
    </DocPage>
  )
}
