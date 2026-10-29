// Shared page furniture on shadcn primitives: interactive pieces compose the vendored
// components (Input, ToggleGroup, Badge, Card), purely presentational pieces stay
// utility-styled components owning their class string. The leading semantic class names
// (`doc-page`, `entity-header`, …) are markers only — the e2e suite and JS select on them.
// Uniform children (the anchors inside actions/chip rows, `small` in link lists) are styled
// with `[&_…]` descendant variants so call sites keep their natural markup.

import {Children, type ReactNode} from 'react'
import {Badge} from './components/ui/badge'
import {Button} from './components/ui/button'
import {Card} from './components/ui/card'
import {Empty, EmptyDescription, EmptyHeader, EmptyTitle} from './components/ui/empty'
import {Item, ItemContent, ItemDescription, ItemFooter, ItemTitle} from './components/ui/item'
import {Separator} from './components/ui/separator'
import {Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue} from './components/ui/select'
import {ToggleGroup, ToggleGroupItem} from './components/ui/toggle-group'
import {cn} from './lib/utils'
import {PageCrumbRow, usePageChrome, type PageMeasure} from './pageLayout'
import {ADR_STATUSES, type AdrStatus} from '../scripts/docMeta'
import {repoPathOf} from '../scripts/sourcePaths'
import {site} from './site'
import {sourceHref} from './sourceRef'

/**
 * The width of a reading page: the document and, at the toc breakpoint, the 220px table of contents with its 8px gap.
 * The prose document is 786px: 70ch of the 16px prose (706px) and its padding. The wide document is 960px. The literal
 * classes let Tailwind see each width.
 */
const READING_WIDTH: Record<PageMeasure, { alone: string, withToc: string }> = {
  prose: { alone: 'w-[786px]', withToc: 'w-[786px] toc:w-[1014px]' },
  wide: { alone: 'w-[960px]', withToc: 'w-[960px] toc:w-[1188px]' },
}

/**
 * The page box of a doc page; `toc` gives the table of contents its column (content first child, TocAside second).
 * In the reading layout the page draws two columns: the document with the crumb row, and the table of contents. The
 * measure of the route gives the width of the document. In the tool and the canvas layout the page is a centered box
 * inside the column of the layout.
 */
export function DocPage({ toc = false, className, children }: { toc?: boolean, className?: string, children: ReactNode }) {
  const { layout, measure } = usePageChrome()
  if (layout === 'reading') {
    const [content, ...aside] = toc ? Children.toArray(children) : [children]
    const width = READING_WIDTH[measure]
    return (
      <div className={cn('doc-page flex max-w-full items-start gap-2 **:scroll-mt-3', toc ? width.withToc : width.alone, toc && 'has-toc', className)} data-measure={measure}>
        <article className="doc-article min-w-0 flex-1 text-base px-10 pt-5 pb-20 max-md:px-4 max-md:pt-3 max-md:pb-15">
          <PageCrumbRow />
          {content}
        </article>
        {aside}
      </div>
    )
  }
  return (
    <div className={cn('doc-page mx-auto max-w-[1160px] px-10 pt-6 pb-20 max-md:px-4 max-md:pt-4 max-md:pb-15', toc && 'has-toc **:scroll-mt-3', className)}>
      <PageCrumbRow />
      {toc
        ? <div className="toc:flex toc:gap-9 toc:[&>:first-child]:min-w-0 toc:[&>:first-child]:flex-1">{children}</div>
        : children}
    </div>
  )
}

/** The page for a route key that names nothing, with a link back to the index. */
export function NotFoundPage({ kind, name, backHref, backLabel }: { kind: string, name: string, backHref: string, backLabel: string }) {
  const page = (
    <DocPage>
      <p>Unknown {kind}: <code>{name}</code>. <a href={backHref}>{backLabel}</a></p>
    </DocPage>
  )
  // a canvas page draws its own panes, so its missing page needs one
  return usePageChrome().layout === 'canvas' ? <div className="min-h-0 flex-1">{page}</div> : page
}

/** End-of-content reference to the markdown file behind a prose page (guides, ADRs, specs). */
export function SourceFooter({ path }: { path: string }) {
  const { href, external } = sourceHref(path)
  return (
    <footer className="source-footer mt-10 text-xs text-muted-foreground">
      <Separator className="mb-3" />
      Source:{' '}
      <a href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})}>
        <code>{repoPathOf(site().config.contentRoot, path)}</code>
        {external && <span aria-hidden="true"> ↗</span>}
      </a>
    </footer>
  )
}

const DECISION_STATUS_CLASS: Record<AdrStatus, string> = {
  'accepted': 'text-muted-foreground',
  'proposed': 'border-sidebar-primary text-foreground',
  'partly-superseded': 'border-warning/60 text-foreground',
  'superseded': 'border-dashed text-muted-foreground',
}

/** Decision record status badge; the accepted status is the norm, so it stays quiet. */
export function DecisionStatusBadge({ status, className }: { status: AdrStatus, className?: string }) {
  return (
    <Badge
      variant={status === 'accepted' ? 'secondary' : 'outline'}
      className={cn('decision-status', DECISION_STATUS_CLASS[status], className)}
      data-status={status}
    >
      {ADR_STATUSES.find(entry => entry.id === status)?.label ?? status}
    </Badge>
  )
}

/** One outline chip of a record header: a status, a date, a topic, an issue. With `href` it is a link. */
export function RecordChip({ href, external = false, className, children, ...rest }: {
  href?: string
  external?: boolean
  className?: string
  children: ReactNode
  /** A marker attribute for the e2e suite, such as the status of a record. */
  'data-status'?: string
}) {
  return (
    <Badge
      {...rest}
      variant="outline"
      className={cn(
        'record-chip h-6 gap-1.5 px-2.5 text-xs font-medium text-foreground/80 [&>svg]:size-3.5! [&>svg]:text-muted-foreground',
        href !== undefined && 'text-foreground no-underline [a]:hover:bg-accent [&>svg]:text-sidebar-primary',
        className,
      )}
      render={href === undefined ? undefined : <a href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})} />}
    >
      {children}
    </Badge>
  )
}

/**
 * The header of a reading document: the display title (balanced, tight), a row of chips, and a lead paragraph. The
 * page draws it in place of the first heading of the markdown body.
 */
export function RecordHeader({ title, chips, chipsClassName, lead }: { title: ReactNode, chips?: ReactNode, chipsClassName?: string, lead?: ReactNode }) {
  return (
    <header className="record-header mb-6">
      <h1 className="m-0 mb-4 text-3xl leading-[1.12] font-[650] tracking-[-0.028em] text-balance max-md:text-2xl">{title}</h1>
      {chips !== undefined && <div className={cn('record-chips mb-5 flex flex-wrap items-center gap-1.5', chipsClassName)}>{chips}</div>}
      {lead !== undefined && lead !== '' && <p className="record-lead m-0 text-lg leading-normal tracking-[-0.006em] text-pretty text-foreground/80">{lead}</p>}
    </header>
  )
}

/** The title of a reading document (RecordHeader), as the h1 of a page header: 34px, tight, and balanced. */
const READING_TITLE = '[&_h1]:text-3xl [&_h1]:leading-[1.12] [&_h1]:font-[650] [&_h1]:tracking-[-0.028em] [&_h1]:text-balance max-md:[&_h1]:text-2xl'

/** True on a reading page, where a page header takes the display title of a document. */
function useReadingTitle(): boolean {
  return usePageChrome().layout === 'reading'
}

/** Catalog page header: h1 + intro paragraphs. */
export function PageHeader({ children }: { children: ReactNode }) {
  const reading = useReadingTitle()
  return (
    <header className={cn('page-header mb-5.5 [&_h1]:mt-0 [&_h1]:mb-1 [&_h1]:text-2xl [&_h1]:tracking-[-0.02em] [&_p]:my-0 [&_p]:text-muted-foreground', reading && cn(READING_TITLE, '[&_h1]:mb-3'))}>
      {children}
    </header>
  )
}

/** Prose-page intro: h1 + dim summary paragraph. */
export function PageIntro({ children }: { children: ReactNode }) {
  const reading = useReadingTitle()
  return (
    <header className={cn('page-intro [&_h1]:mt-0 [&_h1]:mb-2 [&_p]:text-muted-foreground', reading && cn(READING_TITLE, 'mb-6 [&_h1]:mb-4 [&>p:first-of-type]:text-lg [&>p:first-of-type]:leading-normal [&_.chip-links]:mt-2'))}>
      {children}
    </header>
  )
}

/** Spec status badge; `Draft` is the norm across specs, so only non-default statuses render. */
export function SpecStatusChip({ status, className }: { status: string | null, className?: string }) {
  if (!status || status.toLowerCase() === 'draft') {
    return null
  }
  return (
    <Badge variant="secondary" className={cn('spec-status-chip justify-self-start text-xs font-bold uppercase tracking-[0.05em] text-muted-foreground', className)}>
      {status}
    </Badge>
  )
}

/** Entity page header: layer chip, h1, subtitle code line, actions. Long FQNs wrap. */
export function EntityHeader({ children }: { children: ReactNode }) {
  const reading = useReadingTitle()
  return (
    <header className={cn('entity-header mb-5.5 grid justify-items-start gap-1 [&_h1]:m-0 [&_h1]:text-2xl [&_h1]:tracking-[-0.02em] [&>p]:m-0 [&>p]:text-muted-foreground [&_code]:wrap-anywhere', reading && READING_TITLE)}>
      {children}
      <Separator className="mt-2.5" />
    </header>
  )
}

/** Row of EntityAction pills under an entity header. */
export function EntityActions({ children }: { children: ReactNode }) {
  return (
    <div className="entity-actions mt-1 flex flex-wrap gap-2 text-sm">
      {children}
    </div>
  )
}

/** One entity action: a pill link, or with `link` a plain text link (the GitHub actions). */
export function EntityAction({ href, external = false, link = false, children }: {
  href: string
  external?: boolean
  link?: boolean
  children: ReactNode
}) {
  return (
    <Button
      variant={link ? 'link' : 'outline'}
      size="xs"
      className={cn(
        'entity-action font-normal',
        link ? 'action-link h-auto p-0 text-foreground underline' : 'rounded-full text-muted-foreground hover:border-sidebar-primary hover:text-foreground no-underline',
      )}
      render={<a href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})} />}
      nativeButton={false}
    >
      {children}
    </Button>
  )
}

/** Wrapping row of ChipLink pills (modules, specs, features, …). */
export function ChipLinks({ children }: { children: ReactNode }) {
  return (
    <div className="chip-links flex flex-wrap gap-1">
      {children}
    </div>
  )
}

/** One chip of a ChipLinks row: a mono pill link; an inner <code> draws no box of its own. */
export function ChipLink({ href, external = false, className, children }: {
  href: string
  external?: boolean
  className?: string
  children: ReactNode
}) {
  return (
    <Badge
      variant="outline"
      className={cn(
        'chip-link h-auto min-h-5 whitespace-normal font-mono font-normal hover:border-sidebar-primary no-underline [a]:hover:text-foreground [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-[length:inherit]',
        className,
      )}
      render={<a href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})} />}
    >
      {children}
    </Badge>
  )
}

/** Sticky catalog filter bar; children compose Input / FilterChips / Select / FilterCount. The bar is a toolbar, so
 *  its controls take text-sm on a reading page too. */
export function FilterBar({ children }: { children: ReactNode }) {
  return (
    <div className="ep-filters sticky top-0 z-10 mb-5.5 flex flex-wrap items-center gap-2 border-b border-border bg-background/85 py-2 text-sm backdrop-blur [&_[data-slot=input]]:min-w-55 [&_[data-slot=input]]:flex-1 [&_.filter-chips]:order-last [&_.filter-chips]:basis-full">
      {children}
    </div>
  )
}

/**
 * Single-choice filter chip row (ToggleGroup): the built-in "all" chip maps to a null value,
 * and re-clicking the active chip clears back to it.
 */
export function FilterChips({ allLabel, options, value, onChange, 'aria-label': ariaLabel }: {
  'allLabel': string
  'options': { value: string, label: string }[]
  'value': string | null
  'onChange': (value: string | null) => void
  'aria-label'?: string
}) {
  const ALL = '\u0000all'
  return (
    <ToggleGroup
      aria-label={ariaLabel}
      className="filter-chips flex-wrap"
      size="sm"
      spacing={1}
      value={[value ?? ALL]}
      onValueChange={(next: unknown[]) => {
        const picked = next[next.length - 1]
        onChange(picked == null || picked === ALL ? null : String(picked))
      }}
    >
      <ToggleGroupItem value={ALL} className="rounded-full px-2.5 text-xs">{allLabel}</ToggleGroupItem>
      {options.map(option => (
        <ToggleGroupItem key={option.value} value={option.value} className="rounded-full px-2.5 text-xs">
          {option.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

/** Single-choice filter dropdown; '' means "all" (kept as the callers' sentinel). */
export function FilterSelect({ allLabel, options, value, onChange, 'aria-label': ariaLabel }: {
  'allLabel': string
  'options': string[]
  'value': string
  'onChange': (value: string) => void
  'aria-label'?: string
}) {
  const ALL = ' all'
  // items lets SelectValue resolve labels while the popup is unmounted
  const items = [{ value: ALL, label: allLabel }, ...options.map(option => ({ value: option, label: option }))]
  return (
    <Select items={items} value={value === '' ? ALL : value} onValueChange={next => onChange(next === ALL ? '' : String(next))}>
      <SelectTrigger size="sm" aria-label={ariaLabel} className="filter-select">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value={ALL}>{allLabel}</SelectItem>
          {options.map(option => <SelectItem key={option} value={option}>{option}</SelectItem>)}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

export function FilterCount({ children }: { children: ReactNode }) {
  return <Badge variant="secondary" className="filter-count text-xs text-muted-foreground">{children}</Badge>
}

/** What a catalog shows when its FilterBar leaves no row; `children` names the rows ("No extension points …"). */
export function CatalogEmpty({ children }: { children: ReactNode }) {
  return (
    <Empty className="catalog-empty py-10">
      <EmptyHeader>
        <EmptyTitle>Nothing matches</EmptyTitle>
        <EmptyDescription>{children}</EmptyDescription>
      </EmptyHeader>
    </Empty>
  )
}

/** Compact list of LinkListItem rows. */
export function LinkList({ children }: { children: ReactNode }) {
  return (
    <ul className="link-list m-0 grid list-none gap-0.5 p-0">
      {children}
    </ul>
  )
}

/** One LinkList row: the link (or code) as the title, with an optional dim `detail` line. */
export function LinkListItem({ detail, children }: { detail?: ReactNode, children: ReactNode }) {
  return (
    <Item size="xs" render={<li />}>
      <ItemContent className="min-w-0">
        <ItemTitle className="line-clamp-none flex-wrap items-baseline wrap-anywhere">{children}</ItemTitle>
        {detail && <ItemDescription className="line-clamp-none text-xs [&_code]:bg-transparent [&_code]:p-0">{detail}</ItemDescription>}
      </ItemContent>
    </Item>
  )
}

/** Anchored entry card (features, concepts, glossary terms) — the `feature-entry` skin.
 *  `text-base` restores the root size that Card's own `text-sm` would shrink. The card keeps the stock ring of Card. */
export function EntryCard({ id, className, children }: { id?: string, className?: string, children: ReactNode }) {
  return (
    <Card id={id} className={cn('grid gap-2 rounded-lg px-3.5 py-3 text-base', className)}>
      {children}
    </Card>
  )
}

/** Whole-card link (doc lists, spec lists, flow continuations): an outline Item rendered as the anchor. */
export function EntryCardLink({ href, title, description, meta, className }: {
  href: string
  title: ReactNode
  description?: ReactNode
  /** Dim trailing line: a file path, a module list. */
  meta?: ReactNode
  className?: string
}) {
  return (
    <Item
      variant="outline"
      className={cn('bg-card text-foreground hover:border-sidebar-primary no-underline', className)}
      render={<a href={href} />}
    >
      <ItemContent className="min-w-0">
        <ItemTitle className="line-clamp-none">{title}</ItemTitle>
        {description && <ItemDescription className="line-clamp-none">{description}</ItemDescription>}
        {meta && <ItemFooter className="justify-start text-xs text-muted-foreground [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-[length:inherit]">{meta}</ItemFooter>}
      </ItemContent>
    </Item>
  )
}

/** Bordered catalog entry card (extension points, state components, …). */
export function KdocEntry({ id, children }: { id?: string, children: ReactNode }) {
  return (
    <Card id={id} className="kdoc-entry mb-2.5 block rounded-lg px-3 py-2.5">
      {children}
    </Card>
  )
}

/** Symbol line of a KdocEntry; a tag pill must hug its content in the single-column grid. */
export function KdocSymbol({ children }: { children: ReactNode }) {
  return (
    <div className="kdoc-symbol mb-1.5 grid gap-0.5 [&_.tag]:justify-self-start [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-xs [&_code]:text-muted-foreground">
      {children}
    </div>
  )
}

/**
 * Compact labeled fact rows inside a KdocEntry card (the File/Module rows on the storage
 * catalog, Contract/Module on the EP catalog): each child is a `<div><dt>label</dt><dd>value</dd></div>`
 * row — FactList's shape densified to card scale, without the row borders.
 */
export function KdocFacts({ children }: { children: ReactNode }) {
  return (
    <dl className="kdoc-facts m-0 mb-1.5 grid gap-0.5 [&>div]:grid [&>div]:grid-cols-[64px_1fr] [&>div]:items-baseline [&>div]:gap-2 [&_dt]:text-xs [&_dt]:font-medium [&_dt]:tracking-[0.05em] [&_dt]:uppercase [&_dt]:text-muted-foreground [&_dd]:m-0 [&_dd]:text-xs [&_dd_code]:bg-transparent [&_dd_code]:p-0 [&_dd_code]:text-xs [&_dd_code]:wrap-anywhere">
      {children}
    </dl>
  )
}

/**
 * Compact stat tiles under a catalog PageHeader (Application-UI stats pattern, densified to the
 * site ramp). Values are whole-inventory counts, deliberately not filter-reactive — the
 * `filter-count` in the FilterBar stays the authority on the filtered view.
 */
export function StatTiles({ stats }: { stats: { label: string, value: number | string }[] }) {
  return (
    <dl className="catalog-stats m-0 mb-5.5 grid grid-cols-2 gap-3 sm:grid-cols-4">
      {stats.map(stat => (
        <Card key={stat.label} className="block rounded-lg px-4 py-3">
          <dt className="text-xs font-medium tracking-[0.05em] uppercase text-muted-foreground">{stat.label}</dt>
          <dd className="m-0 mt-0.5 text-xl font-semibold tracking-tight">{stat.value}</dd>
        </Card>
      ))}
    </dl>
  )
}

/**
 * Dense description-list fact block for entity pages (Application-UI pattern, densified):
 * each child is a `<div><dt>label</dt><dd>value</dd></div>` row.
 */
export function FactList({ children }: { children: ReactNode }) {
  return (
    <dl className="fact-list m-0 grid border-t border-border [&>div]:grid [&>div]:grid-cols-[140px_1fr] [&>div]:items-baseline [&>div]:gap-3 [&>div]:border-b [&>div]:border-border [&>div]:py-2 [&_dt]:text-xs [&_dt]:font-medium [&_dt]:text-muted-foreground [&_dd]:m-0 [&_dd]:text-sm [&_dd_code]:text-xs [&_dd_code]:wrap-anywhere">
      {children}
    </dl>
  )
}

/**
 * Hover-revealed copyable `#` link for a JSX-rendered section heading (reveal + mobile
 * always-visible styles live in prose.css; markdown headings get theirs injected by
 * transformRendered in markdown.tsx).
 */
export function HeadingAnchor({ href, label = 'Link to this section' }: { href: string, label?: string }) {
  return <a className="heading-anchor" href={href} aria-label={label}>#</a>
}

/** One section of an entity page or of the detail panel. */
export interface EntitySection {
  /** The id of the section heading. The section route ends with it. */
  id: string
  /** The entry in the table of contents, and the heading when `title` is absent. */
  toc: string
  /** The heading when it differs from the entry, for example with a count. */
  title?: ReactNode
  /** `h2` for a main section, `h3` for a list of related entities. */
  heading: 'h2' | 'h3'
  body: ReactNode
}

/** A section, or `false` for a section that the page does not show. */
export type MaybeSection = EntitySection | false

/**
 * One section under its heading. With `anchorHref` the heading carries the section id and a
 * HeadingAnchor. The detail panel renders a section without them.
 */
export function SectionView({ section, anchorHref }: { section: MaybeSection, anchorHref?: (sectionId: string) => string }) {
  if (section === false) {
    return null
  }
  const Heading = section.heading
  return (
    <section>
      <Heading id={anchorHref && section.id}>
        {section.title ?? section.toc}
        {anchorHref && <HeadingAnchor href={anchorHref(section.id)} />}
      </Heading>
      {section.body}
    </section>
  )
}

/** Catalog section grouping entries under a layer/concept heading with a dim id suffix. */
export function UseCaseGroup({ id, children }: { id?: string, children: ReactNode }) {
  return (
    <section id={id} className="use-case-group mt-7 [&_h2_small]:ml-2 [&_h2_small]:text-xs [&_h2_small]:font-medium [&_h2_small]:text-muted-foreground">
      {children}
    </section>
  )
}
