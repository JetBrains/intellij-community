// The page of one bundled markdown doc (a guide, a decision record, a doc under /doc): an optional
// slot above the body, the body, and the source footer, with a table of contents of the body's h2
// headings.

import { type ReactNode, use, useMemo } from 'react'
import { SourceFooter } from '../furniture'
import { type AnchorHref, MarkdownDoc, useAnchorScroll } from '../markdown'
import { markdownBody } from '../markdownSources'
import { markdownTocItems, TocDocPage } from './TocAside'

/** `anchorHref` must be stable across renders, because the table of contents is memoized on it. */
export function MarkdownDocPage({ path, anchor, anchorHref, children }: {
  path: string
  anchor: string | null
  anchorHref: AnchorHref
  children?: ReactNode
}) {
  const source = use(markdownBody(path))
  const tocItems = useMemo(() => markdownTocItems(source, anchorHref), [source, anchorHref])
  useAnchorScroll(anchor, path)
  return (
    <TocDocPage items={tocItems}>
      {children}
      <MarkdownDoc source={source} baseDir={path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''} anchorHref={anchorHref} />
      <SourceFooter path={path} />
    </TocDocPage>
  )
}
