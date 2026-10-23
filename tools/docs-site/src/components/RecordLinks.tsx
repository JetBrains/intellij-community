// "Linked from this record": the links of a decision record body, under its table of contents. The links come from the
// rendered body, so they resolve as the body links do: another record, a doc, a source file, an issue.

import { ExternalLinkIcon, FileCodeIcon, FileTextIcon, ScrollTextIcon } from 'lucide-react'
import { useMemo, type ComponentType } from 'react'
import { renderMarkdown } from '../markdown'
import { decisionsRoute } from '../router'

interface RecordLink {
  href: string
  label: string
  icon: ComponentType<{ className?: string, 'aria-hidden'?: boolean }>
  external: boolean
}

/** The most links that the block shows. */
const LINK_LIMIT = 8

/** The links of the rendered body, in body order, each once, without the heading anchors and the page itself. */
export function recordLinks(source: string, baseDir: string, selfHref: string): RecordLink[] {
  const doc = new DOMParser().parseFromString(renderMarkdown(source, baseDir), 'text/html')
  const decisions = decisionsRoute()
  const seen = new Set<string>([selfHref])
  const links: RecordLink[] = []
  for (const anchor of doc.querySelectorAll('a:not(.heading-anchor)')) {
    const href = anchor.getAttribute('href') ?? ''
    const text = (anchor.textContent ?? '').trim()
    const path = href.split('#')[0]
    if (href === '' || href.startsWith('#') || text === '' || seen.has(path)) {
      continue
    }
    seen.add(path)
    const external = anchor.getAttribute('target') === '_blank'
    if (/youtrack\.jetbrains\.com\/issue\//.test(href)) {
      links.push({ href, label: `${text} on YouTrack`, icon: ExternalLinkIcon, external: true })
    }
    else if (anchor.classList.contains('code-link')) {
      links.push({ href, label: text, icon: FileCodeIcon, external })
    }
    else if (!external) {
      links.push({ href, label: text, icon: path.startsWith(decisions) ? ScrollTextIcon : FileTextIcon, external })
    }
  }
  return links.slice(0, LINK_LIMIT)
}

/** The block of the links of a record, or nothing when the body links nothing. */
export function RecordLinks({ source, baseDir, selfHref }: { source: string, baseDir: string, selfHref: string }) {
  const links = useMemo(() => recordLinks(source, baseDir, selfHref), [source, baseDir, selfHref])
  if (links.length === 0) {
    return null
  }
  return (
    <div className="record-links mt-6 grid gap-0.5">
      <div className="px-1.5 pb-1 text-xs text-muted-foreground">Linked from this record</div>
      {links.map(link => (
        <a
          key={link.href}
          href={link.href}
          {...(link.external ? { target: '_blank', rel: 'noreferrer' } : {})}
          className="flex min-h-7 items-center gap-2 rounded-md px-1.5 py-1 leading-snug text-foreground hover:bg-hover hover:no-underline"
        >
          <link.icon className="size-3.5 flex-none text-muted-foreground" aria-hidden />
          <span className="min-w-0 wrap-anywhere">{link.label}</span>
        </a>
      ))}
    </div>
  )
}
