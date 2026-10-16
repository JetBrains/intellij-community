// ⌘K / Ctrl-K command palette: one search over the search providers of the site (search.ts). A code symbol links
// to the source viewer, or out to GitHub source. Built on the Command (cmdk) dialog; shouldFilter is off — search.ts
// owns matching and rank.

import { ExternalLinkIcon } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import {
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '../components/ui/command'
import { navigate } from '../router'
import { searchKinds, searchSite, type SearchResult } from '../search'
import { site } from '../site.ts'

const OPEN_PALETTE_EVENT = 'docs-site-open-palette'

/** Opens the palette from anywhere on the page, such as the Home page search field. */
export function openPalette(): void {
  window.dispatchEvent(new Event(OPEN_PALETTE_EVENT))
}

/** Hook: global ⌘K / Ctrl-K / '/' shortcut and {@link openPalette}. Returns [open, setOpen]. */
export function usePalette(): [boolean, (open: boolean) => void] {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    const onOpen = () => setOpen(true)
    window.addEventListener(OPEN_PALETTE_EVENT, onOpen)
    return () => window.removeEventListener(OPEN_PALETTE_EVENT, onOpen)
  }, [])
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.key === 'k' || event.key === 'K') && (event.metaKey || event.ctrlKey)) {
        event.preventDefault()
        setOpen(current => !current)
        return
      }
      if (event.key !== '/') {
        return
      }
      const target = event.target as HTMLElement | null
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return
      }
      event.preventDefault()
      setOpen(true)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
  return [open, setOpen]
}

export function CommandPalette({ open, onOpenChange }: { open: boolean, onOpenChange: (open: boolean) => void }) {
  const [query, setQuery] = useState('')
  const text = site().palette

  const results = useMemo(() => searchSite(query), [query])
  const grouped = useMemo(() => {
    const byKind = new Map<string, SearchResult[]>()
    for (const result of results) {
      let list = byKind.get(result.kind)
      if (!list) {
        list = []
        byKind.set(result.kind, list)
      }
      list.push(result)
    }
    // groups render in the provider order of the site
    return searchKinds().filter(kind => byKind.has(kind)).map(kind => ({ kind, results: byKind.get(kind)! }))
  }, [results])

  useEffect(() => {
    if (!open) {
      setQuery('')
    }
  }, [open])

  const openResult = (result: SearchResult) => {
    if (result.external) {
      window.open(result.route, '_blank', 'noreferrer')
    }
    else {
      navigate(result.route)
    }
    onOpenChange(false)
  }

  return (
    <CommandDialog
      open={open}
      onOpenChange={onOpenChange}
      title={text.title}
      description={text.description}
      className="palette top-[12vh] w-[min(620px,calc(100%-1rem))]"
    >
      <Command shouldFilter={false}>
        <CommandInput
          placeholder={text.placeholder}
          value={query}
          onValueChange={setQuery}
        />
        <CommandList className="palette-results max-h-[min(24rem,60dvh)]">
          {query.trim().length >= 2 && <CommandEmpty>No matches.</CommandEmpty>}
          {grouped.map(group => (
            <CommandGroup key={group.kind} heading={group.kind}>
              {group.results.map(result => (
                <CommandItem
                  key={`${result.ref}-${result.route}`}
                  value={`${result.ref}-${result.route}`}
                  onSelect={() => openResult(result)}
                >
                  <span className="min-w-0 flex items-baseline gap-2">
                    <strong className="font-medium whitespace-nowrap">{result.title}</strong>
                    <small className="truncate text-muted-foreground">{result.detail}</small>
                  </span>
                  {result.external && <ExternalLinkIcon className="ml-auto text-muted-foreground" aria-label="opens on GitHub" />}
                </CommandItem>
              ))}
            </CommandGroup>
          ))}
        </CommandList>
      </Command>
    </CommandDialog>
  )
}
