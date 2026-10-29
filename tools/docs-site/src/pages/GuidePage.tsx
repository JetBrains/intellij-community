// A single guide page: the record header with the title and the summary of the guide, then the rendered markdown with
// cross-links, the figure fences of the site, and symbol auto-linking.

import { useCallback } from 'react'
import { NotFoundPage, RecordHeader } from '../furniture'
import { guideRoute, guidesRoute } from '../router'
import { site } from '../site.ts'
import { MarkdownDocPage } from './MarkdownDocPage'

export function GuidePage({ slug, anchor }: { slug: string, anchor: string | null }) {
  const doc = site().index.docForGuideSlug(slug)
  const anchorHref = useCallback((id: string) => guideRoute(slug, id), [slug])
  const header = useCallback(() => doc && <RecordHeader title={doc.title} lead={doc.description} />, [doc])
  if (!doc) {
    return <NotFoundPage kind="guide" name={slug} backHref={guidesRoute()} backLabel="Back to guides" />
  }
  return <MarkdownDocPage path={doc.path} anchor={anchor} anchorHref={anchorHref} header={header} />
}
