// Markdown rendering shared by all pages. KDoc, specs, and prose docs are all markdown;
// relative links between them are rewritten to site routes when the target is bundled, and a
// relative picture gets the URL of the copy that harvest bundles (src/docAssets.ts).
//
// A prose doc can hold fenced blocks that render as a figure: the kernel renders ```diagram (and its alias
// ```air-diagram) as a hand-laid diagram, and a site registers its own fence names through `createSite`.

import { marked } from 'marked'
import { Fragment, lazy, Suspense, type MouseEvent, type ReactNode, useEffect, useMemo } from 'react'
import { parseDiagramFence } from '../scripts/diagram.ts'
import { isExternalPicture, picturePath } from '../scripts/docAssets.ts'
import { contentPathOf, repoPathOf, resolveRepoPath, SOURCE_PATH_PATTERN } from '../scripts/sourcePaths.ts'
import { docAssetUrl } from './docAssets'
import { decisionRoute, docRoute, explainerRoute, guideRoute, isDecisionPath, normalizeMarkdownAppHref } from './router'
import { codeSpanTarget } from './codeSpanLink.ts'
import { site } from './site.ts'
import { guideSlug } from './siteData.ts'
import { repoSourceHref, sourceHref } from './sourceRef'
import { resolveSymbolText } from './symbolLinks'

const HandLaidFigure = lazy(() => import('./graph/HandLaidDiagram'))

export function slugify(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}

/** The spec id of a content-relative path under a spec root of the site config, or null. */
function specIdOfPath(path: string): string | null {
  const { contentRoot, specs } = site().config
  if (!path.endsWith(specs.suffix)) {
    return null
  }
  for (const root of specs.roots) {
    const prefix = contentPathOf(contentRoot, root)
    if (prefix !== null && path.startsWith(`${prefix}/`)) {
      return path.slice(prefix.length + 1, -specs.suffix.length)
    }
  }
  return null
}

/** Site route for a content-relative path, or null when the target is not bundled. */
export function routeForPath(path: string): string | null {
  const { config, index, refLinks } = site()
  const specId = specIdOfPath(path)
  if (specId !== null) {
    return refLinks.specLink?.(specId)?.href ?? null
  }
  const page = refLinks.docPage?.(path) ?? null
  if (page !== null) {
    return page
  }
  if (!index.docsByPath.has(path)) {
    return null
  }
  if (isDecisionPath(path)) {
    return decisionRoute(path)
  }
  if (path.startsWith(`${config.docs.explainers}/`)) {
    return explainerRoute(guideSlug(path))
  }
  return path.startsWith(`${config.docs.root}/`)
    ? guideRoute(guideSlug(path))
    : docRoute(path)
}

/** Human title of a bundled content-relative path (spec name or doc title), or null. */
function titleForPath(path: string): string | null {
  const specId = specIdOfPath(path)
  if (specId !== null) {
    return site().refLinks.specLink?.(specId)?.title ?? null
  }
  return site().index.docsByPath.get(path)?.title || null
}

/** A page of the site: its route and its title, null when the target has no known title. */
export interface SitePage {
  href: string
  title: string | null
}

/** The page of the site for a repo-root-relative path, or null when the site bundles no page of it. */
export function pageOfRepoPath(repoPath: string): SitePage | null {
  const contentPath = contentPathOf(site().config.contentRoot, repoPath)
  if (contentPath === null) {
    return null
  }
  const href = routeForPath(contentPath)
  return href === null ? null : { href, title: titleForPath(contentPath) }
}

/** Appends an in-page anchor to a site route as an extra route segment. */
function routeWithAnchor(route: string, fragment: string | undefined): string {
  return fragment ? `${route}/${encodeURIComponent(fragment)}` : route
}

/**
 * Finds the element for a markdown anchor. Heading ids use this site's slugify; links written
 * for GitHub-style slugs (punctuation removed instead of dashed) are matched by comparing
 * dash-stripped ids.
 */
function findAnchorElement(anchor: string): Element | null {
  const exact = document.getElementById(anchor)
  if (exact) {
    return exact
  }
  const wanted = anchor.toLowerCase().replace(/-/g, '')
  for (const candidate of document.querySelectorAll('[id]')) {
    if (candidate.id.toLowerCase().replace(/-/g, '') === wanted) {
      return candidate
    }
  }
  return null
}

/** Scrolls to `anchor` (and flashes it) once the routed page content is rendered. */
export function useAnchorScroll(anchor: string | null, ready: unknown): void {
  useEffect(() => {
    if (anchor) {
      const element = findAnchorElement(anchor)
      element?.scrollIntoView({ block: 'start' })
      element?.classList.add('anchor-highlight')
    }
  }, [anchor, ready])
}

/** Delegated click handler: in-page `#anchor` links scroll instead of changing the route. */
export function handleMarkdownClick(event: MouseEvent<HTMLElement>): void {
  const link = (event.target as HTMLElement).closest('a')
  const href = link?.getAttribute('href') ?? ''
  if (href.startsWith('#') && !href.startsWith('#/')) {
    event.preventDefault()
    findAnchorElement(decodeURIComponent(href.slice(1)))?.scrollIntoView({ block: 'start' })
  }
}

/** Builds a routed (copyable) href for a heading id; without it heading anchors scroll only. */
export type AnchorHref = (id: string) => string

/** The id transformRendered assigns to a rendered heading — the single source of truth, so
 *  extractMarkdownHeadings can never drift from what ends up in the DOM. */
function headingIdOf(heading: Element): string {
  return slugify(heading.textContent ?? '')
}

export interface MarkdownHeading {
  id: string
  title: string
  level: number
}

/** Heading list of a markdown source, with the same ids renderMarkdown injects. Fenced blocks
 *  (including the figure fences) are code to marked, so their contents never produce headings. */
export function extractMarkdownHeadings(source: string): MarkdownHeading[] {
  const html = marked.parse(source, { async: false, gfm: true }) as string
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return [...doc.querySelectorAll('h1, h2, h3, h4')].map(heading => ({
    id: headingIdOf(heading),
    title: (heading.textContent ?? '').trim(),
    level: Number(heading.tagName.charAt(1)),
  }))
}

function transformRendered(html: string, baseDir: string, anchorHref?: AnchorHref): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  for (const heading of doc.querySelectorAll('h1, h2, h3, h4')) {
    heading.id = headingIdOf(heading)
    // hover-revealed copyable anchor: a routed URL when the host page supports anchor routes,
    // otherwise an in-page `#` href handled by handleMarkdownClick (scroll only)
    const anchor = doc.createElement('a')
    anchor.className = 'heading-anchor'
    anchor.setAttribute('href', anchorHref ? anchorHref(heading.id) : `#${heading.id}`)
    anchor.setAttribute('aria-label', 'Link to this section')
    anchor.textContent = '#'
    heading.append(anchor)
  }
  for (const anchor of [...doc.querySelectorAll('a')]) {
    const href = anchor.getAttribute('href') ?? ''
    if (/^(https?:)?\/\//.test(href) || href.startsWith('mailto:')) {
      anchor.target = '_blank'
      anchor.rel = 'noreferrer'
      continue
    }
    const appHref = normalizeMarkdownAppHref(href)
    if (appHref) {
      anchor.setAttribute('href', appHref)
      continue
    }
    if (href.startsWith('#')) {
      // in-page anchor: handled by handleMarkdownClick (scroll, don't touch the route)
      continue
    }
    const [pathPart, fragment] = href.split('#')
    const repoPath = resolveRepoPath(site().config.contentRoot, baseDir, decodeURIComponent(pathPart))
    const page = pageOfRepoPath(repoPath)
    if (page !== null) {
      anchor.setAttribute('href', routeWithAnchor(page.href, fragment))
      continue
    }
    // not a bundled doc: bundled source files open in the in-site viewer, everything else on
    // GitHub — never a dead link
    const lineMatch = fragment ? /^L(\d+)$/.exec(fragment) : null
    const { href: target, external } = repoSourceHref(repoPath, lineMatch ? Number(lineMatch[1]) : null)
    anchor.setAttribute('href', target)
    if (external) {
      anchor.target = '_blank'
      anchor.rel = 'noreferrer'
      anchor.title = `${repoPath} on GitHub`
    }
  }
  // linkify bare relative paths written as code (e.g. spec "References" sections)
  for (const code of [...doc.querySelectorAll('code')]) {
    if (code.closest('a, pre')) {
      continue
    }
    const text = code.textContent ?? ''
    if (SOURCE_PATH_PATTERN.test(text)) {
      const { config, index } = site()
      const target = codeSpanTarget(text, baseDir, config.contentRoot, index.data.codeSpanPaths?.[baseDir])
      if (target === null) {
        // the span names no file of the repository, for example a path inside a jar
        continue
      }
      const { repoPath, fragment, label } = target
      const pathPart = text.split('#')[0]
      const page = pageOfRepoPath(repoPath)
      if (page !== null) {
        const link = doc.createElement('a')
        link.href = routeWithAnchor(page.href, fragment)
        link.title = repoPath
        // bundled targets with a known title (specs, docs) read as prose links; the resolved
        // path replaces the authored `../…` form when no title exists
        if (page.title) {
          link.textContent = page.title
        }
        else {
          link.className = 'code-link'
          link.textContent = label
        }
        code.replaceWith(link)
        continue
      }
      // path-looking spans with a `/` are treated as source references (bare file names like
      // `plugin.xml` stay plain code — resolving those against baseDir would guess wrong)
      if (pathPart.includes('/') && !pathPart.endsWith('.md')) {
        const lineMatch = fragment ? /^L(\d+)$/.exec(fragment) : null
        const { href, external } = repoSourceHref(repoPath, lineMatch ? Number(lineMatch[1]) : null)
        const link = doc.createElement('a')
        link.href = href
        link.className = 'code-link'
        link.textContent = label
        link.title = repoPath
        if (external) {
          link.target = '_blank'
          link.rel = 'noreferrer'
        }
        code.replaceWith(link)
      }
      continue
    }
    // auto-link inline code that names a harvested code symbol to its source
    const location = resolveSymbolText(text)
    if (location) {
      const { href, external } = sourceHref(location.file, location.line)
      const link = doc.createElement('a')
      link.href = href
      link.className = 'symbol-link'
      link.title = `${repoPathOf(site().config.contentRoot, location.file)}:${location.line}`
      if (external) {
        link.target = '_blank'
        link.rel = 'noreferrer'
      }
      code.replaceWith(link)
      link.append(code.cloneNode(true))
    }
  }
  // after the link pass, so the caption link keeps the asset URL
  for (const image of [...doc.querySelectorAll('img')]) {
    transformPicture(doc, image, baseDir)
  }
  return doc.body.innerHTML
}

/**
 * Gives a relative picture the URL of its harvested copy. A picture alone in its paragraph becomes a
 * figure: a frame that keeps the natural width and scrolls, and a caption from the markdown title with
 * a link that opens the picture. A picture that harvest did not copy becomes an inline error.
 */
function transformPicture(doc: Document, image: HTMLImageElement, baseDir: string): void {
  const source = image.getAttribute('src') ?? ''
  if (isExternalPicture(source)) {
    return
  }
  const path = picturePath(site().config.contentRoot, baseDir, source)
  const url = path === null ? null : docAssetUrl(path)
  if (url === null) {
    const error = doc.createElement('span')
    error.className = 'doc-figure-error'
    error.textContent = `picture not harvested: ${source}`
    image.replaceWith(error)
    return
  }
  image.setAttribute('src', url)
  const paragraph = image.parentElement
  if (paragraph?.tagName !== 'P' || [...paragraph.childNodes].some(node => node !== image && (node.textContent ?? '').trim() !== '')) {
    return
  }
  const title = image.getAttribute('title')
  image.removeAttribute('title')
  const frame = doc.createElement('div')
  frame.className = 'doc-figure-frame'
  frame.append(image)
  const link = doc.createElement('a')
  link.setAttribute('href', url)
  link.target = '_blank'
  link.rel = 'noreferrer'
  link.textContent = 'open the picture →'
  // the print styles hide this span, so the separator goes with the link
  const open = doc.createElement('span')
  open.className = 'doc-figure-open'
  open.append(title ? ' — ' : '', link)
  const caption = doc.createElement('figcaption')
  caption.append(title ?? '', open)
  const figure = doc.createElement('figure')
  figure.className = 'doc-figure'
  figure.append(frame, caption)
  paragraph.replaceWith(figure)
}

export function renderMarkdown(source: string, baseDir: string, anchorHref?: AnchorHref): string {
  const html = marked.parse(source, { async: false, gfm: true }) as string
  return transformRendered(html, baseDir, anchorHref)
}

/* Typography-plugin configuration, Tailwind-template style: element tweaks ride on `prose-*`
   modifier utilities; the plugin's palette is wired to the semantic tokens in prose.css. */
const proseClassName = [
  'markdown-body prose max-w-none',
  // tinted always-on underline that saturates on hover (Syntax-template treatment)
  'prose-a:font-normal prose-a:underline prose-a:underline-offset-4',
  // inline code renders as chips (base layer), not prose's bold backtick-quoted style
  'prose-code:font-normal prose-code:before:content-none prose-code:after:content-none',
  // plain dim citation blocks, not prose's italic quoted style
  'prose-blockquote:font-normal prose-blockquote:not-italic',
  // section headings without a rule, balanced like the record title; bordered code blocks
  'prose-h1:text-balance prose-h2:text-balance prose-h2:tracking-[-0.014em]',
  'prose-pre:rounded-lg prose-pre:border prose-pre:border-border',
  // full-grid tables instead of prose's horizontal-rules-only style
  'prose-table:border-collapse',
  'prose-th:border prose-th:border-border prose-th:px-3 prose-th:py-1.5',
  'prose-td:border prose-td:border-border prose-td:px-3 prose-td:py-1.5',
  // dense lists
  'prose-li:my-0.75',
].join(' ')

/** Inline rendering for a statement that already sits in a list item or a table cell: no block wrappers. */
export function renderMarkdownInline(source: string, baseDir: string): string {
  const html = marked.parseInline(source, { async: false, gfm: true }) as string
  return transformRendered(html, baseDir)
}

export function Markdown({ source, baseDir, anchorHref, inline = false }: {
  source: string
  baseDir: string
  anchorHref?: AnchorHref
  /** Render inline (a `span` without paragraphs); the host element supplies the block. */
  inline?: boolean
}) {
  const html = useMemo(
    () => inline ? renderMarkdownInline(source, baseDir) : renderMarkdown(source, baseDir, anchorHref),
    [source, baseDir, anchorHref, inline],
  )
  if (inline) {
    return <span className={proseClassName} onClick={handleMarkdownClick} dangerouslySetInnerHTML={{ __html: html }} />
  }
  return <article className={proseClassName} onClick={handleMarkdownClick} dangerouslySetInnerHTML={{ __html: html }} />
}

/**
 * Inline-only markdown for authored one-liners that live as plain strings rather than in a `.md`
 * file (the Behavior page's prose in `src/behavior/behaviorData.ts`, which must stay import-free so
 * the gate can load it): `` `code` ``, emphasis and links render, and nothing is wrapped in a
 * block, so it drops straight into a `<dd>`, a heading, or a list item.
 */
export function InlineMarkdown({ source }: { source: string }) {
  const html = useMemo(() => marked.parseInline(source, { async: false, gfm: true }) as string, [source])
  return <span dangerouslySetInnerHTML={{ __html: html }} />
}

// --- prose docs with figure fences: hand-laid diagrams (```diagram and ```air-diagram, ADR 0203 of Air) and the fences of the site

type DocSegment =
  | { kind: 'html', html: string }
  | { kind: 'fence', name: string, body: string }

/** The kernel fence names. ```air-diagram is the first name of ```diagram; the Air docs still use it. */
const DIAGRAM_FENCES = ['diagram', 'air-diagram']

/** The fence pattern for the kernel fence names and the fence names of the site. */
function docFencePattern(): RegExp {
  const names = [...Object.keys(site().fenceRenderers), ...DIAGRAM_FENCES].map(name => name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  return new RegExp('^```(' + names.join('|') + ')[ \\t]*\\n([\\s\\S]*?)^```[ \\t]*$', 'gm')
}

/** Splits a doc on its figure fences; markdown in between goes through the normal pipeline. */
function renderDocSegments(source: string, baseDir: string, anchorHref?: AnchorHref): DocSegment[] {
  const segments: DocSegment[] = []
  let cursor = 0
  for (const fence of source.matchAll(docFencePattern())) {
    const before = source.slice(cursor, fence.index)
    if (before.trim() !== '') {
      segments.push({ kind: 'html', html: renderMarkdown(before, baseDir, anchorHref) })
    }
    segments.push({ kind: 'fence', name: fence[1], body: fence[2] })
    cursor = fence.index + fence[0].length
  }
  const rest = source.slice(cursor)
  if (rest.trim() !== '') {
    segments.push({ kind: 'html', html: renderMarkdown(rest, baseDir, anchorHref) })
  }
  return segments
}

/** The inline error of a fence that does not parse. A site fence renderer shows its errors with it too. */
export function fenceError(className: string, label: string, errors: string[]): ReactNode {
  return <pre className={`${className} rounded-md border border-destructive px-3.5 py-2 text-sm whitespace-pre-wrap text-destructive`}>{label}: {errors.join('; ')}</pre>
}

/** A ```diagram fence: the hand-laid figure from the lazy React Flow chunk. The harvest already checked that the id exists. */
function DiagramSegment({ body }: { body: string }) {
  const { spec, errors } = useMemo(() => parseDiagramFence(body), [body])
  const diagram = spec === null ? undefined : site().index.diagramsById.get(spec.id)
  if (spec === null || diagram === undefined) {
    return fenceError('air-diagram-error', 'air-diagram block error', spec === null ? errors : [`no diagram '${spec.id}'`])
  }
  return (
    <Suspense fallback={<div className="mini-graph my-5.5 overflow-hidden rounded-lg border border-border bg-muted" style={{ height: spec.height ?? 360 }} aria-hidden="true" />}>
      <HandLaidFigure diagram={diagram} initialStep={spec.step} height={spec.height} title={spec.title} />
    </Suspense>
  )
}

/** A prose doc renderer that turns the figure fences into figures: a ```diagram fence into a hand-laid diagram, and a site fence through its renderer. */
export function MarkdownDoc({ source, baseDir, anchorHref }: { source: string, baseDir: string, anchorHref?: AnchorHref }) {
  const segments = useMemo(() => renderDocSegments(source, baseDir, anchorHref), [source, baseDir, anchorHref])
  const renderers = site().fenceRenderers
  return (
    <div className="markdown-doc" onClick={handleMarkdownClick}>
      {segments.map((segment, index) => {
        if (segment.kind === 'html') {
          return <article key={index} className={proseClassName} dangerouslySetInnerHTML={{ __html: segment.html }} />
        }
        if (DIAGRAM_FENCES.includes(segment.name)) {
          return <DiagramSegment key={index} body={segment.body} />
        }
        return <Fragment key={index}>{renderers[segment.name](segment.body)}</Fragment>
      })}
    </div>
  )
}
