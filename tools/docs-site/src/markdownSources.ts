// The lazy loaders of the spec and doc bodies that the harvest writes (the `markdown` of the site data).
// Each body is its own hashed chunk; nothing here lands in the main bundle.

import { site } from './site.ts'

const bodies = new Map<string, Promise<string>>()

/**
 * Loads the body markdown of a harvested spec or doc; the path is its content-relative `path`.
 * The promise is stable per path, so a page can suspend on it with React `use()`.
 * A path with no harvested body is a harvest bug, because the index and the bodies come from one run.
 */
export function markdownBody(path: string): Promise<string> {
  let body = bodies.get(path)
  if (body === undefined) {
    const loader = site().index.data.markdown[path]
    if (loader === undefined) {
      throw new Error(`no harvested markdown for ${path}`)
    }
    body = loader()
    bodies.set(path, body)
  }
  return body
}
