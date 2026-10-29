// The route of a bundled doc. The Guides page, the section tree of the guides, and the site pages link a doc by it.

import { decisionRoute, docRoute, explainerRoute, guideRoute, isDecisionPath } from './router'
import { site } from './site.ts'
import { guideSlug, type HarvestedDoc } from './siteData.ts'

/** Route for a bundled doc: a decision record has its own page, guides and notes under the docs root get pretty slugs, the rest use /doc. */
export function routeForDoc(doc: HarvestedDoc): string {
  if (isDecisionPath(doc.path)) {
    return decisionRoute(doc.path)
  }
  if (doc.kind === 'explainer') {
    return explainerRoute(guideSlug(doc.path))
  }
  return doc.path.startsWith(`${site().config.docs.root}/`)
    ? guideRoute(guideSlug(doc.path))
    : docRoute(doc.path)
}
