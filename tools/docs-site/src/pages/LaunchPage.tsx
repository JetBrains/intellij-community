// The launch picker: one search over the launch targets of the site data, by name, target, and command. The chosen
// target shows its command with a copy button, and each row of the list has one too. The page selects the entry of
// `launchPicker.defaultName` when the query names none, and links the explainer chapter of the config when the site
// has that explainer.
//
// The list is a cmdk Command, as in the palette. The search is the pure `rankLaunchEntries` (launch.ts), so cmdk does
// not filter.

import { ArrowRightIcon, CheckIcon, CopyIcon } from 'lucide-react'
import { type MouseEvent, useEffect, useMemo, useRef, useState } from 'react'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Command, CommandEmpty, CommandInput, CommandItem, CommandList } from '../components/ui/command'
import { Tooltip, TooltipContent, TooltipTrigger } from '../components/ui/tooltip'
import { DocPage, PageHeader } from '../furniture'
import { initialLaunchEntry, rankLaunchEntries } from '../launch.ts'
import { hrefForRoute, parseRoute } from '../router'
import type { KernelRoute } from '../routeTable.ts'
import { site } from '../site.ts'
import type { LaunchEntry } from '../siteData.ts'

type CopyState = 'idle' | 'copied' | 'failed'

const COPY_TEXT: Record<CopyState, string> = { idle: 'Copy the command', copied: 'Copied', failed: 'Copy failed' }

const NO_ENTRIES: readonly LaunchEntry[] = []

/** A button that copies `command` to the clipboard. Its tooltip says "Copied" for a moment after a copy. */
function CopyCommandButton({ command, label, className }: { command: string, label?: string, className?: string }) {
  const [state, setState] = useState<CopyState>('idle')
  const [hover, setHover] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => {
    if (timer.current !== null) {
      clearTimeout(timer.current)
    }
  }, [])

  const copy = async (event: MouseEvent): Promise<void> => {
    // a copy in a row does not choose the row
    event.stopPropagation()
    try {
      await navigator.clipboard.writeText(command)
      setState('copied')
    }
    catch {
      setState('failed')
    }
    if (timer.current !== null) {
      clearTimeout(timer.current)
    }
    timer.current = setTimeout(() => setState('idle'), 1600)
  }

  return (
    <Tooltip open={hover || state !== 'idle'} onOpenChange={setHover}>
      <TooltipTrigger
        render={(
          <Button
            variant="outline"
            size={label === undefined ? 'icon-sm' : 'sm'}
            className={className}
            aria-label={label === undefined ? `Copy ${command}` : undefined}
            onClick={copy}
          />
        )}
      >
        {state === 'copied' ? <CheckIcon data-icon="inline-start" aria-hidden="true" /> : <CopyIcon data-icon="inline-start" aria-hidden="true" />}
        {label}
      </TooltipTrigger>
      <TooltipContent>{COPY_TEXT[state]}</TooltipContent>
    </Tooltip>
  )
}

/** The route of the explainer chapter of the config, or null when the site has no such explainer or chapter. */
function explainHref(path: string | null): string | null {
  if (path === null) {
    return null
  }
  const parsed = parseRoute(path)
  if (parsed.page !== 'explainer') {
    return null
  }
  const route = parsed as Extract<KernelRoute, { page: 'explainer' }>
  const doc = site().index.docForExplainerSlug(route.slug)
  const known = doc !== undefined && (route.chapter === null || doc.chapters.some(chapter => chapter.id === route.chapter))
  return known ? hrefForRoute(route) : null
}

function HalfBadge({ entry }: { entry: LaunchEntry }) {
  return entry.half === 'community'
    ? <Badge variant="outline" className="launch-half text-muted-foreground">Community</Badge>
    : null
}

function ChosenEntry({ entry, explain }: { entry: LaunchEntry, explain: string | null }) {
  return (
    <section className="launch-chosen grid gap-3 rounded-lg border border-border bg-card p-4 md:p-5" aria-label="The chosen launch target">
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        <h2 className="launch-chosen-name m-0 text-lg font-semibold">{entry.name}</h2>
        <HalfBadge entry={entry} />
      </div>
      <div className="flex min-w-0 items-center gap-2 rounded-lg bg-muted px-3 py-2">
        <code className="launch-command min-w-0 flex-1 bg-transparent p-0 font-mono text-sm wrap-break-word text-foreground">{entry.command}</code>
        <CopyCommandButton command={entry.command} label="Copy" className="launch-copy shrink-0" />
      </div>
      <p className="m-0 text-xs text-muted-foreground">
        Run it from the repository root. The run configuration is <code>{entry.configurationFile}</code>.
      </p>
      {explain !== null && (
        <a href={explain} className="launch-explain inline-flex items-center gap-1 text-sm font-medium">
          How a target starts the IDE
          <ArrowRightIcon aria-hidden="true" className="size-4" />
        </a>
      )}
    </section>
  )
}

export function LaunchPage({ query }: { query: string | null }) {
  const { config, index } = site()
  const entries = index.data.launch ?? NO_ENTRIES
  const defaultName = config.launchPicker?.defaultName ?? ''
  const [search, setSearch] = useState(query ?? '')
  const [chosen, setChosen] = useState(() => initialLaunchEntry(entries, query ?? '', defaultName)?.target ?? '')
  const results = useMemo(() => rankLaunchEntries(entries, search), [entries, search])
  // the keyboard highlight starts on the first row: cmdk scrolls the highlight into view, and a row far down the list
  // would scroll the page away from its title on a phone. A check marks the chosen row
  const [highlight, setHighlight] = useState(() => results[0]?.target ?? '')
  const explain = useMemo(() => explainHref(config.launchPicker?.explainChapter ?? null), [config])

  // a palette result opens this page again with another query
  useEffect(() => {
    setSearch(query ?? '')
    setChosen(initialLaunchEntry(entries, query ?? '', defaultName)?.target ?? '')
    setHighlight(rankLaunchEntries(entries, query ?? '')[0]?.target ?? '')
  }, [entries, query, defaultName])

  const onSearch = (value: string): void => {
    setSearch(value)
    // the best match follows the search text, so the command above the list is the first row
    const first = rankLaunchEntries(entries, value)[0]
    if (first !== undefined) {
      setChosen(first.target)
      setHighlight(first.target)
    }
  }

  const entry = entries.find(candidate => candidate.target === chosen) ?? null
  return (
    <DocPage className="launch-page">
      <PageHeader>
        <h1>Start the IDE</h1>
        <p>
          Each launch target builds a dev distribution of one product and starts the IDE. Find a run configuration by its
          name, its Bazel target, or its command, then copy the command.
        </p>
      </PageHeader>
      {entry !== null && <ChosenEntry entry={entry} explain={explain} />}
      <Command
        shouldFilter={false}
        value={highlight}
        onValueChange={setHighlight}
        label="Launch targets"
        className="launch-picker mt-6 h-auto rounded-lg border border-border bg-card"
      >
        <CommandInput
          placeholder="Search by name, target, or command…"
          aria-label="Search the launch targets"
          value={search}
          onValueChange={onSearch}
        />
        <div className="launch-count px-3 pt-2 pb-1 text-xs text-muted-foreground tabular-nums">
          {results.length === entries.length ? `${entries.length} launch targets` : `${results.length} of ${entries.length} launch targets`}
        </div>
        <CommandList className="launch-results max-h-[min(32rem,60dvh)] p-1">
          <CommandEmpty>No launch target matches.</CommandEmpty>
          {results.map(result => (
            <CommandItem
              key={result.target}
              value={result.target}
              data-checked={result.target === chosen}
              onSelect={() => setChosen(result.target)}
              className="launch-row gap-3 py-2"
            >
              <span className="grid min-w-0 flex-1">
                <strong className="launch-row-name truncate font-medium">{result.name}</strong>
                <small className="launch-row-target truncate font-mono text-xs text-muted-foreground">{result.target}</small>
              </span>
              <HalfBadge entry={result} />
              <CopyCommandButton command={result.command} className="launch-row-copy" />
            </CommandItem>
          ))}
        </CommandList>
      </Command>
    </DocPage>
  )
}
