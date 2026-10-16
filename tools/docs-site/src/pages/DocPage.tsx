// One bundled markdown doc under /doc: a doc that no guide, explainer, or decision route owns.

import { useCallback } from 'react'
import { NotFoundPage } from '../furniture'
import { routeForPath } from '../markdown'
import { docRoute, homeRoute } from '../router'
import { site } from '../site.ts'
import { MarkdownDocPage } from './MarkdownDocPage'

/** The page of the home doc of the site, or the Home page when the site has no home doc. */
function overviewRoute(): string {
  const home = site().config.docs.home
  return (home === null ? null : routeForPath(home)) ?? homeRoute()
}

export function DocPage({ path, anchor }: { path: string, anchor: string | null }) {
  const anchorHref = useCallback((id: string) => docRoute(path, id), [path])
  if (!site().index.docsByPath.has(path)) {
    return <NotFoundPage kind="document" name={path} backHref={overviewRoute()} backLabel="Back to overview" />
  }
  return <MarkdownDocPage path={path} anchor={anchor} anchorHref={anchorHref} />
}
