// The page of one bundled markdown doc (a guide, a decision record, a doc under /doc): an optional
// header in place of the first heading of the body, an optional slot above the body, the body, and the
// source footer, with a table of contents of the body's h2 headings.

import { type ReactNode, use, useMemo } from 'react'
import { SourceFooter } from '../furniture'
import { type AnchorHref, MarkdownDoc, useAnchorScroll } from '../markdown'
import { markdownBody } from '../markdownSources'
import { markdownTocItems, TocDocPage } from './TocAside'

/** The body without the `# ` heading that starts it, which a page header replaces. */
export function withoutTitle(source: string): string {
  return source.replace(/^\s*# .*\n+/, '')
}

/** `anchorHref` must be stable across renders, because the table of contents is memoized on it. */
export function MarkdownDocPage({ path, anchor, anchorHref, header, body, aside, children }: {
  path: string
  anchor: string | null
  anchorHref: AnchorHref
  /** The header of the page, from the body. It replaces the first heading of the body. */
  header?: (source: string) => ReactNode
  /** Changes the body before it renders, for example to take out a line that the header shows. */
  body?: (source: string) => string
  /** A block under the table of contents, from the body. */
  aside?: (source: string) => ReactNode
  children?: ReactNode
}) {
  const source = use(markdownBody(path))
  const tocItems = useMemo(() => markdownTocItems(source, anchorHref), [source, anchorHref])
  const shown = useMemo(() => {
    const untitled = header === undefined ? source : withoutTitle(source)
    return body === undefined ? untitled : body(untitled)
  }, [source, header, body])
  useAnchorScroll(anchor, path)
  return (
    <TocDocPage items={tocItems} extra={aside?.(source)}>
      {header?.(source)}
      {children}
      <MarkdownDoc source={shown} baseDir={path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''} anchorHref={anchorHref} />
      <SourceFooter path={path} />
    </TocDocPage>
  )
}
